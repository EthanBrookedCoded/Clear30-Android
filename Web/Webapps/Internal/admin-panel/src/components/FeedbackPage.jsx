import { useState, useEffect, useRef, useCallback } from 'react'
import { supabase } from '../lib/supabaseClient'
import { MessageSquare, Search } from 'lucide-react'

function FeedbackPage() {
  const [feedback, setFeedback] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const [page, setPage] = useState(0)
  const [totalCount, setTotalCount] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const feedbackRef = useRef(null)
  const isLoadingRef = useRef(false)
  const PAGE_SIZE = 20

  // Load on mount
  useEffect(() => {
    loadFeedback(0, true)
  }, [])

  const loadFeedback = async (pageNumber = 0, reset = false) => {
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
        .from('feedback')
        .select('*', { count: 'exact' })
        .order('timestamp', { ascending: false })
        .range(from, to)

      if (searchTerm) {
        query = query.or(`feedback.ilike.%${searchTerm}%,user_id.ilike.%${searchTerm}%`)
      }

      const { data, count, error } = await query

      if (error) throw error

      if (reset || pageNumber === 0) {
        setFeedback(data || [])
        setTotalCount(count || 0)
      } else {
        setFeedback(prev => [...prev, ...(data || [])])
      }

      setHasMore((data?.length || 0) === PAGE_SIZE)
      setPage(pageNumber)
    } catch (error) {
      console.error('Error loading feedback:', error)
    } finally {
      setLoading(false)
      setLoadingMore(false)
      isLoadingRef.current = false
    }
  }

  const handleSearch = () => {
    loadFeedback(0, true)
  }

  // Scroll handler for infinite scroll
  const handleScroll = useCallback(() => {
    if (!feedbackRef.current || loadingMore || !hasMore || loading) return

    const { scrollTop, scrollHeight, clientHeight } = feedbackRef.current
    if (scrollTop + clientHeight >= scrollHeight - 100) {
      setLoadingMore(true)
      loadFeedback(page + 1, false)
    }
  }, [loadingMore, hasMore, loading, page])

  useEffect(() => {
    const element = feedbackRef.current
    if (element) {
      element.addEventListener('scroll', handleScroll, { passive: true })
      return () => element.removeEventListener('scroll', handleScroll)
    }
  }, [handleScroll])

  const formatTimestamp = (timestamp) => {
    return new Date(timestamp).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex justify-between items-center mb-6 flex-shrink-0">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-gray-900">User Feedback</h1>
          {totalCount > 0 && (
            <span className="text-sm text-gray-500">({totalCount} total)</span>
          )}
        </div>
      </div>

      {/* Search */}
      <div className="bg-white rounded-lg shadow mb-6 p-4 flex-shrink-0">
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search feedback or user ID..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>
          <button
            onClick={handleSearch}
            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-colors"
          >
            Search
          </button>
        </div>
      </div>

      {/* Feedback List */}
      <div className="bg-white rounded-lg shadow flex-1 min-h-0 flex flex-col">
        {loading ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
              <p className="mt-2 text-gray-600">Loading feedback...</p>
            </div>
          </div>
        ) : feedback.length === 0 ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <MessageSquare className="h-12 w-12 text-gray-400 mx-auto mb-4" />
              <p className="text-gray-600">No feedback found</p>
            </div>
          </div>
        ) : (
          <div ref={feedbackRef} className="divide-y divide-gray-200 flex-1 overflow-y-auto">
            {feedback.map((item) => (
              <div key={item.id} className="p-4 hover:bg-gray-50 transition-colors">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-gray-500">User: {item.user_id || 'Unknown'}</span>
                  <span className="text-xs text-gray-500">{formatTimestamp(item.timestamp)}</span>
                </div>
                <div className="bg-gray-50 rounded-md p-3">
                  <p className="text-sm text-gray-900 whitespace-pre-wrap">{item.feedback}</p>
                </div>
              </div>
            ))}

            {loadingMore && (
              <div className="p-4 text-center">
                <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
                <p className="mt-2 text-sm text-gray-600">Loading more...</p>
              </div>
            )}

            {!hasMore && feedback.length > 0 && (
              <div className="p-4 text-center">
                <p className="text-sm text-gray-500">
                  Showing {feedback.length} of {totalCount} feedback entries
                </p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export default FeedbackPage
