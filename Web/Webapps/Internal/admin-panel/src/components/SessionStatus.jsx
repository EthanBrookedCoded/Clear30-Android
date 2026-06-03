import { useState, useEffect } from 'react'
import { useAuth } from '../hooks/useAuth'
import { AUTO_LOGOUT_CONFIG } from '../lib/config'

export default function SessionStatus({ showStatus = false }) {
  const { user, getLoginTime } = useAuth()
  const [timeRemaining, setTimeRemaining] = useState(0)

  useEffect(() => {
    if (!user || !showStatus) return

    const interval = setInterval(() => {
      const loginTime = getLoginTime()
      const timeSinceLogin = Date.now() - loginTime
      const remaining = Math.max(0, (AUTO_LOGOUT_CONFIG.timeoutMinutes * 60 * 1000) - timeSinceLogin)
      setTimeRemaining(remaining)
    }, 1000)

    return () => clearInterval(interval)
  }, [user, showStatus, getLoginTime])

  if (!user || !showStatus) return null

  const minutes = Math.floor(timeRemaining / 60000)
  const seconds = Math.floor((timeRemaining % 60000) / 1000)

  return (
    <div className="fixed bottom-4 right-4 bg-white shadow-lg rounded-lg p-4 border border-gray-200">
      <div className="text-sm text-gray-600">
        Session expires in: {minutes}:{seconds.toString().padStart(2, '0')}
      </div>
    </div>
  )
} 