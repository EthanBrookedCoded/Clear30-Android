import { useState, useEffect, useRef, useCallback } from 'react'
import { supabase } from '../lib/supabaseClient'
import { Search, Plus, MessageSquare, Calendar, FileText, Ban, CheckCircle, Tag, Filter } from 'lucide-react'
import ConversationView from './ConversationView'
import NewMessageModal from './NewMessageModal'
import { useAuth } from '../hooks/useAuth'

function SMSPage() {
  const [conversations, setConversations] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const [activeSearchTerm, setActiveSearchTerm] = useState('') // The search term currently applied
  const [selectedConversation, setSelectedConversation] = useState(null)
  const [showNewMessage, setShowNewMessage] = useState(false)
  const [sortBy, setSortBy] = useState('unanswered') // 'unanswered' or 'recent'
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [totalCount, setTotalCount] = useState(0)
  
  // Tag-related state
  const [availableTags, setAvailableTags] = useState([])
  const [selectedTagId, setSelectedTagId] = useState(null)
  const [showTagDropdown, setShowTagDropdown] = useState(false)
  const [adminId, setAdminId] = useState(null)
  
  const conversationsRef = useRef(null)
  const tagDropdownRef = useRef(null)
  const abortControllerRef = useRef(null)
  const loadOperationIdRef = useRef(0)
  const PAGE_SIZE = 20

  const { user } = useAuth()

  // Get admin ID from user
  useEffect(() => {
    const getAdminId = async () => {
      if (user) {
        const { data, error } = await supabase
        .schema('platform')
        .rpc('get_admin_id')
        .single()

        if (data) {
          setAdminId(data)
        }
      }
    }
    getAdminId()
  }, [user])

  // Load available tags when admin ID is available
  useEffect(() => {
    if (adminId) {
      loadAvailableTags()
    }
  }, [adminId])

  useEffect(() => {
    // Abort any in-flight requests when dependencies change
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
    }
    
    resetAndLoadConversations()
    
    // Cleanup function to abort on unmount or when dependencies change
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
      }
    }
  }, [sortBy, selectedTagId, activeSearchTerm])

  // Close tag filter when clicking outside
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (tagDropdownRef.current && !tagDropdownRef.current.contains(event.target)) {
        setShowTagDropdown(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [])

  const loadAvailableTags = async () => {
    if (!adminId) return
    
    try {
      const { data, error } = await supabase
        .schema('platform')
        .from('tags')
        .select('*')
        .eq('admin_id', adminId)
        .order('name')

      if (error) throw error
      setAvailableTags(data || [])
    } catch (error) {
      console.error('Error loading available tags:', error)
    }
  }

  // Reset pagination and load from beginning
  const resetAndLoadConversations = () => {
    // Abort any in-flight requests
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
    }
    
    // Increment operation ID to invalidate any pending operations
    loadOperationIdRef.current += 1
    
    setLoading(true)
    setPage(0)
    setConversations([])
    setHasMore(true)
    loadConversations(0, true)
  }

  const handleSearch = () => {
    setActiveSearchTerm(searchTerm.trim())
  }

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch()
    }
  }

  const loadConversations = async (pageNumber = 0, reset = false) => {
    // Create new AbortController for this request
    const abortController = new AbortController()
    abortControllerRef.current = abortController
    
    // Capture current operation ID
    const currentOperationId = loadOperationIdRef.current
    
    if (pageNumber === 0) {
      setLoading(true)
    } else {
      setLoadingMore(true)
    }
    
    try {
      // Calculate pagination range
      const from = pageNumber * PAGE_SIZE
      const to = from + PAGE_SIZE - 1

      // If tag filtering is active, get phone numbers with the selected tag first
      let taggedPhoneNumbers = null
      if (adminId && selectedTagId) {
        const { data: taggedNumbers, error: tagError } = await supabase
          .schema('platform')
          .from('conversation_tags')
          .select('phone_number')
          .eq('admin_id', adminId)
          .eq('tag_id', selectedTagId)
          .abortSignal(abortController.signal)

        if (tagError) throw tagError
        taggedPhoneNumbers = taggedNumbers?.map(ct => ct.phone_number) || []
        
        // Check if this operation is still valid
        if (currentOperationId !== loadOperationIdRef.current) {
          return
        }
        
        // If no conversations have this tag, return empty results
        if (taggedPhoneNumbers.length === 0) {
          setConversations([])
          setTotalCount(0)
          setHasMore(false)
          setLoading(false)
          setLoadingMore(false)
          return
        }
      }

      // Build query for user conversations with server-side pagination
      // Query the denormalized sms_conversations table directly (much faster than views!)
      let userQuery = supabase
        .schema('platform')
        .from('sms_conversations')
        .select('*', { count: 'exact' })
        .eq('is_user', true) // Filter for user conversations
        .abortSignal(abortController.signal)

      // Apply search filter at database level
      if (activeSearchTerm) {
        userQuery = userQuery.or(
          `name.ilike.%${activeSearchTerm}%,phone_number.ilike.%${activeSearchTerm}%,user_id.eq.${activeSearchTerm}`
        )
      }

      // Apply tag filtering at database level
      if (taggedPhoneNumbers) {
        userQuery = userQuery.in('phone_number', taggedPhoneNumbers)
      }

      // Apply sorting at database level (using new table column names)
      if (sortBy === 'unanswered') {
        // Sort by outbound (false first = inbound/unanswered), then by timestamp
        userQuery = userQuery
          .order('last_message_outbound', { ascending: true })
          .order('last_message_timestamp', { ascending: false })
      } else {
        userQuery = userQuery.order('last_message_timestamp', { ascending: false })
      }

      // Apply pagination
      userQuery = userQuery.range(from, to)

      const { data: userConversations, count: userCount, error: userError } = await userQuery

      if (userError) throw userError
      
      // Check if this operation is still valid
      if (currentOperationId !== loadOperationIdRef.current) {
        return
      }

      // Build query for non-user conversations (only on first page and if no search targeting users)
      let nonUserConversations = []
      let nonUserCount = 0
      
      // Only load non-user conversations if we're not specifically searching for user fields
      if (pageNumber === 0 && !activeSearchTerm) {
        let nonUserQuery = supabase
          .schema('platform')
          .from('sms_conversations')
          .select('*', { count: 'exact' })
          .eq('is_user', false) // Filter for non-user conversations
          .abortSignal(abortController.signal)

        // Apply tag filtering at database level
        if (taggedPhoneNumbers) {
          nonUserQuery = nonUserQuery.in('phone_number', taggedPhoneNumbers)
        }

        // Apply sorting (using new table column names)
        if (sortBy === 'unanswered') {
          nonUserQuery = nonUserQuery
            .order('last_message_outbound', { ascending: true })
            .order('last_message_timestamp', { ascending: false })
        } else {
          nonUserQuery = nonUserQuery.order('last_message_timestamp', { ascending: false })
        }

        // Limit non-user conversations (they're usually fewer)
        nonUserQuery = nonUserQuery.limit(50)

        const { data: nonUserData, count: nuCount, error: nonUserError } = await nonUserQuery

        if (nonUserError) throw nonUserError
        
        nonUserConversations = nonUserData || []
        nonUserCount = nuCount || 0
      }

      // Check if this operation is still valid
      if (currentOperationId !== loadOperationIdRef.current) {
        return
      }

      // Process conversations (using new table column names)
      let processedConversations = []

      // Process user conversations
      userConversations?.forEach(conv => {
        processedConversations.push({
          id: conv.user_id || conv.phone_number,
          user_id: conv.user_id,
          phone_number: conv.phone_number,
          name: conv.name || 'Unknown User',
          type: 'user',
          lastMessage: {
            text: conv.last_message_text,
            timestamp: conv.last_message_timestamp,
            outbound: conv.last_message_outbound
          },
          hasUnread: !conv.last_message_outbound
        })
      })

      // Add non-user conversations (only on first page)
      if (pageNumber === 0) {
        nonUserConversations?.forEach(conv => {
          processedConversations.push({
            id: conv.phone_number,
            phone_number: conv.phone_number,
            name: conv.name || `Unknown (${conv.phone_number})`,
            type: 'nonuser',
            lastMessage: {
              text: conv.last_message_text,
              timestamp: conv.last_message_timestamp,
              outbound: conv.last_message_outbound
            },
            hasUnread: !conv.last_message_outbound
          })
        })

        // Re-sort combined results if needed
        if (sortBy === 'unanswered') {
          processedConversations.sort((a, b) => {
            if (a.hasUnread && !b.hasUnread) return -1
            if (!a.hasUnread && b.hasUnread) return 1
            return new Date(b.lastMessage?.timestamp) - new Date(a.lastMessage?.timestamp)
          })
        } else {
          processedConversations.sort((a, b) => 
            new Date(b.lastMessage?.timestamp) - new Date(a.lastMessage?.timestamp)
          )
        }
      }

      // Load tags for this page's conversations
      if (adminId && processedConversations.length > 0) {
        const phoneNumbers = processedConversations.map(conv => conv.phone_number)
        
        const { data: conversationTags, error: tagsError } = await supabase
          .schema('platform')
          .from('conversation_tags')
          .select(`
            phone_number,
            tags (id, name, color)
          `)
          .eq('admin_id', adminId)
          .in('phone_number', phoneNumbers)
          .abortSignal(abortController.signal)

        // Check if this operation is still valid
        if (currentOperationId !== loadOperationIdRef.current) {
          return
        }

        if (!tagsError && conversationTags) {
          const tagsByPhone = {}
          conversationTags.forEach(ct => {
            if (!tagsByPhone[ct.phone_number]) {
              tagsByPhone[ct.phone_number] = []
            }
            tagsByPhone[ct.phone_number].push(ct.tags)
          })

          processedConversations = processedConversations.map(conv => ({
            ...conv,
            tags: tagsByPhone[conv.phone_number] || []
          }))
        }
      }

      // Final check before setting state
      if (currentOperationId !== loadOperationIdRef.current) {
        return
      }

      // Update state
      if (reset || pageNumber === 0) {
        setConversations(processedConversations)
      } else {
        setConversations(prev => [...prev, ...processedConversations])
      }

      // Update pagination state
      const total = (userCount || 0) + (pageNumber === 0 ? nonUserCount : 0)
      setTotalCount(total)
      setPage(pageNumber)
      setHasMore(userConversations?.length === PAGE_SIZE)

    } catch (error) {
      if (error.name === 'AbortError' || error.message?.includes('aborted')) {
        console.log('Request was aborted')
        return
      }
      
      console.error('Error loading conversations:', error)
      
      if (currentOperationId === loadOperationIdRef.current) {
        setHasMore(false)
      }
    } finally {
      if (currentOperationId === loadOperationIdRef.current) {
        setLoading(false)
        setLoadingMore(false)
      }
    }
  }

  // Load more conversations for infinite scroll
  const loadMoreConversations = useCallback(() => {
    if (!loadingMore && hasMore && !loading) {
      const nextPage = page + 1
      loadConversations(nextPage, false)
    }
  }, [page, loadingMore, hasMore, loading])

  // Scroll detection for infinite scroll with throttling
  const handleScroll = useCallback(() => {
    if (!conversationsRef.current || loadingMore || !hasMore) return

    const element = conversationsRef.current
    const { scrollTop, scrollHeight, clientHeight } = element
    
    const scrollPosition = scrollTop + clientHeight
    const isNearBottom = scrollPosition >= scrollHeight - 100
    
    if (isNearBottom) {
      loadMoreConversations()
    }
  }, [loadMoreConversations, loadingMore, hasMore])

  // Throttled scroll handler
  const throttledHandleScroll = useCallback(() => {
    clearTimeout(window.scrollTimeout)
    window.scrollTimeout = setTimeout(handleScroll, 50)
  }, [handleScroll])

  useEffect(() => {
    const conversationsElement = conversationsRef.current
    if (conversationsElement) {
      conversationsElement.addEventListener('scroll', throttledHandleScroll, { passive: true })
      return () => {
        conversationsElement.removeEventListener('scroll', throttledHandleScroll)
        clearTimeout(window.scrollTimeout)
      }
    }
  }, [throttledHandleScroll])

  const formatTimestamp = (timestamp) => {
    const date = new Date(timestamp)
    const now = new Date()
    const diffInHours = (now - date) / (1000 * 60 * 60)

    if (diffInHours < 24) {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    } else if (diffInHours < 168) {
      return date.toLocaleDateString([], { weekday: 'short' })
    } else {
      return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
    }
  }

  const toggleTagDropdown = (tagId) => {
    setSelectedTagId(tagId)
  }

  const clearTagDropdown = () => {
    setSelectedTagId(null)
  }

  if (selectedConversation) {
    return (
      <ConversationView
        conversation={selectedConversation}
        onBack={() => setSelectedConversation(null)}
        onRefresh={resetAndLoadConversations}
      />
    )
  }

  return (
    <div className="h-full flex flex-col">
      <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-3 mb-4 sm:mb-6 flex-shrink-0">
        <div className="flex items-center gap-2 sm:gap-3">
          <h1 className="text-xl sm:text-2xl font-bold text-gray-900">SMS Conversations</h1>
          {totalCount > 0 && (
            <span className="text-xs sm:text-sm text-gray-500">
              ({totalCount.toLocaleString()})
            </span>
          )}
        </div>
        <button
          onClick={() => setShowNewMessage(true)}
          className="inline-flex items-center justify-center px-3 sm:px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
        >
          <Plus className="h-4 w-4 sm:mr-2" />
          <span className="hidden sm:inline">New Message</span>
          <span className="sm:hidden ml-1">New</span>
        </button>
      </div>

      {/* Search and Sort Controls */}
      <div className="bg-white rounded-lg shadow mb-4 sm:mb-6 p-3 sm:p-4 flex-shrink-0">
        <div className="flex flex-col gap-3">
          {/* Search row */}
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
              <input
                type="text"
                placeholder="Search name, phone, or ID..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onKeyPress={handleKeyPress}
                className="w-full pl-10 pr-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <button
              onClick={handleSearch}
              className="px-3 sm:px-4 py-2 bg-blue-600 text-white text-sm rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-colors"
            >
              Search
            </button>
          </div>
          {/* Filter buttons row */}
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => setSortBy('unanswered')}
              className={`px-2 sm:px-4 py-1.5 sm:py-2 text-xs sm:text-sm font-medium rounded-md ${
                sortBy === 'unanswered'
                  ? 'bg-blue-100 text-blue-700 border border-blue-200'
                  : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'
              }`}
            >
              Unanswered
            </button>
            <button
              onClick={() => setSortBy('recent')}
              className={`px-2 sm:px-4 py-1.5 sm:py-2 text-xs sm:text-sm font-medium rounded-md ${
                sortBy === 'recent'
                  ? 'bg-blue-100 text-blue-700 border border-blue-200'
                  : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'
              }`}
            >
              Recent
            </button>
            
            {/* Tag Filter Dropdown */}
            {availableTags.length > 0 && (
              <div className="relative" ref={tagDropdownRef}>
                <button
                  onClick={() => setShowTagDropdown(!showTagDropdown)}
                  className="inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50"
                >
                  <Tag className="h-4 w-4 mr-2" />
                  Tags
                  {selectedTagId && (
                    <span className="ml-2 inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                      1
                    </span>
                  )}
                </button>

                {showTagDropdown && (
                  <div className="absolute right-0 mt-1 w-64 bg-white border border-gray-200 rounded-md shadow-lg z-10">
                    <div className="p-2">
                      {selectedTagId && (
                        <>
                          <button
                            onClick={() => {
                              clearTagDropdown()
                              setShowTagDropdown(false)
                            }}
                            className="w-full flex items-center px-2 py-2 text-sm text-left hover:bg-gray-100 rounded text-gray-600"
                          >
                            Clear Filter
                          </button>
                          <hr className="my-2" />
                        </>
                      )}
                      <div className="max-h-48 overflow-y-auto">
                        {availableTags.map(tag => (
                          <button
                            key={tag.id}
                            onClick={() => {
                              toggleTagDropdown(tag.id)
                              setShowTagDropdown(false)
                            }}
                            className={`w-full flex items-center px-2 py-2 text-sm text-left hover:bg-gray-100 rounded ${
                              selectedTagId === tag.id ? 'bg-blue-50' : ''
                            }`}
                          >
                            <span 
                              className="w-3 h-3 rounded-full mr-3"
                              style={{ backgroundColor: tag.color }}
                            />
                            <span className="flex-1">{tag.name}</span>
                            {selectedTagId === tag.id && (
                              <CheckCircle className="h-4 w-4 text-blue-600 ml-2" />
                            )}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Conversations List */}
      <div className="bg-white rounded-lg shadow flex-1 min-h-0 flex flex-col">
        {loading ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
              <p className="mt-2 text-gray-600">Loading conversations...</p>
            </div>
          </div>
        ) : conversations.length === 0 ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <MessageSquare className="h-12 w-12 text-gray-400 mx-auto mb-4" />
              <p className="text-gray-600">
                {selectedTagId ? 'No conversations found with the selected tag' : 
                 activeSearchTerm ? 'No conversations found matching your search' :
                 'No conversations found'}
              </p>
            </div>
          </div>
        ) : (
          <div 
            ref={conversationsRef}
            className="divide-y divide-gray-200 flex-1 overflow-y-auto"
          >
            {conversations.map((conversation) => (
              <div
                key={conversation.id}
                onClick={() => setSelectedConversation(conversation)}
                className="p-4 hover:bg-gray-50 cursor-pointer transition-colors"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3 flex-1">
                    <div className={`w-10 h-10 rounded-full flex items-center justify-center ${
                      conversation.hasUnread ? 'bg-blue-100' : 'bg-gray-100'
                    }`}>
                      <MessageSquare className={`h-5 w-5 ${
                        conversation.hasUnread ? 'text-blue-600' : 'text-gray-600'
                      }`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between">
                        <p className={`text-sm font-medium truncate ${
                          conversation.hasUnread ? 'text-gray-900' : 'text-gray-700'
                        }`}>
                          {conversation.name}
                        </p>
                        <div className="flex items-center space-x-2">
                          {conversation.hasUnread && (
                            <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                              New
                            </span>
                          )}
                          <span className="text-xs text-gray-500">
                            {formatTimestamp(conversation.lastMessage?.timestamp)}
                          </span>
                        </div>
                      </div>
                      <p className="text-sm text-gray-600 truncate mt-1">
                        {conversation.lastMessage?.outbound ? 'You: ' : ''}
                        {conversation.lastMessage?.text?.length > 50 
                          ? conversation.lastMessage.text.slice(0, 50) + '...'
                          : conversation.lastMessage?.text
                        }
                      </p>
                      <div className="flex items-center justify-between mt-1">
                        <p className="text-xs text-gray-500">
                          {conversation.user_id || conversation.phone_number}
                        </p>
                        {conversation.tags?.length > 0 && (
                          <div className="flex gap-1">
                            {conversation.tags.slice(0, 3).map(tag => (
                              <span
                                key={tag.id}
                                className="inline-flex items-center px-1.5 py-0.5 rounded text-xs"
                                style={{ 
                                  backgroundColor: `${tag.color}20`,
                                  color: tag.color 
                                }}
                              >
                                {tag.name}
                              </span>
                            ))}
                            {conversation.tags.length > 3 && (
                              <span className="text-xs text-gray-400">
                                +{conversation.tags.length - 3}
                              </span>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ))}
            
            {/* Loading more indicator */}
            {loadingMore && (
              <div className="p-4 text-center">
                <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
                <p className="mt-2 text-sm text-gray-600">Loading more conversations...</p>
              </div>
            )}
            
            {/* End of list indicator */}
            {!hasMore && conversations.length > 0 && (
              <div className="p-4 text-center">
                <p className="text-sm text-gray-500">
                  Showing {conversations.length.toLocaleString()} of {totalCount.toLocaleString()} conversations
                </p>
              </div>
            )}
          </div>
        )}
      </div>

      {/* New Message Modal */}
      {showNewMessage && (
        <NewMessageModal
          onClose={() => setShowNewMessage(false)}
          onSent={() => {
            setShowNewMessage(false)
            resetAndLoadConversations()
          }}
        />
      )}
    </div>
  )
}

export default SMSPage
