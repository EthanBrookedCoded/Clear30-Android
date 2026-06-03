import { useState, useEffect, memo } from 'react'
import { supabase } from '../lib/supabaseClient'
import { X, Calendar as CalendarIcon, ChevronLeft, ChevronRight, Download } from 'lucide-react'

function UserCalendar({ userId, userName, onClose, isEmbedded = false }) {
  const [userInfo, setUserInfo] = useState(null)
  const [currentDate, setCurrentDate] = useState(new Date())
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadUserInfo()
  }, [userId])

  const loadUserInfo = async () => {
    setLoading(true)
    try {
      const { data, error } = await supabase
        .from('users')
        .select('day_info, start_date')
        .eq('id', userId)
        .single()

      if (error) throw error

      // Convert to dictionary s )
      const dayInfoArray = data.day_info;
      const dayInfoDict = {};

      for (let i = 0; i < dayInfoArray.length; i += 2) {
        const date = dayInfoArray[i];
        const info = dayInfoArray[i + 1];
        dayInfoDict[date] = info;
      }

      data.day_info = dayInfoDict;

      setUserInfo(data)
    } catch (error) {
      console.error('Error loading user info:', error)
    } finally {
      setLoading(false)
    }
  }

  const getDaysInMonth = (date) => {
    const year = date.getFullYear()
    const month = date.getMonth()
    const firstDay = new Date(year, month, 1)
    const lastDay = new Date(year, month + 1, 0)
    const daysInMonth = lastDay.getDate()
    const startingDayOfWeek = firstDay.getDay()

    const days = []
    
    // Add empty cells for days before the first day of the month
    for (let i = 0; i < startingDayOfWeek; i++) {
      days.push(null)
    }
    
    // Add all days of the month
    for (let day = 1; day <= daysInMonth; day++) {
      days.push(new Date(year, month, day))
    }
    
    return days
  }

  const getCheckInStatus = (date) => {
    if (!userInfo?.day_info || !date) return null
    
    const dateStr = date.toISOString().split('T')[0]
    return userInfo.day_info[dateStr]
  }

  const navigateMonth = (direction) => {
    const newDate = new Date(currentDate)
    newDate.setMonth(currentDate.getMonth() + direction)
    setCurrentDate(newDate)
  }

  const formatMonthYear = (date) => {
    return date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
  }

  const exportToJson = () => {
    if (!userInfo?.day_info) return

    const exportData = {
      userId,
      userName,
      exportedAt: new Date().toISOString(),
      dayInfo: userInfo.day_info
    }

    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `calendar-${userName || userId}-${new Date().toISOString().split('T')[0]}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const days = getDaysInMonth(currentDate)
  const weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

  const calendarContent = (
    <div className={isEmbedded ? "overflow-y-auto" : "p-6"}>
      {loading ? (
        <div className="text-center py-8">
          <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
          <p className="mt-2 text-gray-600">Loading calendar...</p>
        </div>
      ) : (
        <>
          {/* Month Navigation */}
          <div className="flex items-center justify-between mb-4 px-4 pt-4">
            <button
              onClick={() => navigateMonth(-1)}
              className="p-2 hover:bg-gray-100 rounded-md transition-colors"
            >
              <ChevronLeft className="h-4 w-4 text-gray-600" />
            </button>
            <h4 className={`font-medium text-gray-900 ${isEmbedded ? 'text-sm' : 'text-lg'}`}>
              {formatMonthYear(currentDate)}
            </h4>
            <button
              onClick={() => navigateMonth(1)}
              className="p-2 hover:bg-gray-100 rounded-md transition-colors"
            >
              <ChevronRight className="h-4 w-4 text-gray-600" />
            </button>
          </div>

          {/* Calendar Grid */}
          <div className="px-4">
            <div className="grid grid-cols-7 gap-1 mb-2">
              {weekDays.map(day => (
                <div key={day} className={`text-center font-medium text-gray-500 py-1 ${isEmbedded ? 'text-xs' : 'text-sm'}`}>
                  {isEmbedded ? day.slice(0, 1) : day}
                </div>
              ))}
            </div>

            <div className="grid grid-cols-7 gap-1">
              {days.map((date, index) => {
                if (!date) {
                  return <div key={index} className={isEmbedded ? "h-6" : "h-10"}></div>
                }

                const checkInStatus = getCheckInStatus(date)
                const isToday = date.toDateString() === new Date().toDateString()

                let bgColor = 'bg-gray-50'
                let textColor = 'text-gray-900'
                
                if (checkInStatus?.sober === true) {
                  bgColor = 'bg-green-100'
                  textColor = 'text-green-800'
                } else if (checkInStatus?.sober === false) {
                  bgColor = 'bg-red-100'
                  textColor = 'text-red-800'
                }

                return (
                  <div
                    key={index}
                    className={`${isEmbedded ? 'h-6' : 'h-10'} flex items-center justify-center ${isEmbedded ? 'text-xs' : 'text-sm'} rounded-md ${bgColor} ${textColor} ${
                      isToday ? 'ring-1 ring-blue-500' : ''
                    }`}
                  >
                    {date.getDate()}
                  </div>
                )
              })}
            </div>

            {/* Legend and Export */}
            <div className={`mt-4 pb-4 flex items-center justify-between ${isEmbedded ? 'text-xs' : 'text-sm'}`}>
              <div className="flex space-x-2">
                <div className="flex items-center">
                  <div className={`${isEmbedded ? 'w-3 h-3' : 'w-4 h-4'} bg-green-100 rounded mr-1`}></div>
                  <span className="text-gray-600">Sober</span>
                </div>
                <div className="flex items-center">
                  <div className={`${isEmbedded ? 'w-3 h-3' : 'w-4 h-4'} bg-red-100 rounded mr-1`}></div>
                  <span className="text-gray-600">Not Sober</span>
                </div>
                <div className="flex items-center">
                  <div className={`${isEmbedded ? 'w-3 h-3' : 'w-4 h-4'} bg-gray-50 border rounded mr-1`}></div>
                  <span className="text-gray-600">No Data</span>
                </div>
              </div>
              <button
                onClick={exportToJson}
                className="flex items-center px-2 py-1 text-gray-600 hover:bg-gray-100 rounded transition-colors"
                title="Export as JSON"
              >
                <Download className={`${isEmbedded ? 'h-3 w-3' : 'h-4 w-4'} mr-1`} />
                Export
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )

  if (isEmbedded) {
    return calendarContent
  }

  return (
    <div className="fixed inset-0 bg-gray-100 bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-900">
            {userName}'s Check-in Calendar
          </h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <X className="h-6 w-6" />
          </button>
        </div>
        {calendarContent}
      </div>
    </div>
  )
}

export default memo(UserCalendar)

