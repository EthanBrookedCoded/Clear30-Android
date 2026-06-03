"use client";

import { useState, useEffect } from "react";
import { cn } from "@/lib/utils";
import { Loader2 } from "lucide-react";
import { createClient } from "@/lib/supabase/client";
import { updateWouldAdd } from "@/lib/supabase/queries";

const wouldAddConfig = {
  yes: {
    label: "Yes",
    selectedClassName: "bg-green-100 text-green-700 border-green-300",
  },
  no: {
    label: "No",
    selectedClassName: "bg-red-100 text-red-700 border-red-300",
  },
};

const unselectedClassName = "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100";

interface WouldAddSelectorProps {
  creatorId: string;
  currentValue: boolean | null;
  isAdmin: boolean;
  onValueChanged?: (newValue: boolean | null) => void;
}

export function WouldAddSelector({
  creatorId,
  currentValue,
  isAdmin,
  onValueChanged,
}: WouldAddSelectorProps) {
  const [selectedValue, setSelectedValue] = useState(currentValue);
  const [updating, setUpdating] = useState<"yes" | "no" | null>(null);

  // Sync state when creator changes (e.g., arrow key navigation)
  useEffect(() => {
    setSelectedValue(currentValue);
  }, [creatorId, currentValue]);

  const handleClick = async (value: boolean) => {
    if (!isAdmin || updating) return;

    // Toggle off if clicking the already-selected value
    const newValue = value === selectedValue ? null : value;
    const key = value ? "yes" : "no";
    setUpdating(key);

    try {
      const supabase = createClient();
      await updateWouldAdd(supabase, creatorId, newValue);

      setSelectedValue(newValue);
      onValueChanged?.(newValue);
    } catch (error) {
      console.error("Error updating would_add:", error);
    } finally {
      setUpdating(null);
    }
  };

  // Read-only display for non-admins
  if (!isAdmin) {
    return (
      <div className="flex items-center gap-2">
        <span className="text-xs text-gray-500">Would add?</span>
        <span className="text-xs font-medium">
          {selectedValue === true ? "Yes" : selectedValue === false ? "No" : "—"}
        </span>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-gray-500">Would add?</span>
      <div className="flex gap-1">
        {(["yes", "no"] as const).map((key) => {
          const value = key === "yes";
          const config = wouldAddConfig[key];
          const isSelected = selectedValue === value;
          const isUpdating = updating === key;

          return (
            <button
              key={key}
              onClick={() => handleClick(value)}
              disabled={updating !== null}
              className={cn(
                "inline-flex items-center gap-1 px-2 py-1 rounded text-xs font-medium border transition-colors",
                isSelected ? config.selectedClassName : unselectedClassName,
                updating !== null && !isUpdating && "opacity-50 cursor-not-allowed"
              )}
            >
              {isUpdating && <Loader2 className="h-3 w-3 animate-spin" />}
              {config.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
