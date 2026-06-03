import { useState, useEffect } from 'react'
import { supabase } from '../lib/supabaseClient'
import { GraduationCap, FileText, Loader2 } from 'lucide-react'

const SUPABASE_URL = 'https://quluipmdicjsolnsopkg.supabase.co'

function UniversityReportsPage() {
    const [universities, setUniversities] = useState([])
    const [loading, setLoading] = useState(true)
    const [generatingReports, setGeneratingReports] = useState({}) // Track which report is being generated

    useEffect(() => {
        loadUniversities()
    }, [])

    const loadUniversities = async () => {
        setLoading(true)
        try {
            const { data, error } = await supabase
                .schema('payment')
                .from('domain_allowlist')
                .select('*')
                .order('uses', { ascending: false })

            if (error) throw error
            setUniversities(data || [])
        } catch (error) {
            console.error('Error loading universities:', error)
        } finally {
            setLoading(false)
        }
    }

    const generateReport = async (domain, quarter) => {
        const reportKey = `${domain}-${quarter}`
        setGeneratingReports(prev => ({ ...prev, [reportKey]: true }))

        try {
            // Get current year
            const year = new Date().getFullYear()

            // Format quarter as "Q1", "Q2", etc.
            const quarterFormatted = `Q${quarter}`

            // Get the current session to get the access token
            const { data: { session }, error: sessionError } = await supabase.auth.getSession()

            if (sessionError || !session || !session.access_token) {
                throw new Error('Not authenticated. Please log in again.')
            }

            // Use direct fetch to avoid CORS issues with Supabase client's automatic headers
            const response = await fetch(`${SUPABASE_URL}/functions/v1/reports_university`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${session.access_token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    domain,
                    quarter: quarterFormatted,
                    year
                })
            })

            if (!response.ok) {
                const errorData = await response.text()
                throw new Error(errorData || `HTTP error! status: ${response.status}`)
            }

            // Get the content type to determine file type
            const contentType = response.headers.get('content-type') || ''

            // Get filename from Content-Disposition header if available
            const contentDisposition = response.headers.get('content-disposition')
            let filename = `report_${domain}_${quarterFormatted}_${year}`

            if (contentDisposition) {
                const filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/)
                if (filenameMatch && filenameMatch[1]) {
                    filename = filenameMatch[1].replace(/['"]/g, '')
                }
            } else {
                // Determine file extension from content type
                if (contentType.includes('csv')) {
                    filename += '.csv'
                } else if (contentType.includes('excel') || contentType.includes('spreadsheet')) {
                    filename += '.xlsx'
                } else if (contentType.includes('pdf')) {
                    filename += '.pdf'
                } else if (contentType.includes('json')) {
                    filename += '.json'
                }
            }

            // Convert response to blob
            const blob = await response.blob()

            // Create download link and trigger download
            const url = window.URL.createObjectURL(blob)
            const link = document.createElement('a')
            link.href = url
            link.download = filename
            document.body.appendChild(link)
            link.click()

            // Clean up
            document.body.removeChild(link)
            window.URL.revokeObjectURL(url)

            console.log('Report downloaded successfully:', filename)
        } catch (error) {
            console.error('Error generating report:', error)
            alert(`Failed to generate report: ${error.message || 'Please try again.'}`)
        } finally {
            setGeneratingReports(prev => ({ ...prev, [reportKey]: false }))
        }
    }

    const getCurrentQuarter = () => {
        const month = new Date().getMonth() + 1
        if (month >= 1 && month <= 3) return 1
        if (month >= 4 && month <= 6) return 2
        if (month >= 7 && month <= 9) return 3
        return 4
    }

    const currentQuarter = getCurrentQuarter()

    return (
        <div className="h-full flex flex-col">
            <div className="flex justify-between items-center mb-6 flex-shrink-0">
                <div className="flex items-center">
                    <GraduationCap className="h-6 w-6 text-gray-600 mr-3" />
                    <h1 className="text-2xl font-bold text-gray-900">University Reports</h1>
                </div>
                <div className="text-sm text-gray-500">
                    {universities.length} universities
                </div>
            </div>

            {/* Universities List */}
            <div className="bg-white rounded-lg shadow flex-1 min-h-0 flex flex-col">
                {loading ? (
                    <div className="p-8 text-center flex-1 flex items-center justify-center">
                        <div>
                            <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
                            <p className="mt-2 text-gray-600">Loading universities...</p>
                        </div>
                    </div>
                ) : universities.length === 0 ? (
                    <div className="p-8 text-center flex-1 flex items-center justify-center">
                        <div>
                            <GraduationCap className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                            <p className="text-gray-600">No universities found</p>
                        </div>
                    </div>
                ) : (
                    <div className="flex-1 overflow-y-auto">
                        <div className="divide-y divide-gray-200">
                            {universities.map((university) => {
                                const domain = university.domain
                                return (
                                    <div
                                        key={domain}
                                        className="p-6 hover:bg-gray-50 transition-colors"
                                    >
                                        <div className="flex items-center justify-between">
                                            <div className="flex-1">
                                                <div className="flex items-center space-x-3 mb-2">
                                                    <h3 className="text-lg font-semibold text-gray-900">
                                                        {university.org || 'Unknown University'}
                                                    </h3>
                                                    {university.school_id && (
                                                        <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                                                            {university.school_id}
                                                        </span>
                                                    )}
                                                </div>
                                                <div className="flex items-center space-x-4 text-sm text-gray-600">
                                                    <span className="font-mono">{domain}</span>
                                                    {university.uses !== null && (
                                                        <span>{university.uses} uses</span>
                                                    )}
                                                </div>
                                            </div>

                                            {/* Quarter Buttons */}
                                            <div className="flex items-center space-x-2 ml-6">
                                                {[1, 2, 3, 4].map((quarter) => {
                                                    const reportKey = `${domain}-${quarter}`
                                                    const isGenerating = generatingReports[reportKey]
                                                    const isCurrentQuarter = quarter === currentQuarter

                                                    return (
                                                        <button
                                                            key={quarter}
                                                            onClick={() => generateReport(domain, quarter)}
                                                            disabled={isGenerating}
                                                            className={`inline-flex items-center px-4 py-2 border text-sm font-medium rounded-md transition-colors ${isCurrentQuarter
                                                                ? 'border-blue-300 text-blue-700 bg-blue-50 hover:bg-blue-100'
                                                                : 'border-gray-300 text-gray-700 bg-white hover:bg-gray-50'
                                                                } disabled:opacity-50 disabled:cursor-not-allowed`}
                                                        >
                                                            {isGenerating ? (
                                                                <>
                                                                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                                                                    Generating...
                                                                </>
                                                            ) : (
                                                                <>
                                                                    <FileText className="h-4 w-4 mr-2" />
                                                                    Q{quarter}
                                                                </>
                                                            )}
                                                        </button>
                                                    )
                                                })}
                                            </div>
                                        </div>
                                    </div>
                                )
                            })}
                        </div>
                    </div>
                )}
            </div>
        </div>
    )
}

export default UniversityReportsPage

