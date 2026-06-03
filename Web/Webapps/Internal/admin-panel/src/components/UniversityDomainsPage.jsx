import { useState, useEffect, useRef, useCallback } from 'react'
import { supabase } from '../lib/supabaseClient'
import { GraduationCap, Search, Plus, Copy, Check, X } from 'lucide-react'
import { toast } from 'sonner'

function UniversityDomainsPage() {
  const [domains, setDomains] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const [page, setPage] = useState(0)
  const [totalCount, setTotalCount] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [showAddModal, setShowAddModal] = useState(false)
  const [copiedDomain, setCopiedDomain] = useState(null)
  const domainsRef = useRef(null)
  const isLoadingRef = useRef(false)
  const PAGE_SIZE = 20

  // Form state for new domain
  const [newDomain, setNewDomain] = useState({ domain: '', org: '', mascot: '' })
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    loadDomains(0, true)
  }, [])

  const loadDomains = async (pageNumber = 0, reset = false) => {
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
        .schema('payment')
        .from('domain_allowlist')
        .select('*', { count: 'exact' })
        .order('created_at', { ascending: false })
        .range(from, to)

      if (searchTerm) {
        query = query.or(`domain.ilike.%${searchTerm}%,org.ilike.%${searchTerm}%`)
      }

      const { data, count, error } = await query

      if (error) throw error

      if (reset || pageNumber === 0) {
        setDomains(data || [])
        setTotalCount(count || 0)
      } else {
        setDomains(prev => [...prev, ...(data || [])])
      }

      setHasMore((data?.length || 0) === PAGE_SIZE)
      setPage(pageNumber)
    } catch (error) {
      console.error('Error loading domains:', error)
      toast.error('Failed to load domains')
    } finally {
      setLoading(false)
      setLoadingMore(false)
      isLoadingRef.current = false
    }
  }

  const handleSearch = () => {
    loadDomains(0, true)
  }

  const handleScroll = useCallback(() => {
    if (!domainsRef.current || loadingMore || !hasMore || loading) return

    const { scrollTop, scrollHeight, clientHeight } = domainsRef.current
    if (scrollTop + clientHeight >= scrollHeight - 100) {
      setLoadingMore(true)
      loadDomains(page + 1, false)
    }
  }, [loadingMore, hasMore, loading, page])

  useEffect(() => {
    const element = domainsRef.current
    if (element) {
      element.addEventListener('scroll', handleScroll, { passive: true })
      return () => element.removeEventListener('scroll', handleScroll)
    }
  }, [handleScroll])

  const generateLink = (entry) => {
    const params = [
      `school=${encodeURIComponent(entry.org || '')}`,
      `students=${encodeURIComponent(entry.mascot || '')}`,
      `email=${encodeURIComponent(`@${entry.domain}`)}`
    ].join('&')
    return `https://clear30.org/schools/referral/?${params}`
  }

  const copyLink = async (entry) => {
    const link = generateLink(entry)
    try {
      await navigator.clipboard.writeText(link)
      setCopiedDomain(entry.domain)
      toast.success('Link copied to clipboard')
      setTimeout(() => setCopiedDomain(null), 2000)
    } catch (error) {
      console.error('Failed to copy:', error)
      toast.error('Failed to copy link')
    }
  }

  const handleAddDomain = async (e) => {
    e.preventDefault()
    if (!newDomain.domain.trim()) {
      toast.error('Domain is required')
      return
    }

    setSubmitting(true)
    try {
      const { error } = await supabase
        .schema('payment')
        .from('domain_allowlist')
        .insert({
          domain: newDomain.domain.trim().toLowerCase(),
          org: newDomain.org.trim(),
          mascot: newDomain.mascot.trim()
        })

      if (error) throw error

      toast.success('Domain added successfully')
      setShowAddModal(false)
      setNewDomain({ domain: '', org: '', mascot: '' })
      loadDomains(0, true)
    } catch (error) {
      console.error('Error adding domain:', error)
      if (error.code === '23505') {
        toast.error('This domain already exists')
      } else {
        toast.error('Failed to add domain')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const formatDate = (timestamp) => {
    return new Date(timestamp).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    })
  }

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-3 mb-4 sm:mb-6 flex-shrink-0">
        <div className="flex items-center gap-2 sm:gap-3">
          <h1 className="text-xl sm:text-2xl font-bold text-gray-900">University Domains</h1>
          {totalCount > 0 && (
            <span className="text-xs sm:text-sm text-gray-500">({totalCount})</span>
          )}
        </div>
        <button
          onClick={() => setShowAddModal(true)}
          className="inline-flex items-center justify-center px-3 sm:px-4 py-2 bg-blue-600 text-white text-sm rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-colors"
        >
          <Plus className="h-4 w-4 sm:mr-2" />
          <span className="hidden sm:inline">Add Domain</span>
          <span className="sm:hidden ml-1">Add</span>
        </button>
      </div>

      {/* Search */}
      <div className="bg-white rounded-lg shadow mb-4 sm:mb-6 p-3 sm:p-4 flex-shrink-0">
        <div className="flex gap-2">
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
      </div>

      {/* Domains Table */}
      <div className="bg-white rounded-lg shadow flex-1 min-h-0 flex flex-col">
        {loading ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
              <p className="mt-2 text-gray-600">Loading domains...</p>
            </div>
          </div>
        ) : domains.length === 0 ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <GraduationCap className="h-12 w-12 text-gray-400 mx-auto mb-4" />
              <p className="text-gray-600">No domains found</p>
            </div>
          </div>
        ) : (
          <div ref={domainsRef} className="flex-1 overflow-y-auto">
            <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50 sticky top-0">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Domain</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Organization</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Mascot</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Uses</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {domains.map((domain) => (
                  <tr key={domain.domain} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="text-sm font-medium text-gray-900">@{domain.domain}</span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="text-sm text-gray-900">{domain.org || '-'}</span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="text-sm text-gray-900">{domain.mascot || '-'}</span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="text-sm text-gray-500">{domain.uses || 0}</span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="text-sm text-gray-500">{formatDate(domain.created_at)}</span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <button
                        onClick={() => copyLink(domain)}
                        className="inline-flex items-center px-3 py-1.5 text-sm font-medium rounded-md text-blue-700 bg-blue-100 hover:bg-blue-200 transition-colors"
                      >
                        {copiedDomain === domain.domain ? (
                          <>
                            <Check className="h-4 w-4 mr-1" />
                            Copied
                          </>
                        ) : (
                          <>
                            <Copy className="h-4 w-4 mr-1" />
                            Copy Link
                          </>
                        )}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>

            {loadingMore && (
              <div className="p-4 text-center">
                <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
                <p className="mt-2 text-sm text-gray-600">Loading more...</p>
              </div>
            )}

            {!hasMore && domains.length > 0 && (
              <div className="p-4 text-center">
                <p className="text-sm text-gray-500">
                  Showing {domains.length} of {totalCount} domains
                </p>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Add Domain Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-gray-500/20 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4">
            <div className="flex items-center justify-between px-6 py-4 border-b">
              <h2 className="text-lg font-semibold text-gray-900">Add University Domain</h2>
              <button
                onClick={() => setShowAddModal(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleAddDomain} className="p-6">
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Domain <span className="text-red-500">*</span>
                  </label>
                  <div className="flex items-center">
                    <span className="text-gray-500 mr-1">@</span>
                    <input
                      type="text"
                      value={newDomain.domain}
                      onChange={(e) => setNewDomain({ ...newDomain, domain: e.target.value })}
                      placeholder="university.edu"
                      className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      required
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Organization Name
                  </label>
                  <input
                    type="text"
                    value={newDomain.org}
                    onChange={(e) => setNewDomain({ ...newDomain, org: e.target.value })}
                    placeholder="University of Example"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                  <p className="mt-1 text-xs text-gray-500">Used as "school" in referral link</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Mascot Name
                  </label>
                  <input
                    type="text"
                    value={newDomain.mascot}
                    onChange={(e) => setNewDomain({ ...newDomain, mascot: e.target.value })}
                    placeholder="Eagles"
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                  <p className="mt-1 text-xs text-gray-500">Used as "students" in referral link</p>
                </div>
              </div>
              <div className="mt-6 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-colors disabled:opacity-50"
                >
                  {submitting ? 'Adding...' : 'Add Domain'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

export default UniversityDomainsPage
