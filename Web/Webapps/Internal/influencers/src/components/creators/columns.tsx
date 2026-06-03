"use client";

import { ColumnDef } from "@tanstack/react-table";
import { ArrowUpDown, Star } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { statusConfig } from "./status-selector";

export interface Creator {
  id: string;
  instagram_handle?: string | null;
  tiktok_handle?: string | null;
  instagram_url?: string | null;
  tiktok_url?: string | null;
  full_name?: string | null;
  status: string;
  starred: boolean;
  email?: string | null;
  created_at: string;
  added_by?: { id: string; full_name: string } | null;
  instagram_outreach_account?: {
    id: string;
    handle: string;
    display_name: string;
  } | null;
  tiktok_outreach_account?: {
    id: string;
    handle: string;
    display_name: string;
  } | null;
}

function StatusBadge({ status }: { status: string }) {
  const config = statusConfig[status] || {
    label: status,
    selectedClassName: "bg-gray-100 text-gray-800 border-gray-300",
  };

  return (
    <span
      className={cn(
        "inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border",
        config.selectedClassName
      )}
    >
      {config.label}
    </span>
  );
}

function PlatformBadge({ platform, handle }: { platform: "ig" | "tt"; handle: string }) {
  return (
    <span className={cn(
      "inline-flex items-center gap-1 text-xs",
      platform === "ig" ? "text-pink-600" : "text-gray-700"
    )}>
      <span className="font-medium">{platform === "ig" ? "IG" : "TT"}</span>
      <span className="text-muted-foreground">@{handle}</span>
    </span>
  );
}

function formatDate(dateString: string, includeTime: boolean = false) {
  return new Date(dateString).toLocaleString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
    ...(includeTime && { hour: "numeric", minute: "2-digit" }),
  });
}

export const getColumns = (
  isAdmin: boolean,
  onToggleStar?: (id: string, newStarred: boolean) => void
): ColumnDef<Creator>[] => [
  {
    id: "starred",
    header: "",
    cell: ({ row }) => {
      const creator = row.original;
      return (
        <button
          title={creator.starred ? "Unstar creator" : "Star creator"}
          onClick={(e) => {
            e.stopPropagation();
            onToggleStar?.(creator.id, !creator.starred);
          }}
          className="p-1 rounded hover:bg-yellow-50 transition-colors group"
        >
          <Star
            className={cn(
              "h-4 w-4 transition-colors",
              creator.starred
                ? "fill-yellow-400 text-yellow-400"
                : "text-gray-300 group-hover:text-yellow-400"
            )}
          />
        </button>
      );
    },
    size: 40,
  },
  {
    accessorKey: "full_name",
    header: "Name",
    cell: ({ row }) => {
      const name = row.original.full_name;
      const igHandle = row.original.instagram_handle;
      const ttHandle = row.original.tiktok_handle;
      const displayName = name || igHandle || ttHandle || "Unknown";
      return <span className="font-medium inline-block">{displayName}</span>;
    },
  },
  {
    id: "handles",
    header: "Handles",
    cell: ({ row }) => {
      const ig = row.original.instagram_handle;
      const tt = row.original.tiktok_handle;
      return (
        <div className="flex flex-col gap-0.5">
          {ig && <PlatformBadge platform="ig" handle={ig} />}
          {tt && <PlatformBadge platform="tt" handle={tt} />}
        </div>
      );
    },
  },
  {
    id: "outreach_account",
    header: "Reached Out From",
    cell: ({ row }) => {
      const igAccount = row.original.instagram_outreach_account;
      const ttAccount = row.original.tiktok_outreach_account;
      if (!igAccount && !ttAccount) {
        return <span className="text-muted-foreground">-</span>;
      }
      return (
        <div className="flex flex-col gap-0.5 text-muted-foreground text-xs">
          {igAccount && (
            <span>
              IG: {igAccount.display_name ? `${igAccount.display_name} · @${igAccount.handle}` : `@${igAccount.handle}`}
            </span>
          )}
          {ttAccount && (
            <span>
              TT: {ttAccount.display_name ? `${ttAccount.display_name} · @${ttAccount.handle}` : `@${ttAccount.handle}`}
            </span>
          )}
        </div>
      );
    },
  },
  {
    accessorKey: "status",
    header: "Status",
    cell: ({ row }) => {
      const status = row.getValue("status") as string;
      return <StatusBadge status={status} />;
    },
  },
  {
    accessorKey: "email",
    header: "Email",
    cell: ({ row }) => {
      const email = row.getValue("email") as string | null;
      return (
        <span className="text-muted-foreground">
          {email || "-"}
        </span>
      );
    },
  },
  {
    accessorKey: "created_at",
    header: ({ column }) => {
      return (
        <Button
          variant="ghost"
          onClick={() =>
            column.toggleSorting(column.getIsSorted() !== "desc")
          }
          className="-ml-4"
        >
          Added
          <ArrowUpDown className="ml-2 h-4 w-4" />
        </Button>
      );
    },
    cell: ({ row }) => {
      const date = row.getValue("created_at") as string;
      return (
        <span className="text-muted-foreground">
          {formatDate(date, isAdmin)}
        </span>
      );
    },
  },
  {
    accessorKey: "added_by",
    header: "Added By",
    cell: ({ row }) => {
      const addedBy = row.original.added_by;
      return (
        <span className="text-muted-foreground">
          {addedBy?.full_name || "-"}
        </span>
      );
    },
  },
];
