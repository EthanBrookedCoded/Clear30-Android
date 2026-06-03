"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Loader2,
  ExternalLink,
  Send,
  Trash2,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  Plus,
  Pencil,
  Check,
  X,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { createClient } from "@/lib/supabase/client";
import {
  fetchCreatorDetail,
  updateCreatorEmail,
  addCreatorNote,
  deleteCreatorNote,
  deleteCreator,
  addPlatformToCreator,
  updateCreatorFullName,
} from "@/lib/supabase/queries";
import { parseCreatorUrl } from "@/lib/outreach/url-parser";
import { StatusSelector, statusConfig } from "./status-selector";

interface TimelineItem {
  type: "note" | "status_change";
  id: string;
  createdAt: string;
  authorName: string;
  content?: string;
  oldStatus?: string;
  newStatus?: string;
}

function StatusBadge({ status }: { status: string }) {
  const config = statusConfig[status] || {
    label: status,
    selectedClassName: "bg-gray-100 text-gray-800 border-gray-300",
  };

  return (
    <span
      className={cn(
        "inline-flex items-center px-2.5 py-1 rounded text-xs font-medium border",
        config.selectedClassName,
      )}
    >
      {config.label}
    </span>
  );
}

interface CreatorDetail {
  id: string;
  instagramHandle: string | null;
  tiktokHandle: string | null;
  instagramUrl: string | null;
  tiktokUrl: string | null;
  fullName: string | null;
  status: string;
  niche: string | null;
  email: string | null;
  firstName: string | null;
  phoneNumber: string | null;
  createdAt: string;
  addedByName: string;
  wouldAdd: boolean | null;
}

