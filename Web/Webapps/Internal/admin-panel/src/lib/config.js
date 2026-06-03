// Auto logout configuration
export const AUTO_LOGOUT_CONFIG = {
  // Timeout in minutes
  timeoutMinutes: 25,
  
  // Show session status indicator (useful for development/testing)
  showSessionStatus: false, // Set to true to see session countdown
  
  // Events that count as user activity
  activityEvents: [
    'mousedown',
    'mousemove',
    'keypress',
    'scroll',
    'touchstart',
    'click'
  ],
  
  // Show warning before logout (in minutes)
  warningBeforeLogout: 2 // Show warning 2 minutes before logout
}

// QA Mode - Set to true to use QA environment, false for production
export const QA_MODE = false

// Supabase configuration
export const SUPABASE_CONFIG = {
  // Production environment
  production: {
    url: 'https://quluipmdicjsolnsopkg.supabase.co',
    anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1bHVpcG1kaWNqc29sbnNvcGtnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjQ2OTU4MzcsImV4cCI6MjA0MDI3MTgzN30.aXy2DJAU7pmR-yiniP57d0moGd-REDMlnWi8D4DkQgU'
  },
  // QA environment
  qa: {
    url: 'http://127.0.0.1:54321',
    anonKey: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0'
  }
} 
