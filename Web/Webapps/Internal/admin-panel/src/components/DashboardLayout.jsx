import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useState, useEffect } from 'react'
import { useAuth } from '../hooks/useAuth'
import { AUTO_LOGOUT_CONFIG, QA_MODE } from '../lib/config'
import { MessageSquare, UserCheck, FileText, Users, LogOut, Clock, MessageCircle, GraduationCap, AlertTriangle, Video, Search, Smartphone, Globe } from 'lucide-react'
import {
  SidebarProvider,
  Sidebar,
  SidebarContent,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarTrigger,
  SidebarInset,
  useSidebar,
} from '@/components/ui/sidebar'

function DashboardLayout() {
  return (
    <SidebarProvider className="h-screen overflow-hidden">
      <DashboardContent />
    </SidebarProvider>
  )
}

function DashboardContent() {
  const { user, role, signOut, getLoginTime } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [timeRemaining, setTimeRemaining] = useState(0)
  const { setOpenMobile } = useSidebar()

  const handleSignOut = async () => {
    await signOut()
    navigate('/login')
  }

  // Update session timer every second
  useEffect(() => {
    if (!user) return

    const interval = setInterval(() => {
      const loginTime = getLoginTime()
      const timeSinceLogin = Date.now() - loginTime
      const remaining = Math.max(0, (AUTO_LOGOUT_CONFIG.timeoutMinutes * 60 * 1000) - timeSinceLogin)
      setTimeRemaining(remaining)
    }, 1000)

    return () => clearInterval(interval)
  }, [user, getLoginTime])

  const allNavItems = [
    { path: '/dashboard/sms', label: 'SMS', icon: MessageSquare },
    { path: '/dashboard/in-app', label: 'In-App Messages', icon: Smartphone },
    { path: '/dashboard/dr-fred', label: 'Dr Fred', icon: UserCheck, roles: ['admin'] },
    { path: '/dashboard/assessments', label: 'Assessments', icon: FileText, roles: ['admin'] },
    { path: '/dashboard/community', label: 'Community', icon: Users, roles: ['admin'] },
    { path: '/dashboard/feedback', label: 'Feedback', icon: MessageCircle, roles: ['admin'] },
    { path: '/dashboard/university-reports', label: 'University Reports', icon: GraduationCap, roles: ['admin'] },
    { path: '/dashboard/testimonials', label: 'Testimonials', icon: Video, roles: ['admin'] },
    { path: '/dashboard/user-lookup', label: 'User Lookup', icon: Search, roles: ['admin'] },
    { path: '/dashboard/university-domains', label: 'University Domains', icon: Globe, roles: ['admin'] },
  ]

  const navItems = allNavItems.filter(item => !item.roles || item.roles.includes(role))

  // Format time remaining
  const minutes = Math.floor(timeRemaining / 60000)
  const seconds = Math.floor((timeRemaining % 60000) / 1000)
  const timeDisplay = `${minutes}:${seconds.toString().padStart(2, '0')}`

  const handleNavClick = (path) => {
    navigate(path)
    setOpenMobile(false) // Close mobile sidebar after navigation
  }

  return (
    <>
      {/* Sidebar */}
      <Sidebar>
        <SidebarContent className="pt-4">
          <SidebarMenu>
            {navItems.map((item) => {
              const Icon = item.icon
              const isActive = location.pathname === item.path
              return (
                <SidebarMenuItem key={item.path}>
                  <SidebarMenuButton
                    isActive={isActive}
                    onClick={() => handleNavClick(item.path)}
                    className={isActive ? 'bg-blue-100 text-blue-700' : ''}
                  >
                    <Icon className="h-5 w-5" />
                    <span>{item.label}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              )
            })}
          </SidebarMenu>
        </SidebarContent>
      </Sidebar>

      {/* Main Content Area */}
      <SidebarInset className="flex flex-col h-full overflow-hidden">
        {/* Header */}
        <header className="bg-white shadow-sm border-b flex-shrink-0">
          <div className="px-3 sm:px-4 lg:px-6">
            <div className="flex justify-between items-center h-14 sm:h-16">
              <div className="flex items-center gap-2">
                <SidebarTrigger className="md:hidden" />
                <h1 className="text-base sm:text-lg md:text-xl font-semibold text-gray-900">
                  <span className="hidden sm:inline">Clear30 Admin Panel</span>
                  <span className="sm:hidden">Admin</span>
                </h1>
              </div>
              <div className="flex items-center space-x-2 sm:space-x-4">
                {/* QA Mode Indicator */}
                {QA_MODE && (
                  <div className="flex items-center px-2 sm:px-3 py-1 bg-orange-100 text-orange-700 rounded-md text-xs sm:text-sm font-medium">
                    <AlertTriangle className="h-4 w-4" />
                    <span className="hidden sm:inline ml-1">QA Mode</span>
                  </div>
                )}

                {/* Session Timer */}
                <div className="flex items-center text-xs sm:text-sm text-gray-500">
                  <Clock className="h-4 w-4 mr-1" />
                  <span className="hidden sm:inline">Session expires: </span>
                  <span>{timeDisplay}</span>
                </div>

                <button
                  onClick={handleSignOut}
                  className="inline-flex items-center px-2 sm:px-3 py-2 border border-transparent text-sm leading-4 font-medium rounded-md text-gray-500 hover:text-gray-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
                >
                  <LogOut className="h-4 w-4" />
                  <span className="hidden sm:inline ml-1">Sign Out</span>
                </button>
              </div>
            </div>
          </div>
        </header>

        {/* Main Content */}
        <div className="flex-1 min-h-0 p-3 sm:p-4 md:p-6 overflow-hidden">
          <div className="h-full overflow-hidden">
            <Outlet />
          </div>
        </div>
      </SidebarInset>
    </>
  )
}

export default DashboardLayout

