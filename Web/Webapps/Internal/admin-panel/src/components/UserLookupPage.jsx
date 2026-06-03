import { useState } from 'react'
import { supabase } from '../lib/supabaseClient'
import { Search, User, Mail, Phone, Copy, Calendar, FileText, Hash, Trash2 } from 'lucide-react'
import UserCalendar from './UserCalendar'
import UserAssessments from './UserAssessments'
import { toast } from 'sonner'

function UserLookupPage() {
  const [searchTerm, setSearchTerm] = useState('')
  const [loading, setLoading] = useState(false)
  const [user, setUser] = useState(null)
  const [error, setError] = useState('')
  const [searchDeleted, setSearchDeleted] = useState(false)

  const handleSearch = async () => {
    if (!searchTerm.trim()) {
      setError('Please enter a search term')
      return
    }

    setLoading(true)
    setError('')
    setUser(null)

    try {
      const term = searchTerm.trim()

      if (searchDeleted) {
        // Search deleted users via RPC
        const { data, error: rpcError } = await supabase.rpc('search_deleted_users', {
          search_term: term
        })

        if (rpcError) throw rpcError

        if (!data || data.length === 0) {
          setError('No deleted user found matching your search')
          return
        }

        // Mark as deleted and normalize shape
        setUser({ ...data[0], _isDeleted: true })
        return
      }

      // UUID pattern for user ID
      const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

      // Email pattern
      const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

      // Phone pattern (digits, possibly with + or dashes)
      const phonePattern = /^[\+\d\-\(\)\s]+$/

      let query = supabase
        .from('users')
        .select('id, name, email, phone_number, logging_id')

      if (uuidPattern.test(term)) {
        // Direct UUID lookup
        query = query.eq('id', term)
      } else if (emailPattern.test(term)) {
        // Email search
        query = query.ilike('email', `%${term}%`)
      } else if (phonePattern.test(term) && term.replace(/[\-\(\)\s]/g, '').length >= 7) {
        // Phone number search (clean and search)
        const cleanPhone = term.replace(/[\-\(\)\s]/g, '')
        query = query.ilike('phone_number', `%${cleanPhone}%`)
      } else {
        // General search - try name, email, phone first
        query = query.or(
          `name.ilike.%${term}%,email.ilike.%${term}%,phone_number.ilike.%${term}%`
        )
      }

      let { data, error: queryError } = await query.limit(1)

      if (queryError) throw queryError

      // If no result found, try logging_id array search (logging_id is an array)
      // Uses PostgreSQL ANY() operator: WHERE 'value' = ANY(logging_id)
      if (!data || data.length === 0) {

        const loggingIdQuery = await supabase
          .from('users')
          .select('id, name, email, phone_number, logging_id')
          .filter('logging_id', 'cs', `{${term}}`)
          .limit(1)

        if (!loggingIdQuery.error && loggingIdQuery.data?.length > 0) {
          data = loggingIdQuery.data
        }
      }

      if (!data || data.length === 0) {
        setError('No user found matching your search')
        return
      }

      setUser(data[0])
    } catch (err) {
      console.error('Error searching for user:', err)
      setError('An error occurred while searching. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const copyToClipboard = async (text, field) => {
    try {
      await navigator.clipboard.writeText(text)
      toast.success(`${field} copied to clipboard!`, { duration: 2000 })
    } catch (err) {
      toast.error('Failed to copy', { duration: 2000 })
    }
  }

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="flex justify-between items-center mb-6 flex-shrink-0">
        <h1 className="text-2xl font-bold text-gray-900">User Lookup</h1>
      </div>

      {/* Search */}
      <div className="bg-white rounded-lg shadow mb-6 p-4 flex-shrink-0">
        {/* Active / Deleted toggle */}
        <div className="flex items-center gap-2 mb-3">
          <button
            onClick={() => { setSearchDeleted(false); setUser(null); setError('') }}
            className={`px-3 py-1.5 text-sm font-medium rounded-md transition-colors ${
              !searchDeleted
                ? 'bg-blue-100 text-blue-700'
                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-100'
            }`}
          >
            <User className="inline h-3.5 w-3.5 mr-1 -mt-0.5" />
            Active Users
          </button>
          <button
            onClick={() => { setSearchDeleted(true); setUser(null); setError('') }}
            className={`px-3 py-1.5 text-sm font-medium rounded-md transition-colors ${
              searchDeleted
                ? 'bg-red-100 text-red-700'
                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-100'
            }`}
          >
            <Trash2 className="inline h-3.5 w-3.5 mr-1 -mt-0.5" />
            Deleted Users
          </button>
        </div>

        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              placeholder={searchDeleted
                ? "Search deleted users by ID, name, email, phone, or logging ID..."
                : "Search by phone number, email, user ID, or logging ID..."
              }
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              className={`w-full pl-10 pr-4 py-2 border rounded-md focus:outline-none focus:ring-2 focus:border-transparent ${
                searchDeleted
                  ? 'border-red-200 focus:ring-red-500'
                  : 'border-gray-300 focus:ring-blue-500'
              }`}
            />
          </div>
          <button
            onClick={handleSearch}
            disabled={loading}
            className={`px-4 py-2 text-white rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2 transition-colors disabled:opacity-50 ${
              searchDeleted
                ? 'bg-red-600 hover:bg-red-700 focus:ring-red-500'
                : 'bg-blue-600 hover:bg-blue-700 focus:ring-blue-500'
            }`}
          >
            {loading ? 'Searching...' : 'Search'}
          </button>
        </div>

        {error && (
          <div className="mt-3 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">
            {error}
          </div>
        )}
      </div>

      {/* Results Area */}
      <div className="bg-white rounded-lg shadow flex-1 min-h-0 flex flex-col overflow-hidden">
        {loading ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
              <p className="mt-2 text-gray-600">Searching...</p>
            </div>
          </div>
        ) : !user ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <User className="h-12 w-12 text-gray-400 mx-auto mb-4" />
              <p className="text-gray-600">
                Enter a phone number, email, user ID, or logging ID to search
              </p>
            </div>
          </div>
        ) : (
          <div className="flex flex-col lg:flex-row flex-1 min-h-0">
            {/* User Info Panel - Full width on mobile, half on desktop */}
            <div className={`w-full ${user._isDeleted ? '' : 'lg:w-1/2 lg:border-r border-b lg:border-b-0'} border-gray-200 p-4 lg:p-6 overflow-y-auto`}>
              <div className="flex items-center gap-3 mb-4">
                <h3 className="text-lg font-semibold text-gray-900">User Information</h3>
                {user._isDeleted && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700">
                    <Trash2 className="h-3 w-3" />
                    Deleted
                  </span>
                )}
              </div>

              {/* Deleted At */}
              {user._isDeleted && user.deleted_at && (
                <div className="mb-4">
                  <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">Deleted At</label>
                  <p className="text-sm text-red-600 mt-1">{new Date(user.deleted_at).toLocaleString()}</p>
                </div>
              )}

              {/* Name */}
              <div className="mb-4">
                <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">Name</label>
                <p className="text-sm text-gray-900 mt-1">{user.name || 'Not provided'}</p>
              </div>

              {/* Email */}
              <div className="mb-4">
                <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">Email</label>
                <div className="flex items-center gap-2 mt-1">
                  <Mail className="h-4 w-4 text-gray-400 flex-shrink-0" />
                  <p className="text-sm text-gray-900 flex-1 break-all">{user.email || 'Not provided'}</p>
                  {user.email && (
                    <button
                      onClick={() => copyToClipboard(user.email, 'Email')}
                      className="p-1 hover:bg-gray-100 rounded transition-colors flex-shrink-0"
                      title="Copy email"
                    >
                      <Copy className="h-4 w-4 text-gray-400" />
                    </button>
                  )}
                </div>
              </div>

              {/* Phone */}
              <div className="mb-4">
                <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">Phone</label>
                <div className="flex items-center gap-2 mt-1">
                  <Phone className="h-4 w-4 text-gray-400 flex-shrink-0" />
                  <p className="text-sm text-gray-900 flex-1">{user.phone_number || 'Not provided'}</p>
                  {user.phone_number && (
                    <button
                      onClick={() => copyToClipboard(user.phone_number, 'Phone')}
                      className="p-1 hover:bg-gray-100 rounded transition-colors flex-shrink-0"
                      title="Copy phone"
                    >
                      <Copy className="h-4 w-4 text-gray-400" />
                    </button>
                  )}
                </div>
              </div>

              {/* User ID */}
              <div className="mb-4">
                <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">User ID</label>
                <div className="flex items-center gap-2 mt-1">
                  <p className="text-xs text-gray-600 font-mono flex-1 break-all">{user.id}</p>
                  <button
                    onClick={() => copyToClipboard(user.id, 'User ID')}
                    className="p-1 hover:bg-gray-100 rounded transition-colors flex-shrink-0"
                    title="Copy user ID"
                  >
                    <Copy className="h-4 w-4 text-gray-400" />
                  </button>
                </div>
              </div>

              {/* Logging ID */}
              <div className="mb-4">
                <label className="text-xs font-medium text-gray-500 uppercase tracking-wider">Logging ID</label>
                <div className="flex items-center gap-2 mt-1">
                  <Hash className="h-4 w-4 text-gray-400 flex-shrink-0" />
                  <p className="text-xs text-gray-600 font-mono flex-1 break-all">{
                    Array.isArray(user.logging_id) ? user.logging_id.join(', ') : (user.logging_id || 'Not set')
                  }</p>
                  {user.logging_id && (
                    <button
                      onClick={() => copyToClipboard(
                        Array.isArray(user.logging_id) ? user.logging_id.join(', ') : user.logging_id,
                        'Logging ID'
                      )}
                      className="p-1 hover:bg-gray-100 rounded transition-colors flex-shrink-0"
                      title="Copy logging ID"
                    >
                      <Copy className="h-4 w-4 text-gray-400" />
                    </button>
                  )}
                </div>
              </div>

            </div>

            {/* Calendar & Assessments - Hidden for deleted users */}
            {!user._isDeleted && (
              <div className="w-full lg:w-1/2 overflow-y-auto flex-1 lg:flex-none">
                {/* Calendar Section */}
                <div className="border-b border-gray-200">
                  <div className="bg-white border-b border-gray-200 px-4 py-2 sticky top-0 z-10">
                    <div className="flex items-center">
                      <Calendar className="h-4 w-4 text-gray-600 mr-2" />
                      <h4 className="text-sm font-medium text-gray-900">Check-in Calendar</h4>
                    </div>
                  </div>
                  <div>
                    <UserCalendar
                      userId={user.id}
                      userName={user.name}
                      isEmbedded={true}
                    />
                  </div>
                </div>

                {/* Assessments Section */}
                <div>
                  <div className="bg-white border-b border-gray-200 px-4 py-2 sticky top-0 z-10">
                    <div className="flex items-center">
                      <FileText className="h-4 w-4 text-gray-600 mr-2" />
                      <h4 className="text-sm font-medium text-gray-900">Assessment Responses</h4>
                    </div>
                  </div>
                  <div>
                    <UserAssessments
                      userId={user.id}
                      userName={user.name}
                      isEmbedded={true}
                    />
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export default UserLookupPage
