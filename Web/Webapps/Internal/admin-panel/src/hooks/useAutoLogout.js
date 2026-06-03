import { useEffect, useRef, useCallback } from 'react'
import { supabase } from '../lib/supabaseClient'
import { AUTO_LOGOUT_CONFIG } from '../lib/config'

export function useAutoLogout(timeoutMinutes = AUTO_LOGOUT_CONFIG.timeoutMinutes, isActive = true) {
  const timeoutRef = useRef(null)
  const loginTimeRef = useRef(Date.now())

  const logout = useCallback(async () => {
    await supabase.auth.signOut()
    console.log('Session expired - logged out after 15 minutes')
  }, [])

  useEffect(() => {
    if (!isActive) {
      // Clear timeout if not active
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
        timeoutRef.current = null
      }
      return
    }

    // Set login time when user becomes active (logged in)
    loginTimeRef.current = Date.now()

    // Set fixed timeout for 15 minutes from login
    timeoutRef.current = setTimeout(() => {
      logout()
    }, timeoutMinutes * 60 * 1000) // Convert minutes to milliseconds

    // Cleanup function
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
      }
    }
  }, [logout, timeoutMinutes, isActive])

  // Return function to get login time for display purposes
  return {
    getLoginTime: () => loginTimeRef.current
  }
} 