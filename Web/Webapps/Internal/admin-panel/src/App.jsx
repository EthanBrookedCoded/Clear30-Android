import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './hooks/useAuth'
import Login from './components/Login'
import ProtectedRoute from './components/ProtectedRoute'
import DashboardLayout from './components/DashboardLayout'
import SMSPage from './components/SMSPage'
import InAppPage from './components/InAppPage'
import DrFredPage from './components/DrFredPage'
import AssessmentsPage from './components/AssessmentsPage'
import CommunityPage from './components/CommunityPage'
import FeedbackPage from './components/FeedbackPage'
import UniversityReportsPage from './components/UniversityReportsPage'
import TestimonialsPage from './components/TestimonialsPage'
import UserLookupPage from './components/UserLookupPage'
import UniversityDomainsPage from './components/UniversityDomainsPage'
import SessionStatus from './components/SessionStatus'
import { AUTO_LOGOUT_CONFIG } from './lib/config'
import './App.css'

function App() {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500"></div>
      </div>
    )
  }

  return (
    <Router basename="/panel">
      <Routes>
        <Route 
          path="/login" 
          element={user ? <Navigate to="/dashboard/sms" replace /> : <Login />} 
        />
        <Route 
          path="/dashboard" 
          element={
            <ProtectedRoute>
              <DashboardLayout />
            </ProtectedRoute>
          }
        >
          <Route path="sms" element={<SMSPage />} />
          <Route path="in-app" element={<InAppPage />} />
          <Route path="dr-fred" element={<DrFredPage />} />
          <Route path="assessments" element={<AssessmentsPage />} />
          <Route path="community" element={<CommunityPage />} />
          <Route path="feedback" element={<FeedbackPage />} />
          <Route path="university-reports" element={<UniversityReportsPage />} />
          <Route path="testimonials" element={<TestimonialsPage />} />
          <Route path="user-lookup" element={<UserLookupPage />} />
          <Route path="university-domains" element={<UniversityDomainsPage />} />
        </Route>
        <Route path="/" element={<Navigate to="/dashboard/sms" replace />} />
      </Routes>
      
      {/* Session Status Component - only shows if enabled in config */}
      <SessionStatus showStatus={AUTO_LOGOUT_CONFIG.showSessionStatus} />
    </Router>
  )
}

export default App
