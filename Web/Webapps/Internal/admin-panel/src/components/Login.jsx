import { useState } from 'react'
import { supabase } from '../lib/supabaseClient'
import { useNavigate } from 'react-router-dom'
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/components/ui/input-otp'

function Login() {
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [step, setStep] = useState('email') // 'email' | 'code'
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const handleSendCode = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')

    try {
      const { error: otpError } = await supabase.auth.signInWithOtp({ email })

      if (otpError) {
        setError('Unable to send code. Please try again.')
        return
      }

      setStep('code')
    } catch (err) {
      setError('An error occurred. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyCode = async (codeToVerify) => {
    const tokenCode = codeToVerify || code
    if (tokenCode.length !== 6) return

    setLoading(true)
    setError('')

    try {
      const { error: verifyError } = await supabase.auth.verifyOtp({
        email,
        token: tokenCode,
        type: 'email'
      })

      if (verifyError) {
        setError('Invalid or expired code. Please try again.')
        setCode('')
        return
      }

      // Check if user is an admin (get_admin_role returns null for non-admins)
      const { data: adminRole, error: checkError } = await supabase.rpc('get_admin_role')
      if (checkError || !adminRole) {
        await supabase.auth.signOut()
        setError('You are not an admin.')
        setStep('email')
        setCode('')
        return
      }

      navigate('/dashboard/sms')
    } catch (err) {
      setError('An error occurred during verification.')
      setCode('')
    } finally {
      setLoading(false)
    }
  }

  const handleCodeChange = (value) => {
    setCode(value)
    if (value.length === 6) {
      // Small delay to show the filled state before submitting
      setTimeout(() => handleVerifyCode(value), 150)
    }
  }

  const handleResendCode = async () => {
    setLoading(true)
    setError('')
    setCode('')

    try {
      const { error: otpError } = await supabase.auth.signInWithOtp({ email })

      if (otpError) {
        setError('Unable to resend code. Please try again.')
      }
    } catch (err) {
      setError('An error occurred. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleBack = () => {
    setStep('email')
    setCode('')
    setError('')
  }

  // Step 2: Enter verification code
  if (step === 'code') {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-50">
        <div className="w-full max-w-md">
          <div className="bg-white shadow-lg rounded-lg px-8 pt-6 pb-8 mb-4">
            <div className="mb-6">
              <h1 className="text-3xl font-bold text-center text-gray-900 mb-2">Enter Code</h1>
              <p className="text-center text-gray-600">
                We sent a 6-digit code to<br />
                <span className="font-medium text-gray-900">{email}</span>
              </p>
            </div>

            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded mb-4">
                {error}
              </div>
            )}

            <div className="flex justify-center mb-6">
              <InputOTP
                maxLength={6}
                value={code}
                onChange={handleCodeChange}
                disabled={loading}
              >
                <InputOTPGroup>
                  <InputOTPSlot index={0} />
                  <InputOTPSlot index={1} />
                  <InputOTPSlot index={2} />
                  <InputOTPSlot index={3} />
                  <InputOTPSlot index={4} />
                  <InputOTPSlot index={5} />
                </InputOTPGroup>
              </InputOTP>
            </div>

            <div className="text-center space-y-3">
              <button
                onClick={handleResendCode}
                disabled={loading}
                className="text-blue-500 hover:text-blue-700 text-sm disabled:opacity-50"
              >
                Resend code
              </button>
              <br />
              <button
                onClick={handleBack}
                disabled={loading}
                className="text-gray-500 hover:text-gray-700 text-sm disabled:opacity-50"
              >
                Use a different email
              </button>
            </div>
          </div>
        </div>
      </div>
    )
  }

  // Step 1: Enter email
  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="w-full max-w-md">
        <form onSubmit={handleSendCode} className="bg-white shadow-lg rounded-lg px-8 pt-6 pb-8 mb-4">
          <div className="mb-6">
            <h1 className="text-3xl font-bold text-center text-gray-900 mb-2">Clear30 Admin Panel</h1>
            <p className="text-center text-gray-600">Sign in to access the admin dashboard</p>
          </div>

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded mb-4">
              {error}
            </div>
          )}

          <div className="mb-6">
            <label className="block text-gray-700 text-sm font-bold mb-2" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              type="email"
              className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
              placeholder="Enter your email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div className="flex items-center justify-between">
            <button
              type="submit"
              disabled={loading}
              className="bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded focus:outline-none focus:shadow-outline w-full disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Sending code...' : 'Send Code'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default Login

