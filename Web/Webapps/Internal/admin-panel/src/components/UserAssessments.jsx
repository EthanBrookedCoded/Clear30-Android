import { useState, useEffect, memo } from 'react'
import { supabase } from '../lib/supabaseClient'
import { X, FileText, ChevronDown, ChevronRight, Download } from 'lucide-react'

function UserAssessments({ userId, userName, onClose, isEmbedded = false }) {
  const [assessments, setAssessments] = useState([])
  const [loading, setLoading] = useState(true)
  const [expandedAssessments, setExpandedAssessments] = useState(new Set())

  useEffect(() => {
    loadAssessments()
  }, [userId])

  const loadAssessments = async () => {
    setLoading(true)
    try {
      const { data, error } = await supabase
        .schema('programs')
        .from('program_assessment_responses')
        .select('*')
        .eq('user_id', userId)
        .order('timestamp', { ascending: false })

      if (error) throw error
      setAssessments(data || [])
      
      // Auto-expand the first (most recent) assessment when not embedded
      if (data && data.length > 0 && !isEmbedded) {
        setExpandedAssessments(new Set([data[0].id]))
      }
    } catch (error) {
      console.error('Error loading user assessments:', error)
    } finally {
      setLoading(false)
    }
  }

  const toggleAssessment = (assessmentId) => {
    const newExpanded = new Set(expandedAssessments)
    if (newExpanded.has(assessmentId)) {
      newExpanded.delete(assessmentId)
    } else {
      newExpanded.add(assessmentId)
    }
    setExpandedAssessments(newExpanded)
  }

  const formatQuestionLabel = (key) => {
    // Convert keys like "LO-Age", "Help-Harm", "Then-What" to readable labels
    return key
      .replace(/^LO-/, '')
      .replace(/-/g, ' ')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
  }

  const exportAssessment = (assessment) => {
    const exportData = {
      assessment: assessment.assessment,
      timestamp: assessment.timestamp,
      userId,
      userName,
      responses: assessment.responses
    }

    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `assessment-${userName || userId}-${assessment.assessment}-${new Date(assessment.timestamp).toISOString().split('T')[0]}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const formatTimestamp = (timestamp) => {
    const date = new Date(timestamp)
    if (isEmbedded) {
      return date.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: '2-digit'
      })
    }
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    })
  }

  const getAssessmentTitle = (assessment, index) => {
    const date = new Date(assessment.timestamp)
    const isToday = date.toDateString() === new Date().toDateString()
    const isRecent = index === 0

    if (isEmbedded) {
      return assessment.assessment
    }
    
    return `${assessment.assessment} ${isRecent ? '(Most Recent)' : ``}`
  }

  const assessmentContent = (
    <div className={`${isEmbedded ? 'h-full overflow-y-auto' : 'max-h-[70vh] overflow-y-auto'}`}>
      {loading ? (
        <div className="text-center py-8">
          <div className={`animate-spin rounded-full border-t-2 border-b-2 border-blue-500 mx-auto ${isEmbedded ? 'h-6 w-6' : 'h-8 w-8'}`}></div>
          <p className={`mt-2 text-gray-600 ${isEmbedded ? 'text-sm' : ''}`}>Loading assessments...</p>
        </div>
      ) : assessments.length === 0 ? (
        <div className="text-center py-8">
          <FileText className={`text-gray-400 mx-auto mb-4 ${isEmbedded ? 'h-8 w-8' : 'h-12 w-12'}`} />
          <p className={`text-gray-600 ${isEmbedded ? 'text-sm' : ''}`}>No assessment responses found</p>
        </div>
      ) : (
        <div className={`${isEmbedded ? 'space-y-2 p-2' : 'space-y-3'}`}>
          {assessments.map((assessment, index) => {
            const isExpanded = expandedAssessments.has(assessment.id)
            return (
              <div key={assessment.id} className="border border-gray-200 rounded-lg overflow-hidden">
                {/* Assessment Header - Clickable */}
                <button
                  onClick={() => toggleAssessment(assessment.id)}
                  className={`w-full bg-gray-50 hover:bg-gray-100 transition-colors text-left flex items-center justify-between ${isEmbedded ? 'px-3 py-2' : 'px-4 py-3'}`}
                >
                  <div className="flex items-center space-x-2 min-w-0 flex-1">
                    <div className={`flex-shrink-0 ${isExpanded ? 'text-blue-600' : 'text-gray-400'}`}>
                      {isExpanded ? (
                        <ChevronDown className={isEmbedded ? "h-4 w-4" : "h-5 w-5"} />
                      ) : (
                        <ChevronRight className={isEmbedded ? "h-4 w-4" : "h-5 w-5"} />
                      )}
                    </div>
                    <div className="min-w-0 flex-1">
                      <h4 className={`font-medium text-gray-900 truncate ${isEmbedded ? 'text-xs' : 'text-sm'}`}>
                        {getAssessmentTitle(assessment, index)}
                      </h4>
                      <p className={`text-gray-500 ${isEmbedded ? 'text-xs' : 'text-xs'}`}>
                        {formatTimestamp(assessment.timestamp)}
                      </p>
                    </div>
                  </div>
                  {!isEmbedded && (
                    <div className="flex items-center space-x-2">
                      {index === 0 && (
                        <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                          Latest
                        </span>
                      )}
                    </div>
                  )}
                </button>

                {/* Assessment Details - Expandable */}
                {isExpanded && (
                  <div className={`border-t border-gray-200 bg-white ${isEmbedded ? 'p-2' : 'p-4'}`}>
                    {/* Assessment Responses */}
                    {assessment.responses && typeof assessment.responses === 'object' ? (
                      <div className="space-y-2">
                        {Object.entries(assessment.responses).map(([key, value]) => (
                          <div key={key} className={`${isEmbedded ? 'py-1' : 'py-2'} border-b border-gray-100 last:border-0`}>
                            <div className={`font-medium text-gray-700 ${isEmbedded ? 'text-xs' : 'text-sm'}`}>
                              {formatQuestionLabel(key)}
                            </div>
                            <div className="flex flex-wrap gap-1 mt-1">
                              {Array.isArray(value) ? (
                                value.map((item, i) => (
                                  <span
                                    key={i}
                                    className={`inline-flex items-center rounded-full bg-blue-50 text-blue-700 ${isEmbedded ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-sm'}`}
                                  >
                                    {String(item)}
                                  </span>
                                ))
                              ) : (
                                <span className={`inline-flex items-center rounded-full bg-blue-50 text-blue-700 ${isEmbedded ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-sm'}`}>
                                  {String(value)}
                                </span>
                              )}
                            </div>
                          </div>
                        ))}
                        {/* Export Button */}
                        <div className="pt-2 flex justify-end">
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              exportAssessment(assessment)
                            }}
                            className={`flex items-center text-gray-600 hover:bg-gray-100 rounded transition-colors ${isEmbedded ? 'px-2 py-1 text-xs' : 'px-3 py-1.5 text-sm'}`}
                          >
                            <Download className={`${isEmbedded ? 'h-3 w-3' : 'h-4 w-4'} mr-1`} />
                            Export
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div className={`text-center text-gray-500 ${isEmbedded ? 'py-4' : 'py-8'}`}>
                        <FileText className={`mx-auto mb-2 text-gray-400 ${isEmbedded ? 'h-6 w-6' : 'h-8 w-8'}`} />
                        <p className={isEmbedded ? 'text-xs' : 'text-sm'}>No responses found for this assessment</p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )

  if (isEmbedded) {
    return assessmentContent
  }

  return (
    <div className="fixed inset-0 bg-gray-100 bg-opacity-50 overflow-y-auto h-full w-full z-50">
      <div className="relative top-20 mx-auto p-5 border w-11/12 max-w-5xl shadow-lg rounded-md bg-white">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-medium text-gray-900">
            Assessment Responses - {userName}
            {assessments.length > 0 && (
              <span className="text-sm text-gray-500 ml-2">
                ({assessments.length} total)
              </span>
            )}
          </h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <X className="h-6 w-6" />
          </button>
        </div>
        {assessmentContent}
      </div>
    </div>
  )
}

export default memo(UserAssessments)

