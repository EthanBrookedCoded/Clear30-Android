import React, { useState, useEffect, useRef, useCallback } from 'react'
import { supabase } from '../lib/supabaseClient'
import { Video, Search, ChevronDown, ChevronRight, ExternalLink, Copy, Check, Download, ArrowUp } from 'lucide-react'

const STATUS_OPTIONS = [
  { value: 'pending', label: 'Pending', color: 'bg-gray-100 text-gray-700' },
  { value: 'contacted', label: 'Contacted', color: 'bg-blue-100 text-blue-700' },
  { value: 'in_progress', label: 'In Progress', color: 'bg-yellow-100 text-yellow-700' },
  { value: 'delayed', label: 'Delayed', color: 'bg-orange-100 text-orange-700' },
  { value: 'declined', label: 'Declined', color: 'bg-red-100 text-red-700' },
  { value: 'converted', label: 'Converted', color: 'bg-green-100 text-green-700' },
  { value: 'backfilled', label: 'Backfilled', color: 'bg-purple-100 text-purple-700' },
]

function TestimonialsPage() {
  const [activeTab, setActiveTab] = useState('candidates')

  // Candidates state
  const [candidates, setCandidates] = useState([])
  const [candidatesLoading, setCandidatesLoading] = useState(true)
  const [candidatesLoadingMore, setCandidatesLoadingMore] = useState(false)
  const [candidatesPage, setCandidatesPage] = useState(0)
  const [candidatesTotalCount, setCandidatesTotalCount] = useState(0)
  const [candidatesHasMore, setCandidatesHasMore] = useState(true)
  const [statusFilter, setStatusFilter] = useState('all')
  const [searchTerm, setSearchTerm] = useState('')
  const candidatesRef = useRef(null)
  const candidatesLoadingRef = useRef(false)

  // Submitted state
  const [submitted, setSubmitted] = useState([])
  const [submittedLoading, setSubmittedLoading] = useState(true)
  const [submittedLoadingMore, setSubmittedLoadingMore] = useState(false)
  const [submittedPage, setSubmittedPage] = useState(0)
  const [submittedTotalCount, setSubmittedTotalCount] = useState(0)
  const [submittedHasMore, setSubmittedHasMore] = useState(true)
  const [expandedSubmitted, setExpandedSubmitted] = useState({})
  const [expandedCandidates, setExpandedCandidates] = useState({})
  const submittedRef = useRef(null)
  const submittedLoadingRef = useRef(false)

  // Editing state
  const [editingNotes, setEditingNotes] = useState(null)
  const [editingNotesValue, setEditingNotesValue] = useState('')
  const [copiedMessageId, setCopiedMessageId] = useState(null)
  const [downloadingId, setDownloadingId] = useState(null)
  const [downloadProgress, setDownloadProgress] = useState({ current: 0, total: 0 })

  const PAGE_SIZE = 20

  // Load candidates on mount and when filters change
  useEffect(() => {
    loadCandidates(0, true)
  }, [statusFilter])

  // Load submitted on tab switch
  useEffect(() => {
    if (activeTab === 'submitted' && submitted.length === 0) {
      loadSubmitted(0, true)
    }
  }, [activeTab])

  const loadCandidates = async (pageNumber = 0, reset = false) => {
    if (candidatesLoadingRef.current) return
    candidatesLoadingRef.current = true

    if (pageNumber === 0) {
      setCandidatesLoading(true)
    } else {
      setCandidatesLoadingMore(true)
    }

    try {
      const from = pageNumber * PAGE_SIZE
      const to = from + PAGE_SIZE - 1
      const needsCount = pageNumber === 0

      let query = supabase
        .schema('comms')
        .from('video_testimonial_candidates')
        .select('*', needsCount ? { count: 'exact' } : undefined)
        .order('created_at', { ascending: false })
        .range(from, to)

      if (statusFilter !== 'all') {
        query = query.eq('status', statusFilter)
      }

      const { data, count, error } = await query

      if (error) throw error

      // Fetch user details for these candidates
      const userIds = data.map(c => c.user_id).filter(Boolean)
      let usersMap = {}

      if (userIds.length > 0) {
        const { data: users, error: usersError } = await supabase
          .from('users')
          .select('id, name, phone_number, email')
          .in('id', userIds)

        if (!usersError && users) {
          usersMap = users.reduce((acc, user) => {
            acc[user.id] = user
            return acc
          }, {})
        }
      }

      // Merge user data with candidates
      const candidatesWithUsers = data.map(candidate => ({
        ...candidate,
        user: usersMap[candidate.user_id] || null
      }))

      if (reset || pageNumber === 0) {
        setCandidates(candidatesWithUsers)
        if (needsCount) setCandidatesTotalCount(count || 0)
      } else {
        setCandidates(prev => [...prev, ...candidatesWithUsers])
      }

      setCandidatesHasMore(data.length === PAGE_SIZE)
      setCandidatesPage(pageNumber)
    } catch (error) {
      console.error('Error loading candidates:', error)
    } finally {
      setCandidatesLoading(false)
      setCandidatesLoadingMore(false)
      candidatesLoadingRef.current = false
    }
  }

  const loadSubmitted = async (pageNumber = 0, reset = false) => {
    if (submittedLoadingRef.current) return
    submittedLoadingRef.current = true

    if (pageNumber === 0) {
      setSubmittedLoading(true)
    } else {
      setSubmittedLoadingMore(true)
    }

    try {
      const from = pageNumber * PAGE_SIZE
      const to = from + PAGE_SIZE - 1
      const needsCount = pageNumber === 0

      const { data, count, error } = await supabase
        .schema('comms')
        .from('video_testimonials')
        .select('*', needsCount ? { count: 'exact' } : undefined)
        .order('created_at', { ascending: false })
        .range(from, to)

      if (error) throw error

      // Fetch user details
      const userIds = data.map(s => s.user_id).filter(Boolean)
      let usersMap = {}

      if (userIds.length > 0) {
        const { data: users, error: usersError } = await supabase
          .from('users')
          .select('id, name, phone_number, email')
          .in('id', userIds)

        if (!usersError && users) {
          usersMap = users.reduce((acc, user) => {
            acc[user.id] = user
            return acc
          }, {})
        }
      }

      const submittedWithUsers = data.map(item => ({
        ...item,
        user: usersMap[item.user_id] || null
      }))

      if (reset || pageNumber === 0) {
        setSubmitted(submittedWithUsers)
        if (needsCount) setSubmittedTotalCount(count || 0)
      } else {
        setSubmitted(prev => [...prev, ...submittedWithUsers])
      }

      setSubmittedHasMore(data.length === PAGE_SIZE)
      setSubmittedPage(pageNumber)
    } catch (error) {
      console.error('Error loading submitted testimonials:', error)
    } finally {
      setSubmittedLoading(false)
      setSubmittedLoadingMore(false)
      submittedLoadingRef.current = false
    }
  }

  const handleSearch = () => {
    loadCandidates(0, true)
  }

  const updateStatus = async (id, newStatus) => {
    try {
      const { error } = await supabase
        .schema('comms')
        .from('video_testimonial_candidates')
        .update({ status: newStatus })
        .eq('id', id)

      if (error) throw error

      // Update local state
      setCandidates(prev => prev.map(c =>
        c.id === id ? { ...c, status: newStatus } : c
      ))
    } catch (error) {
      console.error('Error updating status:', error)
    }
  }

  const saveNotes = async (id) => {
    try {
      const { error } = await supabase
        .schema('comms')
        .from('video_testimonial_candidates')
        .update({ notes: editingNotesValue })
        .eq('id', id)

      if (error) throw error

      // Update local state
      setCandidates(prev => prev.map(c =>
        c.id === id ? { ...c, notes: editingNotesValue } : c
      ))
      setEditingNotes(null)
    } catch (error) {
      console.error('Error saving notes:', error)
    }
  }

  const copyOutreachMessage = async (candidate) => {
    const name = candidate.user?.name?.split(' ')[0] || 'there'
    const soberDays = candidate.sober_days || 0

    const message = `Hey ${name},
This is Thatcher from Clear30 (the weed break app)!!
Just saw you've checked in as sober ${soberDays} times, that's awesome!!!

I'm truly so proud and blown away and I hope you are too, cause that's a major feat and super impressive!!!! 🥹❤️

I wanted to see if you'd be down to do a video testimonial on your experience with Clear30, as you have a journey worth sharing !!

We would share it on social media to help motivate those who are struggling / need support.
(We break it down to make it very easy, and we'll pay you $25 after!)

Lmk what you think. If you're down, awesome, you can just respond and I can get everything set up!`

    try {
      await navigator.clipboard.writeText(message)
      setCopiedMessageId(candidate.id)
      setTimeout(() => setCopiedMessageId(null), 2000)
    } catch (error) {
      console.error('Failed to copy message:', error)
    }
  }

  const sendToTop = async (candidate) => {
    try {
      const now = new Date().toISOString()
      const { error } = await supabase
        .schema('comms')
        .from('video_testimonial_candidates')
        .update({ created_at: now })
        .eq('id', candidate.id)

      if (error) throw error

      // Move to top in local state
      setCandidates(prev => {
        const updated = prev.map(c =>
          c.id === candidate.id ? { ...c, created_at: now } : c
        )
        return updated.sort((a, b) => new Date(b.created_at) - new Date(a.created_at))
      })
    } catch (error) {
      console.error('Error sending to top:', error)
    }
  }

  const downloadAllVideos = async (item) => {
    const sections = item.sections || {}
    const entries = Object.entries(sections)
    const userName = item.user?.name?.replace(/\s+/g, '_') || 'unknown'

    setDownloadingId(item.id)
    setDownloadProgress({ current: 0, total: entries.length })

    for (let i = 0; i < entries.length; i++) {
      const [name, url] = entries[i]
      try {
        const response = await fetch(url)
        const blob = await response.blob()
        const blobUrl = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = blobUrl
        link.download = `${userName}_${name}.mp4`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(blobUrl)
        setDownloadProgress({ current: i + 1, total: entries.length })
        // Small delay between downloads
        await new Promise(resolve => setTimeout(resolve, 500))
      } catch (error) {
        console.error(`Failed to download ${name}:`, error)
        setDownloadProgress({ current: i + 1, total: entries.length })
      }
    }

    setDownloadingId(null)
  }

  // Scroll handlers
  const handleCandidatesScroll = useCallback(() => {
    if (!candidatesRef.current || candidatesLoadingMore || !candidatesHasMore || candidatesLoading) return

    const { scrollTop, scrollHeight, clientHeight } = candidatesRef.current
    if (scrollTop + clientHeight >= scrollHeight - 100) {
      loadCandidates(candidatesPage + 1, false)
    }
  }, [candidatesLoadingMore, candidatesHasMore, candidatesLoading, candidatesPage])

  const handleSubmittedScroll = useCallback(() => {
    if (!submittedRef.current || submittedLoadingMore || !submittedHasMore || submittedLoading) return

    const { scrollTop, scrollHeight, clientHeight } = submittedRef.current
    if (scrollTop + clientHeight >= scrollHeight - 100) {
      loadSubmitted(submittedPage + 1, false)
    }
  }, [submittedLoadingMore, submittedHasMore, submittedLoading, submittedPage])

  useEffect(() => {
    const element = candidatesRef.current
    if (element) {
      element.addEventListener('scroll', handleCandidatesScroll, { passive: true })
      return () => element.removeEventListener('scroll', handleCandidatesScroll)
    }
  }, [handleCandidatesScroll])

  useEffect(() => {
    const element = submittedRef.current
    if (element) {
      element.addEventListener('scroll', handleSubmittedScroll, { passive: true })
      return () => element.removeEventListener('scroll', handleSubmittedScroll)
    }
  }, [handleSubmittedScroll])

  const formatDate = (timestamp) => {
    return new Date(timestamp).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  }

  const getStatusBadge = (status) => {
    const option = STATUS_OPTIONS.find(o => o.value === status) || STATUS_OPTIONS[0]
    return (
      <span className={`px-2 py-1 rounded-full text-xs font-medium ${option.color}`}>
        {option.label}
      </span>
    )
  }

  const toggleExpanded = (id) => {
    setExpandedSubmitted(prev => ({
      ...prev,
      [id]: !prev[id]
    }))
  }

  const formatSectionName = (name) => {
    return name.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
  }

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center gap-2 sm:gap-3 mb-4 sm:mb-6 flex-shrink-0">
        <h1 className="text-xl sm:text-2xl font-bold text-gray-900">Testimonials</h1>
      </div>

      {/* Tabs */}
      <div className="bg-white rounded-t-lg shadow border-b flex-shrink-0">
        <div className="flex">
          <button
            onClick={() => setActiveTab('candidates')}
            className={`px-3 sm:px-6 py-2 sm:py-3 text-xs sm:text-sm font-medium border-b-2 transition-colors ${
              activeTab === 'candidates'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            Candidates
            {candidatesTotalCount > 0 && (
              <span className="ml-1 sm:ml-2 text-xs bg-gray-100 px-1.5 sm:px-2 py-0.5 rounded-full">
                {candidatesTotalCount}
              </span>
            )}
          </button>
          <button
            onClick={() => setActiveTab('submitted')}
            className={`px-3 sm:px-6 py-2 sm:py-3 text-xs sm:text-sm font-medium border-b-2 transition-colors ${
              activeTab === 'submitted'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            Submitted
            {submittedTotalCount > 0 && (
              <span className="ml-1 sm:ml-2 text-xs bg-gray-100 px-1.5 sm:px-2 py-0.5 rounded-full">
                {submittedTotalCount}
              </span>
            )}
          </button>
        </div>
      </div>

      {/* Candidates Tab */}
      {activeTab === 'candidates' && (
        <>
          {/* Filters */}
          <div className="bg-white shadow p-3 sm:p-4 flex-shrink-0 flex flex-col sm:flex-row gap-3 sm:gap-4">
            <div className="flex gap-2 flex-1">
              <div className="relative flex-1">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
                <input
                  type="text"
                  placeholder="Search..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
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
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="all">All Statuses</option>
              {STATUS_OPTIONS.map(option => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </div>

          {/* Candidates List */}
          <div className="bg-white rounded-b-lg shadow flex-1 min-h-0 flex flex-col">
            {candidatesLoading ? (
              <div className="p-8 text-center flex-1 flex items-center justify-center">
                <div>
                  <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
                  <p className="mt-2 text-gray-600">Loading candidates...</p>
                </div>
              </div>
            ) : candidates.length === 0 ? (
              <div className="p-8 text-center flex-1 flex items-center justify-center">
                <div>
                  <Video className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                  <p className="text-gray-600">No candidates found</p>
                </div>
              </div>
            ) : (
              <div ref={candidatesRef} className="flex-1 overflow-y-auto">
                <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50 sticky top-0 z-10">
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">User</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Sober Days</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Last Check-in</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Notes</th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Added</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {candidates.map((candidate) => (
                      <React.Fragment key={candidate.id}>
                        <tr className="hover:bg-gray-50">
                          <td className="px-4 py-3">
                            <div
                              className="flex items-center gap-2 cursor-pointer"
                              onClick={() => setExpandedCandidates(prev => ({
                                ...prev,
                                [candidate.id]: !prev[candidate.id]
                              }))}
                            >
                              {expandedCandidates[candidate.id] ? (
                                <ChevronDown className="h-4 w-4 text-gray-400 flex-shrink-0" />
                              ) : (
                                <ChevronRight className="h-4 w-4 text-gray-400 flex-shrink-0" />
                              )}
                              <span className="text-sm font-medium text-gray-900">
                                {candidate.user?.name || 'Unknown'}
                              </span>
                            </div>
                          </td>
                        <td className="px-4 py-3 text-sm text-gray-900">
                          {candidate.sober_days} days
                        </td>
                        <td className="px-4 py-3 text-sm text-gray-500">
                          {formatDate(candidate.last_check_in_at)}
                        </td>
                        <td className="px-4 py-3">
                          <select
                            value={candidate.status}
                            onChange={(e) => updateStatus(candidate.id, e.target.value)}
                            className={`text-xs font-medium rounded-full px-1 py-1 border-0 cursor-pointer ${
                              STATUS_OPTIONS.find(o => o.value === candidate.status)?.color || 'bg-gray-100'
                            }`}
                          >
                            {STATUS_OPTIONS.map(option => (
                              <option key={option.value} value={option.value}>{option.label}</option>
                            ))}
                          </select>
                        </td>
                        <td className="px-4 py-3">
                          {editingNotes === candidate.id ? (
                            <div className="flex gap-2">
                              <input
                                type="text"
                                value={editingNotesValue}
                                onChange={(e) => setEditingNotesValue(e.target.value)}
                                onKeyDown={(e) => {
                                  if (e.key === 'Enter') saveNotes(candidate.id)
                                  if (e.key === 'Escape') setEditingNotes(null)
                                }}
                                className="flex-1 text-sm px-2 py-1 border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                                autoFocus
                              />
                              <button
                                onClick={() => saveNotes(candidate.id)}
                                className="text-xs px-2 py-1 bg-blue-600 text-white rounded hover:bg-blue-700"
                              >
                                Save
                              </button>
                              <button
                                onClick={() => setEditingNotes(null)}
                                className="text-xs px-2 py-1 bg-gray-200 text-gray-700 rounded hover:bg-gray-300"
                              >
                                Cancel
                              </button>
                            </div>
                          ) : (
                            <div
                              onClick={() => {
                                setEditingNotes(candidate.id)
                                setEditingNotesValue(candidate.notes || '')
                              }}
                              className="text-sm text-gray-500 cursor-pointer hover:text-gray-700 min-h-[24px]"
                            >
                              {candidate.notes || <span className="text-gray-400 italic">Click to add notes...</span>}
                            </div>
                          )}
                        </td>
                        <td className="px-4 py-3 text-sm text-gray-500">
                          {formatDate(candidate.created_at)}
                        </td>
                      </tr>
                      {expandedCandidates[candidate.id] && (
                        <tr className="bg-gray-50">
                          <td colSpan={6} className="px-4 py-3">
                            <div className="ml-6 flex items-start gap-6">
                              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm flex-1">
                                <div>
                                  <div className="text-gray-400 text-xs uppercase mb-1">Name</div>
                                  <div className="text-gray-900">{candidate.user?.name || 'Unknown'}</div>
                                </div>
                                <div>
                                  <div className="text-gray-400 text-xs uppercase mb-1">User ID</div>
                                  <div className="text-gray-900 font-mono text-xs">{candidate.user_id}</div>
                                </div>
                                <div>
                                  <div className="text-gray-400 text-xs uppercase mb-1">Email</div>
                                  <div className="text-gray-900">{candidate.user?.email || 'N/A'}</div>
                                </div>
                                <div>
                                  <div className="text-gray-400 text-xs uppercase mb-1">Phone</div>
                                  <div className="text-gray-900">{candidate.user?.phone_number || 'N/A'}</div>
                                </div>
                              </div>
                              <div className="flex items-center gap-2">
                                <button
                                  onClick={(e) => {
                                    e.stopPropagation()
                                    copyOutreachMessage(candidate)
                                  }}
                                  className={`flex items-center gap-1.5 px-2 py-1 rounded text-xs font-medium transition-colors ${
                                    copiedMessageId === candidate.id
                                      ? 'bg-green-100 text-green-700'
                                      : 'bg-blue-100 text-blue-700 hover:bg-blue-200'
                                  }`}
                                >
                                  {copiedMessageId === candidate.id ? (
                                    <>
                                      <Check className="h-3 w-3" />
                                      Message Copied
                                    </>
                                  ) : (
                                    <>
                                      <Copy className="h-3 w-3" />
                                      Copy Message
                                    </>
                                  )}
                                </button>
                                <button
                                  onClick={(e) => {
                                    e.stopPropagation()
                                    sendToTop(candidate)
                                  }}
                                  className="flex items-center gap-1.5 px-2 py-1 rounded text-xs font-medium bg-gray-100 text-gray-700 hover:bg-gray-200 transition-colors"
                                >
                                  <ArrowUp className="h-3 w-3" />
                                  Send to Top
                                </button>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                      </React.Fragment>
                    ))}
                  </tbody>
                </table>
                </div>

                {candidatesLoadingMore && (
                  <div className="p-4 text-center">
                    <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
                    <p className="mt-2 text-sm text-gray-600">Loading more...</p>
                  </div>
                )}

                {!candidatesHasMore && candidates.length > 0 && (
                  <div className="p-4 text-center">
                    <p className="text-sm text-gray-500">
                      Showing {candidates.length} of {candidatesTotalCount} candidates
                    </p>
                  </div>
                )}
              </div>
            )}
          </div>
        </>
      )}

      {/* Submitted Tab */}
      {activeTab === 'submitted' && (
        <div className="bg-white rounded-b-lg shadow flex-1 min-h-0 flex flex-col">
          {submittedLoading ? (
            <div className="p-8 text-center flex-1 flex items-center justify-center">
              <div>
                <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
                <p className="mt-2 text-gray-600">Loading testimonials...</p>
              </div>
            </div>
          ) : submitted.length === 0 ? (
            <div className="p-8 text-center flex-1 flex items-center justify-center">
              <div>
                <Video className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                <p className="text-gray-600">No submitted testimonials yet</p>
              </div>
            </div>
          ) : (
            <div ref={submittedRef} className="divide-y divide-gray-200 flex-1 overflow-y-auto">
              {submitted.map((item) => (
                <div key={item.id} className="hover:bg-gray-50">
                  <div
                    className="p-4 cursor-pointer"
                    onClick={() => toggleExpanded(item.id)}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-4">
                        {expandedSubmitted[item.id] ? (
                          <ChevronDown className="h-5 w-5 text-gray-400" />
                        ) : (
                          <ChevronRight className="h-5 w-5 text-gray-400" />
                        )}
                        <div>
                          <div className="text-sm font-medium text-gray-900">
                            {item.user?.name || 'Unknown User'}
                          </div>
                          <div className="text-xs text-gray-500">
                            {item.user?.phone_number || item.user_id?.slice(0, 8) + '...'}
                          </div>
                        </div>
                      </div>
                      <div className="flex items-center gap-6 text-sm text-gray-500">
                        <div>
                          <span className="text-gray-400">Source:</span> {item.source || 'default'}
                        </div>
                        <div>
                          <span className="text-gray-400">Payment:</span> {item.payment_info}
                        </div>
                        <div>{formatDate(item.created_at)}</div>
                      </div>
                    </div>
                  </div>

                  {expandedSubmitted[item.id] && (
                    <div className="px-4 pb-4 pl-12">
                      <div className="bg-gray-50 rounded-lg p-4">
                        <div className="flex items-center justify-between mb-3">
                          <h4 className="text-sm font-medium text-gray-700">Video Sections</h4>
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              if (downloadingId !== item.id) {
                                downloadAllVideos(item)
                              }
                            }}
                            disabled={downloadingId === item.id}
                            className={`flex items-center gap-1.5 px-2 py-1 rounded text-xs font-medium transition-colors ${
                              downloadingId === item.id
                                ? 'bg-gray-100 text-gray-500 cursor-not-allowed'
                                : 'bg-blue-100 text-blue-700 hover:bg-blue-200'
                            }`}
                          >
                            {downloadingId === item.id ? (
                              <>
                                <div className="h-3 w-3 border-2 border-gray-400 border-t-transparent rounded-full animate-spin" />
                                {downloadProgress.current} / {downloadProgress.total}
                              </>
                            ) : (
                              <>
                                <Download className="h-3 w-3" />
                                Download All
                              </>
                            )}
                          </button>
                        </div>
                        <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                          {Object.entries(item.sections || {}).map(([name, url]) => (
                            <a
                              key={name}
                              href={url}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="flex items-center gap-2 text-sm text-blue-600 hover:text-blue-800 bg-white rounded px-3 py-2 border border-gray-200"
                            >
                              <Video className="h-4 w-4" />
                              {formatSectionName(name)}
                              <ExternalLink className="h-3 w-3 ml-auto" />
                            </a>
                          ))}
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ))}

              {submittedLoadingMore && (
                <div className="p-4 text-center">
                  <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
                  <p className="mt-2 text-sm text-gray-600">Loading more...</p>
                </div>
              )}

              {!submittedHasMore && submitted.length > 0 && (
                <div className="p-4 text-center">
                  <p className="text-sm text-gray-500">
                    Showing {submitted.length} of {submittedTotalCount} testimonials
                  </p>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default TestimonialsPage
