import { useState, useEffect, useRef } from 'react'
import { supabase } from '../lib/supabaseClient'
import { useAutoLogout } from './useAutoLogout'
import { AUTO_LOGOUT_CONFIG } from '../lib/config'

export function useAuth() {
  const [user, setUser] = useState(undefined)
  const [role, setRole] = useState(null)
  const [loading, setLoading] = useState(true)
  const mountedRef = useRef(true)

  const autoLogout = useAutoLogout(AUTO_LOGOUT_CONFIG.timeoutMinutes, !!user)

  useEffect(() => {
    mountedRef.current = true

    supabase.auth.getSession().then(({ data: { session } }) => {
      if (!mountedRef.current) return
      setUser(session?.user ?? null)
      if (!session?.user) setLoading(false)
    }).catch(() => {
      if (!mountedRef.current) return
      setUser(null)
      setLoading(false)
    })

    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      (_event, session) => {
        if (!mountedRef.current) return
        setUser(session?.user ?? null)
        if (!session?.user) {
          setRole(null)
          setLoading(false)
        }
      }
    )

    return () => {
      mountedRef.current = false
      subscription.unsubscribe()
    }
  }, [])

  useEffect(() => {
    if (!user) return

    let cancelled = false
    setLoading(true)
    supabase.rpc('get_admin_role')
      .then(({ data, error }) => {
        if (cancelled) return
        setRole(error ? null : data)
      })
      .catch(() => {
        if (cancelled) return
        setRole(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => { cancelled = true }
  }, [user?.id])

  const signOut = async () => {
    await supabase.auth.signOut()
  }

  return {
    user,
    role,
    loading,
    signOut,
    getLoginTime: autoLogout.getLoginTime
  }
}

