"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Loader2, Send, Check } from "lucide-react";
import { cn } from "@/lib/utils";
import { createClient } from "@/lib/supabase/client";
import { addCreatorNote } from "@/lib/supabase/queries";

interface QuickNoteInputProps {
  creatorId: string;
  compact?: boolean;
}

export function QuickNoteInput({ creatorId, compact = false }: QuickNoteInputProps) {
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const handleSave = async () => {
    if (!note.trim() || saving) return;

    setSaving(true);
    try {
      const supabase = createClient();
      await addCreatorNote(supabase, creatorId, note.trim());

      setNote("");
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch (error) {
      console.error("Error adding note:", error);
    } finally {
      setSaving(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSave();
    }
  };

  return (
    <div className="space-y-2">
      <p className="text-xs text-gray-500">Add note</p>
      <div className="flex gap-2">
        <Textarea
          placeholder="Write a quick note..."
          value={note}
          onChange={(e) => setNote(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={saving}
          className={cn(
            "resize-none focus-visible:ring-0 text-sm",
            compact ? "min-h-[50px]" : "min-h-[70px]"
          )}
        />
        <Button
          onClick={handleSave}
          disabled={saving || !note.trim()}
          size="icon"
          className="shrink-0"
          variant="outline"
        >
          {saving ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : saved ? (
            <Check className="h-4 w-4" />
          ) : (
            <Send className="h-4 w-4" />
          )}
        </Button>
      </div>
    </div>
  );
}
