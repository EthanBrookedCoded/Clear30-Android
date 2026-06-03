import { useState } from 'react'
import { supabase } from '../lib/supabaseClient'
import { X, Send } from 'lucide-react'

function NewMessageModal({ onClose, onSent }) {
  const [userId, setUserId] = useState('')
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')

  const sendMessage = async () => {
    if (!userId.trim() || !message.trim()) {
      setError('Please enter both user ID and message')
      return
    }

    setSending(true)
    setError('')

    try {
      // First, get the user's phone number
      const { data: userData, error: userError } = await supabase
        .from('users')
        .select('phone_number')
        .eq('id', userId.trim())
        .single()

      if (userError || !userData) {
        setError('User not found')
        return
      }

      if (!userData.phone_number) {
        setError('User does not have a phone number')
        return
      }

      // Check if the phone number is blocked
      const { data: blockedData, error: blockedError } = await supabase
        .schema('comms')
        .from('sms_blocked')
        .select('*')
        .eq('phone_number', userData.phone_number)
        .limit(1)

      if (blockedError) throw blockedError

      if (blockedData && blockedData.length > 0) {
        setError('Cannot send message: phone number is blocked')
        return
      }

      // Send the message
      const { error: messageError } = await supabase
        .schema('comms')
        .from('sms_messages')
        .insert({
          user_id: userId.trim(),
          phone_number: userData.phone_number,
          text: message.trim(),
          outbound: true,
          scheduled_for: new Date().toISOString()
        })

      if (messageError) throw messageError

      onSent()
    } catch (error) {
      console.error('Error sending message:', error)
      setError('Failed to send message. Please try again.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-gray-100 bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h3 className="text-lg font-semibold text-gray-900">Send New Message</h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <X className="h-6 w-6" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">
              {error}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              User ID
            </label>
            <input
              type="text"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="Enter user ID"
              className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Message
            </label>
            <textarea
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="Enter your message"
              rows={4}
              className="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>
        </div>

        <div className="flex justify-end space-x-3 p-6 border-t border-gray-200">
          <button
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            onClick={sendMessage}
            disabled={sending || !userId.trim() || !message.trim()}
            className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {sending ? (
              <>
                <div className="animate-spin rounded-full h-4 w-4 border-t-2 border-b-2 border-white mr-2"></div>
                Sending...
              </>
            ) : (
              <>
                <Send className="h-4 w-4 mr-2" />
                Send Message
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  )
}

export default NewMessageModal

