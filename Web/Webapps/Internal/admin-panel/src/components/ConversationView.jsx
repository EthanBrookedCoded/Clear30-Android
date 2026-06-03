import { useState, useEffect, useRef } from 'react'
import { supabase } from '../lib/supabaseClient'
import { X, Send, Ban, CheckCircle, Calendar, FileText, ArrowLeft, Shield, ChevronRight, ChevronLeft, Tag, Plus, User } from 'lucide-react'
import UserCalendar from './UserCalendar'
import UserAssessments from './UserAssessments'
import { useAuth } from '../hooks/useAuth'
import { toast } from 'sonner'
import { useIsMobile } from '@/hooks/use-mobile'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'

function ConversationView({ conversation, onBack, onRefresh }) {
  const [messages, setMessages] = useState([])
  const [newMessage, setNewMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [isBlocked, setIsBlocked] = useState(false)
  const [isHardBlock, setIsHardBlock] = useState(false)
  const [showSidePanel, setShowSidePanel] = useState(true)
  const messagesEndRef = useRef(null)
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

  // Hide side panel by default on mobile
  useEffect(() => {
    if (window.innerWidth < 768) {
      setShowSidePanel(false)
    }
  }, [])

  // Tag-related state
  const [conversationTags, setConversationTags] = useState([])
  const [availableTags, setAvailableTags] = useState([])
  const [showTagDropdown, setShowTagDropdown] = useState(false)
  const [showCreateTag, setShowCreateTag] = useState(false)
  const [newTagName, setNewTagName] = useState('')
  const [adminId, setAdminId] = useState(null)

  const { user } = useAuth()
  const tagDropdownRef = useRef(null)
  const [copied, setCopied] = useState(false);

  // Get admin ID from user
  useEffect(() => {
    const getAdminId = async () => {
      if (user) {
        const { data, error } = await supabase
          .schema('platform')
          .rpc('get_admin_id')
          .single()

        if (data) {
          setAdminId(data)
        }
      }
    }
    getAdminId()
  }, [user])

  // Close tag dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (tagDropdownRef.current && !tagDropdownRef.current.contains(event.target)) {
        setShowTagDropdown(false)
        setShowCreateTag(false)
        setNewTagName('')
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [])

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    loadMessages()
    checkBlockedStatus()
    if (adminId) {
      loadConversationTags()
      loadAvailableTags()
    }
  }, [conversation, adminId])

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const loadMessages = async () => {
    setLoading(true)
    try {
      const { data, error } = await supabase
        .schema('comms')
        .from('sms_messages')
        .select('*')
        .eq('phone_number', conversation.phone_number)
        .eq('canceled', false)
        .lte('scheduled_for', new Date().toISOString())
        .order('sent_at', { ascending: true })

      if (error) throw error
      setMessages(data || [])
    } catch (error) {
      console.error('Error loading messages:', error)
    } finally {
      setLoading(false)
    }
  }

  const loadConversationTags = async () => {
    if (!adminId) return

    try {
      const { data, error } = await supabase
        .schema('platform')
        .from('conversation_tags')
        .select(`
          *,
          tags (id, name, color)
        `)
        .eq('phone_number', conversation.phone_number)
        .eq('admin_id', adminId)

      if (error) throw error
      setConversationTags(data || [])
    } catch (error) {
      console.error('Error loading conversation tags:', error)
    }
  }

  const loadAvailableTags = async () => {
    if (!adminId) return

    try {
      const { data, error } = await supabase
        .schema('platform')
        .from('tags')
        .select('*')
        .eq('admin_id', adminId)
        .order('name')

      if (error) throw error
      setAvailableTags(data || [])
    } catch (error) {
      console.error('Error loading available tags:', error)
    }
  }

  const generateRandomColor = () => {
    const colors = [
      '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
      '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9'
    ]
    return colors[Math.floor(Math.random() * colors.length)]
  }

  const createTag = async () => {
    if (!newTagName.trim() || !adminId) return

    try {
      const { data, error } = await supabase
        .schema('platform')
        .from('tags')
        .insert({
          admin_id: adminId,
          name: newTagName.trim(),
          color: generateRandomColor()
        })
        .select()
        .single()

      if (error) throw error

      setAvailableTags(prev => [...prev, data].sort((a, b) => a.name.localeCompare(b.name)))
      setNewTagName('')
      setShowCreateTag(false)
    } catch (error) {
      console.error('Error creating tag:', error)
      alert('Failed to create tag. Please try again.')
    }
  }

  const addTagToConversation = async (tagId) => {
    if (!adminId) return

    try {
      const { data, error } = await supabase
        .schema('platform')
        .from('conversation_tags')
        .insert({
          tag_id: tagId,
          phone_number: conversation.phone_number,
          admin_id: adminId
        })
        .select(`
          *,
          tags (id, name, color)
        `)
        .single()

      if (error) throw error

      setConversationTags(prev => [...prev, data])
      setShowTagDropdown(false)
    } catch (error) {
      console.error('Error adding tag to conversation:', error)
      alert('Failed to add tag. Please try again.')
    }
  }

  const removeTagFromConversation = async (conversationTagId) => {
    try {
      const { error } = await supabase
        .schema('platform')
        .from('conversation_tags')
        .delete()
        .eq('id', conversationTagId)

      if (error) throw error

      setConversationTags(prev => prev.filter(ct => ct.id !== conversationTagId))
    } catch (error) {
      console.error('Error removing tag from conversation:', error)
      alert('Failed to remove tag. Please try again.')
    }
  }

  const checkBlockedStatus = async () => {
    try {
      const { data, error } = await supabase
        .schema('comms')
        .from('sms_blocked')
        .select('*, hard_stop')
        .eq('phone_number', conversation.phone_number)
        .limit(1)

      if (error) throw error

      if (data && data.length > 0) {
        setIsBlocked(true)
        setIsHardBlock(data[0].hard_stop || false)
      } else {
        setIsBlocked(false)
        setIsHardBlock(false)
      }
    } catch (error) {
      console.error('Error checking blocked status:', error)
    }
  }

  const sendMessage = async () => {
    if (!newMessage.trim() || sending || isBlocked) return

    setSending(true)
    try {
      const { error } = await supabase
        .schema('comms')
        .from('sms_messages')
        .insert({
          user_id: conversation.user_id,
          phone_number: conversation.phone_number,
          text: newMessage.trim(),
          outbound: true,
          scheduled_for: new Date().toISOString()
        })

      if (error) throw error

      setNewMessage('')
      await loadMessages()
      onRefresh()
    } catch (error) {
      console.error('Error sending message:', error)
      alert('Failed to send message. Please try again.')
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

  const toggleBlockStatus = async () => {
    try {
      if (isBlocked) {
        // Cannot unblock if it's a hard block
        if (isHardBlock) {
          alert('This user has a hard block and cannot be unblocked from this interface.')
          return
        }

        // Unblock - remove from blocked table
        const { error } = await supabase
          .schema('comms')
          .from('sms_blocked')
          .delete()
          .eq('phone_number', conversation.phone_number)

        if (error) throw error
        setIsBlocked(false)
        setIsHardBlock(false)
      } else {
        // Block - add to blocked table
        const { error } = await supabase
          .schema('comms')
          .from('sms_blocked')
          .insert({
            phone_number: conversation.phone_number,
            hard_stop: false
          })

        if (error) throw error
        setIsBlocked(true)
        setIsHardBlock(false)
      }
    } catch (error) {
      console.error('Error toggling block status:', error)
      alert('Failed to update block status. Please try again.')
    }
  }

  const formatTimestamp = (timestamp) => {
    const date = new Date(timestamp)
    return date.toLocaleString()
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
      <div className={`flex flex-col min-h-0 transition-all duration-300 ${!isMobile && showSidePanel && conversation.user_id ? 'w-2/3' : 'w-full'}`}>
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
            <div className="flex items-center space-x-1 sm:space-x-2 flex-shrink-0">
              {/* Tags Dropdown */}
              <div className="relative" ref={tagDropdownRef}>
                <button
                  onClick={() => setShowTagDropdown(!showTagDropdown)}
                  className="inline-flex items-center px-2 sm:px-3 py-2 border border-gray-300 text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50"
                >
                  <Tag className="h-4 w-4" />
                  <span className="hidden sm:inline ml-2">Tags</span>
                  {conversationTags.length > 0 && (
                    <span className="ml-1 sm:ml-2 inline-flex items-center px-1.5 sm:px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                      {conversationTags.length}
                    </span>
                  )}
                </button>

                {/* Tag Dropdown */}
                {showTagDropdown && (
                  <div className="absolute right-0 mt-1 w-64 bg-white border border-gray-200 rounded-md shadow-lg z-10">
                    <div className="p-2">
                      {availableTags.length > 0 ? (
                        <>
                          <div className="max-h-48 overflow-y-auto">
                            {availableTags.map(tag => {
                              const isApplied = conversationTags.some(ct => ct.tags.id === tag.id)
                              return (
                                <button
                                  key={tag.id}
                                  onClick={() => {
                                    if (isApplied) {
                                      const conversationTag = conversationTags.find(ct => ct.tags.id === tag.id)
                                      if (conversationTag) {
                                        removeTagFromConversation(conversationTag.id)
                                      }
                                    } else {
                                      addTagToConversation(tag.id)
                                    }
                                  }}
                                  className="w-full flex items-center px-2 py-2 text-sm text-left hover:bg-gray-100 rounded"
                                >
                                  <div className="flex items-center flex-1">
                                    <span
                                      className="w-3 h-3 rounded-full mr-3"
                                      style={{ backgroundColor: tag.color }}
                                    />
                                    <span className="flex-1">{tag.name}</span>
                                    {isApplied && (
                                      <CheckCircle className="h-4 w-4 text-green-600 ml-2" />
                                    )}
                                  </div>
                                </button>
                              )
                            })}
                          </div>
                          <hr className="my-2" />
                        </>
                      ) : (
                        <p className="text-xs text-gray-500 mb-2">No tags available</p>
                      )}

                      {!showCreateTag ? (
                        <button
                          onClick={() => setShowCreateTag(true)}
                          className="w-full flex items-center px-2 py-2 text-sm text-blue-600 hover:bg-blue-50 rounded"
                        >
                          <Plus className="h-4 w-4 mr-2" />
                          Create New Tag
                        </button>
                      ) : (
                        <div className="space-y-2">
                          <input
                            type="text"
                            placeholder="Tag name"
                            value={newTagName}
                            onChange={(e) => setNewTagName(e.target.value)}
                            className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
                            onKeyPress={(e) => {
                              if (e.key === 'Enter') {
                                createTag()
                              } else if (e.key === 'Escape') {
                                setShowCreateTag(false)
                                setNewTagName('')
                              }
                            }}
                            autoFocus
                          />
                          <div className="flex space-x-1">
                            <button
                              onClick={createTag}
                              className="flex-1 px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700"
                            >
                              Create
                            </button>
                            <button
                              onClick={() => {
                                setShowCreateTag(false)
                                setNewTagName('')
                              }}
                              className="flex-1 px-2 py-1 text-xs bg-gray-300 text-gray-700 rounded hover:bg-gray-400"
                            >
                              Cancel
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>

              {/* Block/Unblock Button */}
              <button
                onClick={toggleBlockStatus}
                disabled={isBlocked && isHardBlock}
                className={`inline-flex items-center px-2 sm:px-3 py-2 border text-sm font-medium rounded-md ${isBlocked && isHardBlock
                    ? 'border-gray-300 text-gray-500 bg-gray-100 cursor-not-allowed'
                    : isBlocked
                      ? 'border-green-300 text-green-700 bg-green-50 hover:bg-green-100'
                      : 'border-red-300 text-red-700 bg-red-50 hover:bg-red-100'
                  }`}
              >
                {isBlocked ? (
                  isHardBlock ? (
                    <>
                      <Shield className="h-4 w-4" />
                      <span className="hidden sm:inline ml-2">Hard Block</span>
                    </>
                  ) : (
                    <>
                      <CheckCircle className="h-4 w-4" />
                      <span className="hidden sm:inline ml-2">Unblock</span>
                    </>
                  )
                ) : (
                  <>
                    <Ban className="h-4 w-4" />
                    <span className="hidden sm:inline ml-2">Block</span>
                  </>
                )}
              </button>

              {/* Show/Hide Details Button */}
              {conversation.user_id && (
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
              )}
            </div>
          </div>

          {isBlocked && (
            <div className={`mt-2 p-2 border rounded-md ${isHardBlock
                ? 'bg-red-100 border-red-300'
                : 'bg-red-50 border-red-200'
              }`}>
              <p className={`text-sm ${isHardBlock ? 'text-red-800' : 'text-red-700'}`}>
                {isHardBlock
                  ? 'This user has a hard block and cannot receive messages or be unblocked.'
                  : 'This user is blocked from receiving messages.'
                }
              </p>
            </div>
          )}
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
            </div>
          ) : (
            messages.map((message) => (
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
                  <p className="text-sm">{message.text}</p>
                  <p className={`text-xs mt-1 ${message.outbound ? 'text-blue-100' : 'text-gray-500'
                    }`}>
                    {formatTimestamp(message.sent_at)}
                  </p>
                </div>
              </div>
            ))
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
              placeholder={isBlocked ? "User is blocked" : "Type your message... (Enter to send, Shift+Enter for new line)"}
              disabled={isBlocked || sending}
              rows={1}
              className="flex-1 border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:bg-gray-100 disabled:cursor-not-allowed resize-none"
            />
            <button
              onClick={sendMessage}
              disabled={!newMessage.trim() || sending || isBlocked}
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
      {conversation.user_id && (
        isMobile ? (
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
        )
      )}
    </div>
  )
}

export default ConversationView

