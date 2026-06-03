import { useState, useEffect, useRef, useCallback } from 'react'
import { supabase } from '../lib/supabaseClient'
import { Search, MessageSquare, Plus, X, RefreshCw } from 'lucide-react'
import DrFredConversationView from './DrFredConversationView'

function DrFredPage() {
  const [conversations, setConversations] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const [activeSearchTerm, setActiveSearchTerm] = useState('')
  const [selectedConversation, setSelectedConversation] = useState(null)
  const [sortBy, setSortBy] = useState('unanswered')
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [totalCount, setTotalCount] = useState(0)
  const [showNewConversationModal, setShowNewConversationModal] = useState(false)
  const [newConversationUserId, setNewConversationUserId] = useState('')
  const [loadingUser, setLoadingUser] = useState(false)
  const [userError, setUserError] = useState('')
  const conversationsRef = useRef(null)
  const isLoadingRef = useRef(false)
  const PAGE_SIZE = 20

  // Load on mount and when sort/search changes
  useEffect(() => {
    loadConversations(0, true)
  }, [sortBy, activeSearchTerm])

  const loadConversations = async (pageNumber = 0, reset = false) => {
    if (isLoadingRef.current) return
    isLoadingRef.current = true

    if (pageNumber === 0) {
      setLoading(true)
    } else {
      setLoadingMore(true)
    }

    try {
      const from = pageNumber * PAGE_SIZE
      const to = from + PAGE_SIZE - 1

      let query = supabase
        .schema('comms')
        .from('dr_fred_conversations')
        .select('*', { count: 'exact' })

      if (activeSearchTerm) {
        query = query.or(`name.ilike.%${activeSearchTerm}%,user_id.eq.${activeSearchTerm}`)
      }

      if (sortBy === 'unanswered') {
        query = query
          .order('last_real_message_outbound', { ascending: true, nullsFirst: true })
          .order('last_message_timestamp', { ascending: false })
      } else {
        query = query.order('last_message_timestamp', { ascending: false })
      }

      query = query.range(from, to)

      const { data: convData, count, error } = await query

      if (error) throw error

      const processedConversations = convData?.map(conv => ({
        id: conv.user_id,
        user_id: conv.user_id,
        name: conv.name || 'Unknown User',
        lastMessage: {
          id: conv.last_message_id,
          text: conv.last_message_text,
          created_at: conv.last_message_timestamp,
          outbound: conv.last_message_outbound
        },
        hasUnread: conv.last_real_message_outbound === false
      })) || []

      if (reset || pageNumber === 0) {
        setConversations(processedConversations)
      } else {
        // Deduplicate by user_id when loading more
        setConversations(prev => {
          const existingIds = new Set(prev.map(c => c.user_id))
          const newConvs = processedConversations.filter(c => !existingIds.has(c.user_id))
          return [...prev, ...newConvs]
        })
      }

      setTotalCount(count || 0)
      setPage(pageNumber)
      setHasMore(convData?.length === PAGE_SIZE)
    } catch (error) {
      console.error('Error loading Dr Fred conversations:', error)
    } finally {
      setLoading(false)
      setLoadingMore(false)
      isLoadingRef.current = false
    }
  }

  // Scroll handler for infinite scroll
  const handleScroll = useCallback(() => {
    if (!conversationsRef.current || loadingMore || !hasMore || loading) return

    const { scrollTop, scrollHeight, clientHeight } = conversationsRef.current
    if (scrollTop + clientHeight >= scrollHeight - 100) {
      loadConversations(page + 1, false)
    }
  }, [loadingMore, hasMore, loading, page])

  useEffect(() => {
    const element = conversationsRef.current
    if (element) {
      element.addEventListener('scroll', handleScroll, { passive: true })
      return () => element.removeEventListener('scroll', handleScroll)
    }
  }, [handleScroll])

  const handleSearch = () => {
    setActiveSearchTerm(searchTerm.trim())
  }

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

  const handleStartNewConversation = async () => {
    if (!newConversationUserId.trim()) {
      setUserError('Please enter a user ID')
      return
    }

    setLoadingUser(true)
    setUserError('')

    try {
      const { data: userData, error } = await supabase
        .from('users')
        .select('id, name')
        .eq('id', newConversationUserId.trim())
        .single()

      if (error || !userData) {
        setUserError('User not found')
        return
      }

      setSelectedConversation({
        id: userData.id,
        user_id: userData.id,
        name: userData.name || 'Unknown User',
        messages: [],
        lastMessage: null,
        hasUnread: false
      })
      setShowNewConversationModal(false)
      setNewConversationUserId('')
    } catch (error) {
      console.error('Error starting new conversation:', error)
      setUserError('Failed to start conversation. Please try again.')
    } finally {
      setLoadingUser(false)
    }
  }

  if (selectedConversation) {
    return (
      <DrFredConversationView
        conversation={selectedConversation}
        onBack={() => setSelectedConversation(null)}
        onRefresh={() => loadConversations(0, true)}
      />
    )
  }

  return (
    <div className="h-full flex flex-col">
      <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-3 mb-4 sm:mb-6 flex-shrink-0">
        <div className="flex items-center gap-2 sm:gap-3">
          <h1 className="text-xl sm:text-2xl font-bold text-gray-900">Dr Fred</h1>
          {totalCount > 0 && <span className="text-xs sm:text-sm text-gray-500">({totalCount})</span>}
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => loadConversations(0, true)}
            disabled={loading}
            className="inline-flex items-center px-2 sm:px-3 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            <span className="hidden sm:inline ml-2">Refresh</span>
          </button>
          <button
            onClick={() => { setShowNewConversationModal(true); setNewConversationUserId(''); setUserError('') }}
            className="inline-flex items-center px-3 sm:px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-green-600 hover:bg-green-700"
          >
            <Plus className="h-4 w-4" />
            <span className="hidden sm:inline ml-2">Start New</span>
            <span className="sm:hidden ml-1">New</span>
          </button>
        </div>
      </div>

      {/* New Conversation Modal */}
      {showNewConversationModal && (
        <div className="fixed inset-0 bg-gray-100 bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4">
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <h3 className="text-lg font-semibold text-gray-900">Start New Conversation</h3>
              <button onClick={() => setShowNewConversationModal(false)} className="text-gray-400 hover:text-gray-600">
                <X className="h-6 w-6" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              {userError && <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">{userError}</div>}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">User ID</label>
                <input
                  type="text"
                  value={newConversationUserId}
                  onChange={(e) => { setNewConversationUserId(e.target.value); setUserError('') }}
                  onKeyDown={(e) => e.key === 'Enter' && !loadingUser && handleStartNewConversation()}
                  placeholder="Enter user ID"
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-green-500"
                  disabled={loadingUser}
                />
              </div>
            </div>
            <div className="flex justify-end space-x-3 p-6 border-t border-gray-200">
              <button onClick={() => setShowNewConversationModal(false)} className="px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50" disabled={loadingUser}>
                Cancel
              </button>
              <button
                onClick={handleStartNewConversation}
                disabled={loadingUser || !newConversationUserId.trim()}
                className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-green-600 hover:bg-green-700 disabled:opacity-50"
              >
                {loadingUser ? <><div className="animate-spin rounded-full h-4 w-4 border-t-2 border-b-2 border-white mr-2"></div>Loading...</> : <><Plus className="h-4 w-4 mr-2" />Start Conversation</>}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Search and Sort Controls */}
      <div className="bg-white rounded-lg shadow mb-4 sm:mb-6 p-3 sm:p-4 flex-shrink-0">
        <div className="flex flex-col gap-3">
          {/* Search row */}
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
              <input
                type="text"
                placeholder="Search name or user ID..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                className="w-full pl-10 pr-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <button onClick={handleSearch} className="px-3 sm:px-4 py-2 bg-blue-600 text-white text-sm rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-colors">
              Search
            </button>
          </div>
          {/* Filter buttons row */}
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => setSortBy('unanswered')}
              className={`px-2 sm:px-4 py-1.5 sm:py-2 text-xs sm:text-sm font-medium rounded-md ${sortBy === 'unanswered' ? 'bg-blue-100 text-blue-700 border border-blue-200' : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'}`}
            >
              Unanswered
            </button>
            <button
              onClick={() => setSortBy('recent')}
              className={`px-2 sm:px-4 py-1.5 sm:py-2 text-xs sm:text-sm font-medium rounded-md ${sortBy === 'recent' ? 'bg-blue-100 text-blue-700 border border-blue-200' : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'}`}
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
              <p className="text-gray-600">{activeSearchTerm ? 'No conversations found matching your search' : 'No Dr Fred conversations found'}</p>
            </div>
          </div>
        ) : (
          <div ref={conversationsRef} className="divide-y divide-gray-200 flex-1 overflow-y-auto">
            {conversations.map((conversation) => (
              <div
                key={conversation.id}
                onClick={() => setSelectedConversation(conversation)}
                className="p-4 hover:bg-gray-50 cursor-pointer transition-colors"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3 flex-1">
                    <div className={`w-10 h-10 rounded-full flex items-center justify-center ${conversation.hasUnread ? 'bg-green-100' : 'bg-gray-100'}`}>
                      <MessageSquare className={`h-5 w-5 ${conversation.hasUnread ? 'text-green-600' : 'text-gray-600'}`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between">
                        <p className={`text-sm font-medium truncate ${conversation.hasUnread ? 'text-gray-900' : 'text-gray-700'}`}>
                          {conversation.name || 'Unknown User'}
                        </p>
                        <div className="flex items-center space-x-2">
                          {conversation.hasUnread && (
                            <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">New</span>
                          )}
                          <span className="text-xs text-gray-500">{formatTimestamp(conversation.lastMessage?.created_at)}</span>
                        </div>
                      </div>
                      <p className="text-sm text-gray-600 truncate mt-1">
                        {conversation.lastMessage?.outbound ? 'Dr Fred: ' : 'User: '}
                        {conversation.lastMessage?.text?.length > 50 ? conversation.lastMessage.text.slice(0, 50) + '...' : conversation.lastMessage?.text}
                      </p>
                      <p className="text-xs text-gray-500 mt-1">User ID: {conversation.user_id}</p>
                    </div>
                  </div>
                </div>
              </div>
            ))}

            {loadingMore && (
              <div className="p-4 text-center">
                <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
                <p className="mt-2 text-sm text-gray-600">Loading more...</p>
              </div>
            )}

            {!hasMore && conversations.length > 0 && (
              <div className="p-4 text-center">
                <p className="text-sm text-gray-500">Showing {conversations.length} of {totalCount} conversations</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export default DrFredPage
