import { useState, useEffect, useRef, useCallback } from 'react'
import { supabase } from '../lib/supabaseClient'
import { FileText } from 'lucide-react'

function AssessmentsPage() {
  const [assessments, setAssessments] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [selectedAssessment, setSelectedAssessment] = useState('clear30')
  const [page, setPage] = useState(0)
  const [totalCount, setTotalCount] = useState(0)
  const [columns, setColumns] = useState([])
  const [hasMore, setHasMore] = useState(true)
  const assessmentsRef = useRef(null)
  const isLoadingRef = useRef(false)
  const PAGE_SIZE = 10 // Smaller page size to prevent timeouts

  const assessmentTypes = {
    clear30: { label: 'Clear30', assessment: 'clear30' },
    life: { label: 'Life', assessment: 'life' }
  }

  // Load on mount and when assessment type changes
  useEffect(() => {
    loadAssessments(0, true)
  }, [selectedAssessment])

  const loadAssessments = async (pageNumber = 0, reset = false) => {
    if (isLoadingRef.current) return
    isLoadingRef.current = true

    if (pageNumber === 0) {
      setLoading(true)
    } else {
      setLoadingMore(true)
    }

    try {
      const assessmentFilter = assessmentTypes[selectedAssessment].assessment
      const from = pageNumber * PAGE_SIZE
      const to = from + PAGE_SIZE - 1

      // Only get count on first page to reduce query load
      const needsCount = pageNumber === 0
      const { data: rawData, count, error } = await supabase
        .schema('programs')
        .from('program_assessment_responses')
        .select('id, user_id, assessment, responses, timestamp', needsCount ? { count: 'exact' } : undefined)
        .eq('assessment', assessmentFilter)
        .order('timestamp', { ascending: false })
        .range(from, to)

      if (error) throw error

      // Transform data - skip user name lookup to prevent timeout
      const data = rawData?.map(row => {
        const responses = row.responses || {}
        const transformed = {
          response_id: row.id,
          user_id: row.user_id,
          timestamp: row.timestamp
        }

        if (assessmentFilter === 'clear30') {
          transformed['Age'] = responses['LO-Age']
          transformed['Consumption Method'] = responses['Consumption-Method']
          transformed['Days Using'] = responses['Days-Using']
          transformed['Triggers'] = responses['Trigger']
          transformed['Help vs Harm'] = responses['Help-Harm']
          transformed['Previous Break'] = responses['Previous-Break']
          transformed['Break Reason'] = responses['Break-Reason']
          transformed['30 Day Goal'] = responses['Goal30']
          transformed['Commitment'] = responses['Commitment']
          transformed['Then What'] = responses['Then-What']
          transformed['Money Spent'] = responses['Money-Spent']
          transformed['Start Date'] = responses['Start-Date']
        } else if (assessmentFilter === 'life') {
          Object.keys(responses).forEach(key => {
            transformed[key] = responses[key]
          })
        }

        return transformed
      }) || []

      if (reset || pageNumber === 0) {
        setAssessments(data)
        if (needsCount) setTotalCount(count || 0)
        if (data.length > 0) {
          setColumns(Object.keys(data[0]))
        } else {
          setColumns([])
        }
      } else {
        setAssessments(prev => [...prev, ...data])
      }

      setHasMore(data.length === PAGE_SIZE)
      setPage(pageNumber)
    } catch (error) {
      console.error('Error loading assessments:', error)
    } finally {
      setLoading(false)
      setLoadingMore(false)
      isLoadingRef.current = false
    }
  }

  // Scroll handler for infinite scroll
  const handleScroll = useCallback(() => {
    if (!assessmentsRef.current || loadingMore || !hasMore || loading) return

    const { scrollTop, scrollHeight, clientHeight } = assessmentsRef.current
    if (scrollTop + clientHeight >= scrollHeight - 100) {
      loadAssessments(page + 1, false)
    }
  }, [loadingMore, hasMore, loading, page])

  useEffect(() => {
    const element = assessmentsRef.current
    if (element) {
      element.addEventListener('scroll', handleScroll, { passive: true })
      return () => element.removeEventListener('scroll', handleScroll)
    }
  }, [handleScroll])

  const formatValue = (value) => {
    if (value === null || value === undefined) return '-'
    if (typeof value === 'object') {
      return (
        <pre className="text-xs bg-gray-100 p-2 rounded overflow-x-auto whitespace-pre-wrap max-w-md">
          {JSON.stringify(value, null, 2)}
        </pre>
      )
    }
    if (typeof value === 'boolean') return value ? 'Yes' : 'No'
    if (typeof value === 'string' && value.length > 100) {
      return <span title={value}>{value.substring(0, 100)}...</span>
    }
    return String(value)
  }

  const formatColumnName = (columnName) => {
    return columnName.split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ')
  }

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-between items-center mb-6 flex-shrink-0">
        <h1 className="text-2xl font-bold text-gray-900">Assessment Responses</h1>
        <div className="flex items-center space-x-4">
          <label className="text-sm font-medium text-gray-700">Assessment Type:</label>
          <select
            value={selectedAssessment}
            onChange={(e) => setSelectedAssessment(e.target.value)}
            className="border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            {Object.entries(assessmentTypes).map(([key, type]) => (
              <option key={key} value={key}>{type.label}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow flex-1 min-h-0 flex flex-col">
        {loading ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
              <p className="mt-2 text-gray-600">Loading assessments...</p>
            </div>
          </div>
        ) : assessments.length === 0 ? (
          <div className="p-8 text-center flex-1 flex items-center justify-center">
            <div>
              <FileText className="h-12 w-12 text-gray-400 mx-auto mb-4" />
              <p className="text-gray-600">No assessment responses found</p>
            </div>
          </div>
        ) : (
          <div ref={assessmentsRef} className="flex-1 overflow-y-auto">
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200 table-auto">
                <thead className="bg-gray-50 sticky top-0 z-10">
                  <tr>
                    {columns.map((column) => (
                      <th key={column} className="px-8 py-4 text-left text-xs font-medium text-gray-500 uppercase tracking-wider min-w-[150px]">
                        {formatColumnName(column)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {assessments.map((assessment, index) => (
                    <tr key={assessment.response_id || index} className="hover:bg-gray-50">
                      {columns.map((column) => (
                        <td key={column} className="px-8 py-6 text-sm text-gray-900 align-top min-w-[150px] max-w-[300px]">
                          {formatValue(assessment[column])}
                        </td>
                      ))}
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

            {!hasMore && assessments.length > 0 && (
              <div className="p-4 text-center">
                <p className="text-sm text-gray-500">Showing {assessments.length} of {totalCount} assessments</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export default AssessmentsPage
