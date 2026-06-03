import { useState, useEffect, useRef } from 'react'
import { supabase } from '../lib/supabaseClient'
import { Send, ArrowLeft, Calendar, FileText, ChevronRight, ChevronLeft, User } from 'lucide-react'
import UserCalendar from './UserCalendar'
import UserAssessments from './UserAssessments'
import { toast } from 'sonner'
import { useIsMobile } from '@/hooks/use-mobile'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'

function InAppConversationView({ conversation, onBack, onRefresh }) {
  const [messages, setMessages] = useState([])
  const [newMessage, setNewMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [showSidePanel, setShowSidePanel] = useState(true)
  const messagesEndRef = useRef(null)
  const [copied, setCopied] = useState(false)
  const isMobile = useIsMobile()

  // Handle back navigation with scroll reset for iOS Safari
  const handleBack = () => {
    window.scrollTo(0, 0)
    document.documentElement.scrollTop = 0
    document.body.scrollTop = 0
    const scrollableParent = document.querySelector('[data-slot="sidebar-inset"]')
    if (scrollableParent) scrollableParent.scrollTop = 0
    onBack()
  }

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  // Hide side panel by default on mobile
  useEffect(() => {
    if (window.innerWidth < 768) {
      setShowSidePanel(false)
    }
  }, [])

  useEffect(() => {
    loadMessages()
    markMessagesAsRead()
  }, [conversation])

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  // Subscribe to realtime updates for this conversation
  useEffect(() => {
    const channel = supabase
      .channel(`peer_messages_${conversation.user_id}`)
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'comms',
          table: 'peer_messages',
          filter: `user_id=eq.${conversation.user_id}`
        },
        (payload) => {
          // Add new message to the list
          const newMsg = payload.new
          setMessages(prev => {
            // Check if message already exists
            if (prev.some(m => m.id === newMsg.id)) {
              return prev
            }
            return [...prev, newMsg]
          })

          // Mark as read if it's an inbound message
          if (!newMsg.outbound) {
            markMessagesAsRead()
          }
        }
      )
      .subscribe()

    return () => {
      supabase.removeChannel(channel)
    }
  }, [conversation.user_id])

  const loadMessages = async () => {
    setLoading(true)
    try {
      const { data, error } = await supabase
        .schema('comms')
        .from('peer_messages')
        .select('*')
        .eq('user_id', conversation.user_id)
        .eq('deleted', false)
        .order('created_at', { ascending: true })

      if (error) throw error
      setMessages(data || [])
    } catch (error) {
      console.error('Error loading messages:', error)
    } finally {
      setLoading(false)
    }
  }

  const markMessagesAsRead = async () => {
    try {
      // Call the database function to mark messages as read
      const { error } = await supabase
        .schema('comms')
        .rpc('mark_peer_messages_read', { p_user_id: conversation.user_id })

      if (error) {
        console.error('Error marking messages as read:', error)
      }
    } catch (error) {
      console.error('Error marking messages as read:', error)
    }
  }

  const sendMessage = async () => {
    if (!newMessage.trim() || sending) return

    setSending(true)
    try {
      const { data, error } = await supabase
        .schema('comms')
        .from('peer_messages')
        .insert({
          user_id: conversation.user_id,
          text: newMessage.trim(),
          outbound: true,
          type: 'manual'
        })
        .select()
        .single()

      if (error) throw error

      // Add message to local state immediately
      if (data) {
        setMessages(prev => [...prev, data])
      }

      setNewMessage('')
      onRefresh()
    } catch (error) {
      console.error('Error sending message:', error)
      toast.error('Failed to send message. Please try again.')
    } finally {
      setSending(false)
    }
  }

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const formatTimestamp = (timestamp) => {
    const date = new Date(timestamp)
    return date.toLocaleString()
  }

  const getMessageTypeLabel = (type) => {
    switch (type) {
      case 'milestone':
        return 'Milestone'
      case 'migrated':
        return 'SMS'
      case 'system':
        return 'System'
      case 'manual':
      default:
        return null
    }
  }

  // Side panel content (shared between desktop inline and mobile sheet)
  const sidePanelContent = (
    <>
      {/* Scrollable Content Area */}
      <div className="flex-1 min-h-0 overflow-y-auto">
        {/* Calendar Section */}
        <div className="border-b border-gray-200">
          <div className="bg-white border-b border-gray-200 px-4 py-2 sticky top-0 z-10">
            <div className="flex items-center">
              <Calendar className="h-4 w-4 text-gray-600 mr-2" />
              <h4 className="text-sm font-medium text-gray-900">Calendar</h4>
            </div>
          </div>
          <UserCalendar
            userId={conversation.user_id}
            userName={conversation.name}
            isEmbedded={true}
          />
        </div>

        {/* Assessments Section */}
        <div>
          <div className="bg-white border-b border-gray-200 px-4 py-2 sticky top-0 z-10">
            <div className="flex items-center">
              <FileText className="h-4 w-4 text-gray-600 mr-2" />
              <h4 className="text-sm font-medium text-gray-900">Assessments</h4>
            </div>
          </div>
          <UserAssessments
            userId={conversation.user_id}
            userName={conversation.name}
            isEmbedded={true}
          />
        </div>
      </div>

      {/* Action Buttons */}
      <div className="p-4 border-t border-gray-200 bg-gray-50 space-y-2">
        <button
          onClick={() => {
            setNewMessage("By the way, if this is really helping you, i can send you a link to record a testimonial and we'll pay you $20 for it !");
            toast.success('Testimonial text filled!', { duration: 2000 });
            if (isMobile) setShowSidePanel(false);
          }}
          className="w-full inline-flex items-center justify-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-900 bg-white hover:bg-gray-100 focus:outline-none focus:ring-0 focus:ring-offset-0 transition-colors"
        >
          Fill Testimonial Text
        </button>
        <button
          onClick={async () => {
            const testimonialLink = "Here's the testimonial link: clear30://testimonial";
            try {
              await navigator.clipboard.writeText(testimonialLink);
              toast.success('Testimonial link copied to clipboard!', { duration: 2000 });
            } catch (err) {
              toast.error('Failed to copy link', { duration: 2000 });
            }
          }}
          className="w-full inline-flex items-center justify-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-900 bg-white hover:bg-gray-100 focus:outline-none focus:ring-0 focus:ring-offset-0 transition-colors"
        >
          Copy Testimonial Link
        </button>
        <button
          onClick={async () => {
            const link = `https://clear30.org/redir/?des=survey&user_id=${conversation.user_id}&type=free_sub_form_trial_ju`;
            try {
              await navigator.clipboard.writeText(link);
              toast.success('Link copied to clipboard!', { duration: 2000 });
              setCopied(true);
              setTimeout(() => setCopied(false), 2000);
            } catch (err) {
              toast.error('Failed to copy link', { duration: 2000 });
            }
          }}
          className="w-full inline-flex items-center justify-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-900 bg-white hover:bg-gray-100 focus:outline-none focus:ring-0 focus:ring-offset-0 transition-colors"
        >
          {copied ? (
            <>
              <svg className="h-5 w-5 text-green-500 mr-2" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" /></svg>
              Copied!
            </>
          ) : (
            'Copy Free Code Link'
          )}
        </button>
      </div>
    </>
  )

  return (
    <div className="h-full flex overflow-hidden">
      {/* Main Conversation Panel - full width on mobile */}
      <div className={`flex flex-col min-h-0 transition-all duration-300 ${!isMobile && showSidePanel ? 'w-2/3' : 'w-full'}`}>
        {/* Header */}
        <div className="bg-white border-b border-gray-200 px-3 sm:px-6 py-3 sm:py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2 sm:space-x-4 min-w-0 flex-1">
              <button
                onClick={handleBack}
                className="p-2 hover:bg-gray-100 rounded-md transition-colors flex-shrink-0"
              >
                <ArrowLeft className="h-5 w-5 text-gray-600" />
              </button>
              <div className="min-w-0">
                <h2 className="text-base sm:text-lg font-semibold text-gray-900 truncate">{conversation.name}</h2>
                <p className="text-xs sm:text-sm text-gray-600 truncate">{conversation.user_id}</p>
              </div>
            </div>
            <div className="flex items-center space-x-2 flex-shrink-0">
              {/* Show/Hide Details Button */}
              <button
                onClick={() => setShowSidePanel(!showSidePanel)}
                className={`inline-flex items-center px-2 sm:px-3 py-2 border text-sm font-medium rounded-md transition-colors ${showSidePanel
                    ? 'border-blue-300 text-blue-700 bg-blue-50 hover:bg-blue-100'
                    : 'border-gray-300 text-gray-700 bg-white hover:bg-gray-50'
                  }`}
              >
                {showSidePanel ? (
                  <>
                    <User className="h-4 w-4 sm:hidden" />
                    <ChevronRight className="h-4 w-4 hidden sm:block" />
                    <span className="hidden sm:inline ml-2">Hide Details</span>
                  </>
                ) : (
                  <>
                    <User className="h-4 w-4 sm:hidden" />
                    <ChevronLeft className="h-4 w-4 hidden sm:block" />
                    <span className="hidden sm:inline ml-2">Show Details</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {loading ? (
            <div className="text-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500 mx-auto"></div>
              <p className="mt-2 text-gray-600">Loading messages...</p>
            </div>
          ) : messages.length === 0 ? (
            <div className="text-center py-8">
              <p className="text-gray-600">No messages in this conversation</p>
              <p className="text-sm text-gray-500 mt-2">Send a message to start the conversation</p>
            </div>
          ) : (
            messages.map((message) => {
              const typeLabel = getMessageTypeLabel(message.type)
              return (
                <div
                  key={message.id}
                  className={`flex ${message.outbound ? 'justify-end' : 'justify-start'}`}
                >
                  <div
                    className={`max-w-xs lg:max-w-md px-4 py-2 rounded-lg ${message.outbound
                        ? 'bg-blue-500 text-white'
                        : 'bg-gray-200 text-gray-900'
                      }`}
                  >
                    {typeLabel && (
                      <p className={`text-xs mb-1 ${message.outbound ? 'text-blue-200' : 'text-gray-400'}`}>
                        {typeLabel}
                      </p>
                    )}
                    <p className="text-sm whitespace-pre-wrap">{message.text}</p>
                    <p className={`text-xs mt-1 ${message.outbound ? 'text-blue-100' : 'text-gray-500'
                      }`}>
                      {formatTimestamp(message.created_at)}
                    </p>
                  </div>
                </div>
              )
            })
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Message Input */}
        <div className="bg-white border-t border-gray-200 p-4">
          <div className="flex space-x-4">
            <textarea
              value={newMessage}
              onChange={(e) => setNewMessage(e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder="Type your message... (Enter to send, Shift+Enter for new line)"
              disabled={sending}
              rows={1}
              className="flex-1 border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:bg-gray-100 disabled:cursor-not-allowed resize-none"
            />
            <button
              onClick={sendMessage}
              disabled={!newMessage.trim() || sending}
              className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {sending ? (
                <div className="animate-spin rounded-full h-4 w-4 border-t-2 border-b-2 border-white"></div>
              ) : (
                <Send className="h-4 w-4" />
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Side Panel - Sheet on mobile, inline on desktop */}
      {isMobile ? (
        <Sheet open={showSidePanel} onOpenChange={setShowSidePanel}>
          <SheetContent side="right" className="w-[85vw] sm:w-[400px] p-0 flex flex-col">
            <SheetHeader className="px-4 py-3 border-b flex-shrink-0">
              <SheetTitle>User Details</SheetTitle>
            </SheetHeader>
            {sidePanelContent}
          </SheetContent>
        </Sheet>
      ) : (
        showSidePanel && (
          <div className="w-1/3 border-l border-gray-200 bg-gray-50 flex flex-col">
            {/* Side Panel Header */}
            <div className="bg-white border-b border-gray-200 px-4 py-3">
              <div className="flex items-center justify-between">
                <h3 className="text-lg font-medium text-gray-900">User Details</h3>
              </div>
            </div>
            {sidePanelContent}
          </div>
        )
      )}
    </div>
  )
}

export default InAppConversationView