interface CreatorModalProps {
  creatorId: string | null;
  creatorIds: string[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onStatusChanged?: (creatorId: string, newStatus: string) => void;
  onCreatorDeleted?: (creatorId: string) => void;
  onNavigate?: (creatorId: string) => void;
}

function formatDateTime(dateString: string) {
  return new Date(dateString).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

function formatDate(dateString: string) {
  return new Date(dateString).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

// Cache for prefetched creator data
const creatorCache = new Map<
  string,
  { creator: CreatorDetail; timeline: TimelineItem[] }
>();

function getDisplayName(creator: CreatorDetail): string {
  return creator.fullName || creator.instagramHandle || creator.tiktokHandle || "Unknown";
}

function getDeleteDisplayName(creator: CreatorDetail | null): string {
  if (!creator) return "this creator";
  if (creator.instagramHandle) return `@${creator.instagramHandle}`;
  if (creator.tiktokHandle) return `@${creator.tiktokHandle}`;
  return creator.fullName || "this creator";
}

export function CreatorModal({
  creatorId,
  creatorIds,
  open,
  onOpenChange,
  onStatusChanged,
  onCreatorDeleted,
  onNavigate,
}: CreatorModalProps) {
  const [loading, setLoading] = useState(true);
  const [creator, setCreator] = useState<CreatorDetail | null>(null);
  const [timeline, setTimeline] = useState<TimelineItem[]>([]);
  const [newNote, setNewNote] = useState("");
  const [addingNote, setAddingNote] = useState(false);
  const [deletingNoteId, setDeletingNoteId] = useState<string | null>(null);
  const [noteToDelete, setNoteToDelete] = useState<string | null>(null);
  const [showDeleteCreator, setShowDeleteCreator] = useState(false);
  const [deletingCreator, setDeletingCreator] = useState(false);
  const [firstName, setFirstName] = useState("");
  const [email, setEmail] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [savingContact, setSavingContact] = useState(false);
  const [contactSaved, setContactSaved] = useState(false);

  // Full name editing state
  const [editingFullName, setEditingFullName] = useState(false);
  const [fullNameInput, setFullNameInput] = useState("");
  const [savingFullName, setSavingFullName] = useState(false);

  // Add platform state
  const [addPlatformUrl, setAddPlatformUrl] = useState("");
  const [addingPlatform, setAddingPlatform] = useState(false);
  const [addPlatformError, setAddPlatformError] = useState<string | null>(null);

  const currentIndex = creatorId ? creatorIds.indexOf(creatorId) : -1;
  const hasPrev = currentIndex > 0;
  const hasNext = currentIndex < creatorIds.length - 1;

  const goToPrev = () => {
    if (hasPrev && onNavigate) {
      onNavigate(creatorIds[currentIndex - 1]);
    }
  };

  const goToNext = () => {
    if (hasNext && onNavigate) {
      onNavigate(creatorIds[currentIndex + 1]);
    }
  };

  useEffect(() => {
    if (open && creatorId) {
      loadCreator(creatorId);
      setAddPlatformUrl("");
      setAddPlatformError(null);
      setEditingFullName(false);
      setFullNameInput("");
    }
  }, [open, creatorId]);

  // Prefetch adjacent creators when current one loads
  useEffect(() => {
    if (!open || currentIndex === -1) return;

    const prefetchIds: string[] = [];
    if (hasPrev) prefetchIds.push(creatorIds[currentIndex - 1]);
    if (hasNext) prefetchIds.push(creatorIds[currentIndex + 1]);

    prefetchIds.forEach((id) => {
      if (!creatorCache.has(id)) {
        prefetchCreator(id);
      }
    });
  }, [open, currentIndex, creatorIds, hasPrev, hasNext]);

  // Keyboard navigation
  useEffect(() => {
    if (!open) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (
        e.target instanceof HTMLTextAreaElement ||
        e.target instanceof HTMLInputElement
      ) {
        return;
      }

      if (e.key === "ArrowLeft") {
        e.preventDefault();
        goToPrev();
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        goToNext();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [open, currentIndex, creatorIds]);

  const loadCreator = async (id: string) => {
    const cached = creatorCache.get(id);
    if (cached) {
      setCreator(cached.creator);
      setTimeline(cached.timeline);
      setFirstName(cached.creator.firstName || "");
      setEmail(cached.creator.email || "");
      setPhoneNumber(cached.creator.phoneNumber || "");
      setLoading(false);
      return;
    }

    setLoading(true);
    try {
      const supabase = createClient();
      const data = await fetchCreatorDetail(supabase, id);
      setCreator(data.creator);
      setTimeline(data.timeline);
      setFirstName(data.creator.firstName || "");
      setEmail(data.creator.email || "");
      setPhoneNumber(data.creator.phoneNumber || "");
      creatorCache.set(id, {
        creator: data.creator,
        timeline: data.timeline,
      });
    } catch (error) {
      console.error("Error fetching creator:", error);
    } finally {
      setLoading(false);
    }
  };

  const prefetchCreator = async (id: string) => {
    try {
      const supabase = createClient();
      const data = await fetchCreatorDetail(supabase, id);
      creatorCache.set(id, {
        creator: data.creator,
        timeline: data.timeline,
      });
    } catch {
      // Silently fail prefetch
    }
  };

  const handleStatusChange = (newStatus: string) => {
    if (creator && creatorId) {
      const oldStatus = creator.status;
      const updatedCreator = { ...creator, status: newStatus };
      setCreator(updatedCreator);

      const newTimelineItem: TimelineItem = {
        type: "status_change",
        id: `temp-${Date.now()}`,
        createdAt: new Date().toISOString(),
        authorName: "You",
        oldStatus,
        newStatus,
      };
      const newTimeline = [newTimelineItem, ...timeline];
      setTimeline(newTimeline);

      creatorCache.set(creatorId, {
        creator: updatedCreator,
        timeline: newTimeline,
      });

      onStatusChanged?.(creatorId, newStatus);
    }
  };

  const handleSaveContact = async () => {
    if (!creator || !creatorId) return;

    setSavingContact(true);
    try {
      const supabase = createClient();
      await updateCreatorEmail(
        supabase,
        creatorId,
        email.trim() || null,
        firstName.trim() || null,
        phoneNumber.trim() || null
      );

      const updatedCreator = {
        ...creator,
        email: email.trim() || null,
        firstName: firstName.trim() || null,
        phoneNumber: phoneNumber.trim() || null,
      };
      setCreator(updatedCreator);
      creatorCache.set(creatorId, { creator: updatedCreator, timeline });
      setContactSaved(true);
      setTimeout(() => setContactSaved(false), 2000);
    } catch (error) {
      console.error("Error saving contact:", error);
    } finally {
      setSavingContact(false);
    }
  };

  const handleAddNote = async () => {
    if (!newNote.trim() || !creatorId || !creator) return;

    setAddingNote(true);
    try {
      const supabase = createClient();
      const data = await addCreatorNote(supabase, creatorId, newNote.trim());
      const newTimelineItem: TimelineItem = {
        type: "note",
        id: data.note.id,
        createdAt: data.note.createdAt,
        authorName: data.note.authorName,
        content: data.note.content,
      };
      const newTimeline = [newTimelineItem, ...timeline];
      setTimeline(newTimeline);
      setNewNote("");

      creatorCache.set(creatorId, { creator, timeline: newTimeline });
    } catch (error) {
      console.error("Error adding note:", error);
    } finally {
      setAddingNote(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleAddNote();
    }
  };

  const handleDeleteNote = async () => {
    if (!creatorId || !noteToDelete || !creator) return;

    setDeletingNoteId(noteToDelete);
    try {
      const supabase = createClient();
      await deleteCreatorNote(supabase, creatorId, noteToDelete);

      const newTimeline = timeline.filter((item) => item.id !== noteToDelete);
      setTimeline(newTimeline);

      creatorCache.set(creatorId, { creator, timeline: newTimeline });
    } catch (error) {
      console.error("Error deleting note:", error);
    } finally {
      setDeletingNoteId(null);
      setNoteToDelete(null);
    }
  };

  const handleDeleteCreator = async () => {
    if (!creatorId) return;

    setDeletingCreator(true);
    try {
      const supabase = createClient();
      await deleteCreator(supabase, creatorId);

      setShowDeleteCreator(false);
      onOpenChange(false);
      onCreatorDeleted?.(creatorId);
    } catch (error) {
      console.error("Error deleting creator:", error);
      alert(error instanceof Error ? error.message : "Failed to delete creator");
    } finally {
      setDeletingCreator(false);
    }
  };

  const handleAddPlatform = async () => {
    if (!creator || !creatorId || !addPlatformUrl.trim()) return;

    setAddingPlatform(true);
    setAddPlatformError(null);

    try {
      const parseResult = parseCreatorUrl(addPlatformUrl.trim());
      if (!parseResult.success) {
        setAddPlatformError(parseResult.error);
        return;
      }

      const { platform, handle, normalizedUrl } = parseResult.data;

      if (platform === "instagram" && creator.instagramHandle) {
        setAddPlatformError("This creator already has an Instagram handle");
        return;
      }
      if (platform === "tiktok" && creator.tiktokHandle) {
        setAddPlatformError("This creator already has a TikTok handle");
        return;
      }

      const supabase = createClient();
      await addPlatformToCreator(supabase, creatorId, platform, handle, normalizedUrl);

      const updatedCreator = { ...creator };
      if (platform === "instagram") {
        updatedCreator.instagramHandle = handle;
        updatedCreator.instagramUrl = normalizedUrl;
      } else {
        updatedCreator.tiktokHandle = handle;
        updatedCreator.tiktokUrl = normalizedUrl;
      }
      setCreator(updatedCreator);
      creatorCache.set(creatorId, { creator: updatedCreator, timeline });
      setAddPlatformUrl("");
    } catch (error) {
      setAddPlatformError(error instanceof Error ? error.message : "Failed to add platform");
    } finally {
      setAddingPlatform(false);
    }
  };

  const missingPlatform = creator
    ? !creator.instagramHandle
      ? "Instagram"
      : !creator.tiktokHandle
        ? "TikTok"
        : null
    : null;

  const handleEditFullName = () => {
    setFullNameInput(creator?.fullName || "");
    setEditingFullName(true);
  };

  const handleCancelEditFullName = () => {
    setEditingFullName(false);
    setFullNameInput("");
  };

  const handleSaveFullName = async () => {
    if (!creator || !creatorId) return;

    setSavingFullName(true);
    try {
      const supabase = createClient();
      await updateCreatorFullName(
        supabase,
        creatorId,
        fullNameInput.trim() || null
      );

      const updatedCreator = {
        ...creator,
        fullName: fullNameInput.trim() || null,
      };
      setCreator(updatedCreator);
      creatorCache.set(creatorId, { creator: updatedCreator, timeline });
      setEditingFullName(false);
    } catch (error) {
      console.error("Error saving full name:", error);
    } finally {
      setSavingFullName(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[95vw] sm:max-w-xl min-h-[500px] sm:min-h-[630px] max-h-[90vh] sm:max-h-[85vh] flex flex-col p-0">
        {loading ? (
          <>
            <DialogHeader className="p-4 sm:p-6">
              <DialogTitle>Loading...</DialogTitle>
            </DialogHeader>
            <div className="flex-1 flex items-center justify-center">
              <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            </div>
          </>
        ) : creator ? (
          <>
            <DialogHeader className="p-4 sm:p-6 pb-3">
              <DialogTitle className="flex items-center gap-2 text-base sm:text-lg">
                {editingFullName ? (
                  <div className="flex items-center gap-2 flex-1">
                    <input
                      type="text"
                      value={fullNameInput}
                      onChange={(e) => setFullNameInput(e.target.value)}
                      placeholder="Enter full name"
                      className="flex-1 px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-black"
                      autoFocus
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          handleSaveFullName();
                        } else if (e.key === "Escape") {
                          handleCancelEditFullName();
                        }
                      }}
                    />
                    <button
                      onClick={handleSaveFullName}
                      disabled={savingFullName}
                      className="p-1 hover:bg-gray-100 rounded transition-colors"
                      title="Save"
                    >
                      {savingFullName ? (
                        <Loader2 className="h-4 w-4 animate-spin text-gray-600" />
                      ) : (
                        <Check className="h-4 w-4 text-green-600" />
                      )}
                    </button>
                    <button
                      onClick={handleCancelEditFullName}
                      disabled={savingFullName}
                      className="p-1 hover:bg-gray-100 rounded transition-colors"
                      title="Cancel"
                    >
                      <X className="h-4 w-4 text-gray-600" />
                    </button>
                  </div>
                ) : (
                  <>
                    <span className="truncate">{getDisplayName(creator)}</span>
                    <button
                      onClick={handleEditFullName}
                      className="p-1 hover:bg-gray-100 rounded transition-colors shrink-0"
                      title="Edit full name"
                    >
                      <Pencil className="h-3.5 w-3.5 text-gray-500" />
                    </button>
                  </>
                )}
              </DialogTitle>
              <div className="flex flex-col gap-1 text-xs sm:text-sm text-muted-foreground">
                <div className="flex items-center gap-3 flex-wrap">
                  {creator.instagramHandle && (
                    <a
                      href={`https://www.instagram.com/${creator.instagramHandle}/reels/`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 hover:text-foreground"
                    >
                      <span className="font-medium text-pink-600">IG</span>
                      @{creator.instagramHandle}
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  )}
                  {creator.tiktokHandle && (
                    <a
                      href={creator.tiktokUrl || `https://tiktok.com/@${creator.tiktokHandle}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 hover:text-foreground"
                    >
                      <span className="font-medium">TT</span>
                      @{creator.tiktokHandle}
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  )}
                </div>
                <p>
                  Added {formatDate(creator.createdAt)} by {creator.addedByName}
                </p>
              </div>
            </DialogHeader>

            {/* Scrollable content area */}
            <div className="flex-1 overflow-y-auto px-4 sm:px-6">
              {/* Status */}
              <div className="pb-4 border-b">
                <StatusSelector
                  creatorId={creator.id}
                  currentStatus={creator.status}
                  onStatusChanged={handleStatusChange}
                  showLabel={false}
                />
              </div>

              {/* Contact Info */}
              <div className="pb-4 border-b pt-4">
                <label className="text-xs text-muted-foreground mb-1 block">
                  Contact
                </label>
                <div className="flex flex-col gap-2">
                  <div className="flex gap-2">
                    <input
                      type="text"
                      placeholder="First Name"
                      value={firstName}
                      onChange={(e) => setFirstName(e.target.value)}
                      className="w-28 px-3 py-1.5 border border-gray-200 rounded text-sm focus:outline-none focus:ring-1 focus:ring-black"
                    />
                    <input
                      type="email"
                      placeholder="Email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="flex-1 px-3 py-1.5 border border-gray-200 rounded text-sm focus:outline-none focus:ring-1 focus:ring-black"
                    />
                  </div>
                  <div className="flex gap-2">
                    <input
                      type="tel"
                      placeholder="Phone Number"
                      value={phoneNumber}
                      onChange={(e) => setPhoneNumber(e.target.value)}
                      className="flex-1 px-3 py-1.5 border border-gray-200 rounded text-sm focus:outline-none focus:ring-1 focus:ring-black"
                    />
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleSaveContact}
                      disabled={savingContact}
                      className="min-w-[60px]"
                    >
                      {savingContact ? (
                        <Loader2 className="h-3 w-3 animate-spin" />
                      ) : contactSaved ? (
                        "Saved"
                      ) : (
                        "Save"
                      )}
                    </Button>
                  </div>
                </div>
              </div>

              {/* Add Platform */}
              {missingPlatform && (
                <div className="pb-4 border-b pt-4">
                  <label className="text-xs text-muted-foreground mb-1 block">
                    Add {missingPlatform}
                  </label>
                  <div className="flex gap-2">
                    <input
                      type="text"
                      placeholder={`Paste ${missingPlatform} profile URL...`}
                      value={addPlatformUrl}
                      onChange={(e) => {
                        setAddPlatformUrl(e.target.value);
                        setAddPlatformError(null);
                      }}
                      className="flex-1 px-3 py-1.5 border border-gray-200 rounded text-sm focus:outline-none focus:ring-1 focus:ring-black"
                    />
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleAddPlatform}
                      disabled={addingPlatform || !addPlatformUrl.trim()}
                    >
                      {addingPlatform ? (
                        <Loader2 className="h-3 w-3 animate-spin" />
                      ) : (
                        <Plus className="h-3 w-3" />
                      )}
                    </Button>
                  </div>
                  {addPlatformError && (
                    <p className="text-xs text-red-600 mt-1">{addPlatformError}</p>
                  )}
                </div>
              )}

              {/* Activity Timeline */}
              <div className="pt-4">
                <h3 className="text-xs sm:text-sm font-medium mb-2">Activity</h3>

                <div className="space-y-2 sm:space-y-3">
                  {timeline.length === 0 ? (
                    <p className="text-xs sm:text-sm text-muted-foreground text-center py-4">
                      No activity yet. Add a note below.
                    </p>
                  ) : (
                    timeline.map((item) =>
                      item.type === "note" ? (
                        <div
                          key={item.id}
                          className="group bg-gray-50 rounded-lg p-2 sm:p-3 text-xs sm:text-sm relative"
                        >
                          <div className="flex items-center justify-between text-xs text-muted-foreground mb-1">
                            <span className="font-medium">{item.authorName}</span>
                            <span>{formatDateTime(item.createdAt)}</span>
                          </div>
                          <p className="text-gray-700 whitespace-pre-wrap">
                            {item.content}
                          </p>
                          <button
                            onClick={() => setNoteToDelete(item.id)}
                            disabled={deletingNoteId === item.id}
                            className="absolute bottom-2 right-2 opacity-100 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity text-muted-foreground hover:text-red-500 disabled:opacity-50"
                            title="Delete note"
                          >
                            {deletingNoteId === item.id ? (
                              <Loader2 className="h-3 w-3 animate-spin" />
                            ) : (
                              <Trash2 className="h-3 w-3" />
                            )}
                          </button>
                        </div>
                      ) : (
                        <div
                          key={item.id}
                          className="bg-white border border-gray-200 rounded-lg p-2 sm:p-3 text-xs sm:text-sm"
                        >
                          <div className="flex items-center justify-between text-xs text-muted-foreground mb-2">
                            <span className="font-medium">{item.authorName}</span>
                            <span>{formatDateTime(item.createdAt)}</span>
                          </div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <StatusBadge status={item.oldStatus || ""} />
                            <ArrowRight className="h-3 w-3 text-muted-foreground" />
                            <StatusBadge status={item.newStatus || ""} />
                          </div>
                        </div>
                      ),
                    )
                  )}
                </div>
              </div>
            </div>

            {/* Add Note Input - Fixed at bottom */}
            <div className="flex gap-2 p-4 sm:px-6 sm:pb-4 border-t">
              <Textarea
                placeholder="Add a note..."
                value={newNote}
                onChange={(e) => setNewNote(e.target.value)}
                onKeyDown={handleKeyDown}
                disabled={addingNote}
                className="min-h-[50px] sm:min-h-[60px] resize-none focus-visible:ring-0 text-sm"
              />
              <Button
                onClick={handleAddNote}
                disabled={addingNote || !newNote.trim()}
                size="icon"
                className="shrink-0"
              >
                {addingNote ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Send className="h-4 w-4" />
                )}
              </Button>
            </div>

            {/* Footer with delete and navigation */}
            <div className="flex items-center justify-between px-4 sm:px-6 pb-4 sm:pb-6 pt-3 border-t">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowDeleteCreator(true)}
                className="text-muted-foreground hover:text-red-500"
              >
                <Trash2 className="h-4 w-4 mr-2" />
                Remove
              </Button>
              <div className="flex items-center gap-1">
                <span className="text-xs text-muted-foreground mr-2">
                  {currentIndex + 1} of {creatorIds.length}
                </span>
                <Button
                  variant="outline"
                  size="icon"
                  onClick={goToPrev}
                  disabled={!hasPrev}
                  className="h-8 w-8"
                  title="Previous (Left Arrow)"
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button
                  variant="outline"
                  size="icon"
                  onClick={goToNext}
                  disabled={!hasNext}
                  className="h-8 w-8"
                  title="Next (Right Arrow)"
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </>
        ) : (
          <>
            <DialogHeader className="p-4 sm:p-6">
              <DialogTitle>Not Found</DialogTitle>
            </DialogHeader>
            <div className="text-center py-12 text-muted-foreground">
              Creator not found
            </div>
          </>
        )}
      </DialogContent>

      <AlertDialog
        open={!!noteToDelete}
        onOpenChange={(open) => !open && setNoteToDelete(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete note?</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <Button
              onClick={handleDeleteNote}
              disabled={!!deletingNoteId}
              className="bg-red-600 hover:bg-red-700"
            >
              {deletingNoteId ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                "Delete"
              )}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={showDeleteCreator} onOpenChange={setShowDeleteCreator}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove creator?</AlertDialogTitle>
            <AlertDialogDescription>
              This will remove {getDeleteDisplayName(creator)} from your outreach list. The
              creator can be re-added later if needed.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deletingCreator}>
              Cancel
            </AlertDialogCancel>
            <Button
              onClick={handleDeleteCreator}
              disabled={deletingCreator}
              className="bg-red-600 hover:bg-red-700"
            >
              {deletingCreator ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                "Remove"
              )}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Dialog>
  );
}
