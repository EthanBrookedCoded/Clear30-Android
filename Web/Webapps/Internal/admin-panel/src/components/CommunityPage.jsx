import { useState, useEffect, useRef, useCallback } from 'react'
import { supabase } from '../lib/supabaseClient'
import { Users, Trash2, EyeOff, Eye, AlertTriangle, RefreshCw } from 'lucide-react'

function CommunityPage() {
  const [reportedPosts, setReportedPosts] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [actionLoading, setActionLoading] = useState({})
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [totalCount, setTotalCount] = useState(0)
  const postsRef = useRef(null)
  const isLoadingRef = useRef(false)
  const PAGE_SIZE = 10

  // Load on mount
  useEffect(() => {
    loadReportedPosts(0, true)
  }, [])

  const loadReportedPosts = async (pageNumber = 0, reset = false) => {
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

      // Get all reported post IDs with counts
      const { data: reportCounts, error: countError } = await supabase
        .schema('community')
        .from('reported_posts')
        .select('post_id')

      if (countError) throw countError

      // Count reports per post
      const reportCountMap = {}
      reportCounts?.forEach(r => {
        reportCountMap[r.post_id] = (reportCountMap[r.post_id] || 0) + 1
      })

      const postIds = Object.keys(reportCountMap)

      if (postIds.length === 0) {
        setReportedPosts([])
        setTotalCount(0)
        setHasMore(false)
        return
      }

      // Sort by report count and paginate
      const sortedPostIds = postIds.sort((a, b) => reportCountMap[b] - reportCountMap[a])
      const paginatedPostIds = sortedPostIds.slice(from, to + 1)

      if (paginatedPostIds.length === 0) {
        setHasMore(false)
        return
      }

      // Get post details
      const { data: posts, error: postsError } = await supabase
        .schema('community')
        .from('posts')
        .select('*')
        .in('id', paginatedPostIds)

      if (postsError) throw postsError

      // Get user names
      const userIds = [...new Set(posts?.map(post => post.user_id).filter(Boolean) || [])]
      let userMap = {}

      if (userIds.length > 0) {
        const { data: userData } = await supabase
          .from('users')
          .select('id, name')
          .in('id', userIds)

        userData?.forEach(u => { userMap[u.id] = u.name || 'Unknown User' })
      }

      // Build result in sorted order
      const processedPosts = paginatedPostIds.map(postId => {
        const post = posts?.find(p => p.id === postId)
        if (!post) return null
        return {
          post: { ...post, user_name: userMap[post.user_id] || 'Unknown User' },
          reportCount: reportCountMap[postId] || 0
        }
      }).filter(Boolean)

      if (reset || pageNumber === 0) {
        setReportedPosts(processedPosts)
      } else {
        setReportedPosts(prev => [...prev, ...processedPosts])
      }

      setTotalCount(sortedPostIds.length)
      setPage(pageNumber)
      setHasMore(to + 1 < sortedPostIds.length)
    } catch (error) {
      console.error('Error loading reported posts:', error)
    } finally {
      setLoading(false)
      setLoadingMore(false)
      isLoadingRef.current = false
    }
  }

  // Scroll handler for infinite scroll
  const handleScroll = useCallback(() => {
    if (!postsRef.current || loadingMore || !hasMore || loading) return

    const { scrollTop, scrollHeight, clientHeight } = postsRef.current
    if (scrollTop + clientHeight >= scrollHeight - 100) {
      loadReportedPosts(page + 1, false)
    }
  }, [loadingMore, hasMore, loading, page])

  useEffect(() => {
    const element = postsRef.current
    if (element) {
      element.addEventListener('scroll', handleScroll, { passive: true })
      return () => element.removeEventListener('scroll', handleScroll)
    }
  }, [handleScroll])

  const deletePost = async (postId) => {
    setActionLoading(prev => ({ ...prev, [postId]: 'deleting' }))
    try {
      const { error } = await supabase.rpc('delete_post', { post_id: postId })
      if (error) throw error
      setReportedPosts(prev => prev.filter(item => item.post.id !== postId))
      setTotalCount(prev => prev - 1)
    } catch (error) {
      console.error('Error deleting post:', error)
      alert('Failed to delete post. Please try again.')
    } finally {
      setActionLoading(prev => ({ ...prev, [postId]: null }))
    }
  }

  const togglePostVisibility = async (postId, currentlyHidden) => {
    const action = currentlyHidden ? 'unhiding' : 'hiding'
    setActionLoading(prev => ({ ...prev, [postId]: action }))

    try {
      const { error } = await supabase
        .schema('community')
        .from('posts')
        .update({ is_hidden: !currentlyHidden })
        .eq('id', postId)

      if (error) throw error

      setReportedPosts(prev => prev.map(item => {
        if (item.post.id === postId) {
          return { ...item, post: { ...item.post, is_hidden: !currentlyHidden } }
        }
        return item
      }))
    } catch (error) {
      console.error(`Error ${action} post:`, error)
      alert(`Failed to ${action.slice(0, -3)} post. Please try again.`)
    } finally {
      setActionLoading(prev => ({ ...prev, [postId]: null }))
    }
  }

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
    })
  }

  const truncateText = (text, maxLength = 200) => {
    if (!text || text.length <= maxLength) return text || ''
    return text.substring(0, maxLength) + '...'
  }

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-between items-center mb-6 flex-shrink-0">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-gray-900">Community Moderation</h1>
          {totalCount > 0 && (
            <span className="text-sm text-gray-500">({totalCount} reported posts)</span>
          )}
        </div>
        <button
          onClick={() => loadReportedPosts(0, true)}
          disabled={loading}
          className="inline-flex items-center px-3 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      <div className="bg-white rounded-lg shadow flex-1 min-h-0 flex flex-col">
        {loading ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
              <p className="mt-2 text-gray-600">Loading reported posts...</p>
            </div>
          </div>
        ) : reportedPosts.length === 0 ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <Users className="h-12 w-12 text-gray-400 mx-auto mb-4" />
              <p className="text-gray-600">No reported posts found</p>
            </div>
          </div>
        ) : (
          <div ref={postsRef} className="flex-1 overflow-y-auto">
            <div className="space-y-6 p-6">
              {reportedPosts.map(({ post, reportCount }) => (
                <div key={post.id} className="bg-white rounded-lg shadow overflow-hidden border">
                  <div className="px-6 py-4 border-b border-gray-200">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-3">
                        <div className="flex items-center space-x-2">
                          <AlertTriangle className="h-5 w-5 text-red-500" />
                          <span className="text-sm font-medium text-red-600">
                            {reportCount} report{reportCount !== 1 ? 's' : ''}
                          </span>
                        </div>
                        {post.is_hidden && (
                          <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-800">Hidden</span>
                        )}
                      </div>
                      <div className="flex items-center space-x-2">
                        <button
                          onClick={() => togglePostVisibility(post.id, post.is_hidden)}
                          disabled={actionLoading[post.id]}
                          className={`inline-flex items-center px-3 py-2 border text-sm font-medium rounded-md ${
                            post.is_hidden
                              ? 'border-green-300 text-green-700 bg-green-50 hover:bg-green-100'
                              : 'border-yellow-300 text-yellow-700 bg-yellow-50 hover:bg-yellow-100'
                          } disabled:opacity-50`}
                        >
                          {actionLoading[post.id] === 'hiding' || actionLoading[post.id] === 'unhiding' ? (
                            <div className="animate-spin rounded-full h-4 w-4 border-t-2 border-b-2 border-current mr-2"></div>
                          ) : post.is_hidden ? (
                            <Eye className="h-4 w-4 mr-2" />
                          ) : (
                            <EyeOff className="h-4 w-4 mr-2" />
                          )}
                          {post.is_hidden ? 'Unhide' : 'Hide'}
                        </button>
                        <button
                          onClick={() => window.confirm('Delete this post?') && deletePost(post.id)}
                          disabled={actionLoading[post.id]}
                          className="inline-flex items-center px-3 py-2 border border-red-300 text-sm font-medium rounded-md text-red-700 bg-red-50 hover:bg-red-100 disabled:opacity-50"
                        >
                          {actionLoading[post.id] === 'deleting' ? (
                            <div className="animate-spin rounded-full h-4 w-4 border-t-2 border-b-2 border-red-500 mr-2"></div>
                          ) : (
                            <Trash2 className="h-4 w-4 mr-2" />
                          )}
                          Delete
                        </button>
                      </div>
                    </div>
                  </div>

                  <div className="px-6 py-4">
                    <div className="flex items-center justify-between mb-2">
                      <h3 className="text-lg font-medium text-gray-900">{post.title || 'Untitled Post'}</h3>
                      <span className="text-sm text-gray-500">{formatDate(post.created_at)}</span>
                    </div>
                    <p className="text-sm text-gray-600 mb-2">
                      By: {post.user_name} {post.user_id && <span className="text-gray-500">(ID: {post.user_id})</span>}
                    </p>
                    {post.body && (
                      <div className="bg-gray-50 rounded-md p-3 mt-3">
                        <p className="text-sm text-gray-700 whitespace-pre-wrap">{truncateText(post.body)}</p>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {loadingMore && (
              <div className="p-4 text-center">
                <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
                <p className="mt-2 text-sm text-gray-600">Loading more...</p>
              </div>
            )}

            {!hasMore && reportedPosts.length > 0 && (
              <div className="p-4 text-center">
                <p className="text-sm text-gray-500">Showing {reportedPosts.length} of {totalCount} reported posts</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export default CommunityPage
