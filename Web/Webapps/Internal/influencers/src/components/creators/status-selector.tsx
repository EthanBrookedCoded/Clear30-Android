"use client";

import { useState, useEffect } from "react";
import { cn } from "@/lib/utils";
import { Loader2 } from "lucide-react";
import { createClient } from "@/lib/supabase/client";
import { updateCreatorStatus } from "@/lib/supabase/queries";

export const statusConfig: Record<string, { label: string; selectedClassName: string }> = {
  sent: {
    label: "Sent",
    selectedClassName: "bg-yellow-100 text-yellow-800 border-yellow-300",
  },
  replied: {
    label: "Replied",
    selectedClassName: "bg-emerald-100 text-emerald-800 border-emerald-300",
  },
  maybe_follow_up: {
    label: "Maybe Follow Up",
    selectedClassName: "bg-purple-100 text-purple-800 border-purple-300",
  },
  declined: {
    label: "Declined",
    selectedClassName: "bg-gray-100 text-gray-600 border-gray-300",
  },
  sent_more_info: {
    label: "Sent More Info",
    selectedClassName: "bg-teal-100 text-teal-800 border-teal-300",
  },
  call_scheduled: {
    label: "Call Scheduled",
    selectedClassName: "bg-green-700 text-white border-green-800",
  },
  contract_sent: {
    label: "Contract Sent",
    selectedClassName: "bg-orange-100 text-orange-800 border-orange-300",
  },
  guidelines_sent: {
    label: "Guidelines Sent",
    selectedClassName: "bg-amber-100 text-amber-800 border-amber-300",
  },
  references_sent: {
    label: "References Sent",
    selectedClassName: "bg-violet-100 text-violet-800 border-violet-300",
  },
  active: {
    label: "Active",
    selectedClassName: "bg-sky-100 text-sky-800 border-sky-300",
  },
  delayed: {
    label: "Delayed",
    selectedClassName: "bg-pink-100 text-pink-800 border-pink-300",
  },
  followup: {
    label: "Follow Up",
    selectedClassName: "bg-blue-100 text-blue-800 border-blue-300",
  },
  loose_ends: {
    label: "Loose Ends",
    selectedClassName: "bg-red-100 text-red-800 border-red-300",
  },
};

const unselectedClassName = "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100";

export const statusOptions = [
  { value: "sent", label: "Sent" },
  { value: "replied", label: "Replied" },
  { value: "maybe_follow_up", label: "Maybe Follow Up" },
  { value: "declined", label: "Declined" },
  { value: "sent_more_info", label: "Sent More Info" },
  { value: "call_scheduled", label: "Call Scheduled" },
  { value: "contract_sent", label: "Contract Sent" },
  { value: "guidelines_sent", label: "Guidelines Sent" },
  { value: "references_sent", label: "References Sent" },
  { value: "active", label: "Active" },
  { value: "delayed", label: "Delayed" },
  { value: "followup", label: "Follow Up" },
  { value: "loose_ends", label: "Loose Ends" },
];

interface StatusSelectorProps {
  creatorId: string;
  currentStatus: string | null;
  onStatusChanged?: (newStatus: string) => void;
  showLabel?: boolean;
}

export function StatusSelector({
  creatorId,
  currentStatus,
  onStatusChanged,
  showLabel = true,
}: StatusSelectorProps) {
  const [selectedStatus, setSelectedStatus] = useState(currentStatus);
  const [updating, setUpdating] = useState<string | null>(null);

  // Sync state when creator changes
  useEffect(() => {
    setSelectedStatus(currentStatus);
  }, [creatorId, currentStatus]);

  const handleStatusClick = async (newStatus: string) => {
    if (newStatus === selectedStatus || updating) return;

    setUpdating(newStatus);

    try {
      const supabase = createClient();
      await updateCreatorStatus(supabase, creatorId, newStatus);

      setSelectedStatus(newStatus);
      onStatusChanged?.(newStatus);
    } catch (error) {
      console.error("Error updating status:", error);
    } finally {
      setUpdating(null);
    }
  };

  return (
    <div className={showLabel ? "space-y-2" : ""}>
      {showLabel && <p className="text-sm text-gray-600">Update status:</p>}
      <div className="flex flex-wrap gap-2">
        {statusOptions.map((option) => {
          const config = statusConfig[option.value];
          const isSelected = selectedStatus === option.value;
          const isUpdating = updating === option.value;

          return (
            <button
              key={option.value}
              onClick={() => handleStatusClick(option.value)}
              disabled={updating !== null}
              className={cn(
                "inline-flex items-center gap-1.5 px-2.5 py-1 rounded text-xs font-medium border transition-colors",
                isSelected ? config.selectedClassName : unselectedClassName,
                updating !== null && !isUpdating && "opacity-50 cursor-not-allowed"
              )}
            >
              {isUpdating && <Loader2 className="h-3 w-3 animate-spin" />}
              {option.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
