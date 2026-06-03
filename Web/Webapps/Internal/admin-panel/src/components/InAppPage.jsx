import { useState, useEffect, useRef, useCallback } from 'react'
import { supabase } from '../lib/supabaseClient'
import { Search, MessageSquare } from 'lucide-react'
import InAppConversationView from './InAppConversationView'

function InAppPage() {
  const [conversations, setConversations] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const [activeSearchTerm, setActiveSearchTerm] = useState('')
  const [selectedConversation, setSelectedConversation] = useState(null)
  const [sortBy, setSortBy] = useState('unanswered') // 'unanswered' or 'recent'
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [totalCount, setTotalCount] = useState(0)

  const conversationsRef = useRef(null)
  const abortControllerRef = useRef(null)
  const loadOperationIdRef = useRef(0)
  const PAGE_SIZE = 20

  useEffect(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
    }

    resetAndLoadConversations()

    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort()
      }
    }
  }, [sortBy, activeSearchTerm])

  // Subscribe to realtime updates for new messages
  useEffect(() => {
    const channel = supabase
      .channel('peer_conversations_updates')
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'comms',
          table: 'peer_conversations'
        },
        (payload) => {
          // Refresh conversations when there's an update
          resetAndLoadConversations()
        }
      )
      .subscribe()

    return () => {
      supabase.removeChannel(channel)
    }
  }, [])

  const resetAndLoadConversations = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
    }

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
    const abortController = new AbortController()
    abortControllerRef.current = abortController

    const currentOperationId = loadOperationIdRef.current

    if (pageNumber === 0) {
      setLoading(true)
    } else {
      setLoadingMore(true)
    }

    try {
      const from = pageNumber * PAGE_SIZE
      const to = from + PAGE_SIZE - 1

      // Query peer_conversations table
      let query = supabase
        .schema('comms')
        .from('peer_conversations')
        .select('*', { count: 'exact' })
        .abortSignal(abortController.signal)

      // Apply search filter
      if (activeSearchTerm) {
        query = query.or(
          `name.ilike.%${activeSearchTerm}%,user_id.eq.${activeSearchTerm}`
        )
      }

      // Apply sorting
      if (sortBy === 'unanswered') {
        // Sort by outbound (false first = inbound/unanswered), then by timestamp
        query = query
          .order('last_message_outbound', { ascending: true })
          .order('last_message_timestamp', { ascending: false, nullsFirst: false })
      } else {
        query = query.order('last_message_timestamp', { ascending: false, nullsFirst: false })
      }

      // Apply pagination
      query = query.range(from, to)

      const { data: conversationsData, count, error } = await query

      if (error) throw error

      if (currentOperationId !== loadOperationIdRef.current) {
        return
      }

      // Process conversations
      const processedConversations = (conversationsData || []).map(conv => ({
        id: conv.user_id,
        user_id: conv.user_id,
        name: conv.name || 'Unknown User',
        lastMessage: {
          text: conv.last_message_text,
          timestamp: conv.last_message_timestamp,
          outbound: conv.last_message_outbound
        },
        unreadCount: conv.unread_count || 0,
        hasUnread: conv.last_message_outbound === false
      }))

      if (currentOperationId !== loadOperationIdRef.current) {
        return
      }

      // Update state
      if (reset || pageNumber === 0) {
        setConversations(processedConversations)
      } else {
        setConversations(prev => [...prev, ...processedConversations])
      }

      setTotalCount(count || 0)
      setPage(pageNumber)
      setHasMore(conversationsData?.length === PAGE_SIZE)

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

  const loadMoreConversations = useCallback(() => {
    if (!loadingMore && hasMore && !loading) {
      const nextPage = page + 1
      loadConversations(nextPage, false)
    }
  }, [page, loadingMore, hasMore, loading])

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
    if (!timestamp) return ''
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

  if (selectedConversation) {
    return (
      <InAppConversationView
        conversation={selectedConversation}
        onBack={() => setSelectedConversation(null)}
        onRefresh={resetAndLoadConversations}
      />
    )
  }

  return (
    <div className="h-full flex flex-col">
      <div className="flex items-center gap-2 sm:gap-3 mb-4 sm:mb-6 flex-shrink-0">
        <h1 className="text-xl sm:text-2xl font-bold text-gray-900">In-App Messages</h1>
        {totalCount > 0 && (
          <span className="text-xs sm:text-sm text-gray-500">
            ({totalCount.toLocaleString()})
          </span>
        )}
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
                placeholder="Search name or ID..."
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
                {activeSearchTerm ? 'No conversations found matching your search' :
                 'No in-app conversations found'}
              </p>
              <p className="text-sm text-gray-500 mt-2">
                Users will appear here after they migrate to in-app messaging
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
                              {conversation.unreadCount} New
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
                          : conversation.lastMessage?.text || 'No messages yet'
                        }
                      </p>
                      <p className="text-xs text-gray-500 mt-1">
                        {conversation.user_id}
                      </p>
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
    </div>
  )
}

export default InAppPage
