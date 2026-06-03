import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

const PEER_SUPPORT_PATHS = ['/dashboard/sms', '/dashboard/in-app']

function ProtectedRoute({ children }) {
  const { user, role, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500"></div>
      </div>
    )
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (role === 'peer_support' && !PEER_SUPPORT_PATHS.includes(location.pathname)) {
    return <Navigate to="/dashboard/sms" replace />
  }

  return children
}

export default ProtectedRoute

