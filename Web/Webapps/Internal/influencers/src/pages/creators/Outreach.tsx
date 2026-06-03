import { useState, useEffect, useMemo, useRef, useCallback } from "react";
import { format } from "date-fns";
import { getDayKey } from "@/lib/timezone-utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Calendar } from "@/components/ui/calendar";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Loader2, Search, CalendarIcon, Star } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CreatorsTable } from "@/components/creators/creators-table";
import { getColumns, Creator } from "@/components/creators/columns";
import { CreatorsChart } from "@/components/creators/creators-chart";
import { statusOptions, statusConfig } from "@/components/creators/status-selector";
import { CreatorModal } from "@/components/creators/creator-modal";
import { cn } from "@/lib/utils";
import { createClient } from "@/lib/supabase/client";
import { fetchCreators as fetchCreatorsQuery, fetchVAs, toggleStarred } from "@/lib/supabase/queries";
import { useAuth } from "@/lib/auth-provider";

interface Profile {
  id: string;
  full_name: string;
}

function getThisWeekRange() {
  const now = new Date();
  const dayOfWeek = now.getDay();
  const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;

  const monday = new Date(now);
  monday.setDate(now.getDate() + mondayOffset);
  monday.setHours(0, 0, 0, 0);

  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  sunday.setHours(23, 59, 59, 999);

  return { from: monday, to: sunday };
}

export default function OutreachPage() {
  const supabase = createClient();
  const { role, myAccountsOnly, userAccountIds } = useAuth();
  const isAdmin = role === "admin";

  const [loading, setLoading] = useState(true);
  const [creators, setCreators] = useState<Creator[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [selectedStatuses, setSelectedStatuses] = useState<string[]>([]);
  const [addedByFilter, setAddedByFilter] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [dateRange, setDateRange] = useState<{ from: Date; to: Date }>(() => {
    const start = new Date(2020, 0, 1);
    start.setHours(0, 0, 0, 0);
    const end = new Date();
    end.setHours(23, 59, 59, 999);
    return { from: start, to: end };
  });
  const [activeTab, setActiveTab] = useState("main");
  const [selectedCreatorId, setSelectedCreatorId] = useState<string | null>(null);
  const [creatorModalOpen, setCreatorModalOpen] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key === "f") {
      e.preventDefault();
      searchInputRef.current?.focus();
      searchInputRef.current?.select();
    }
  }, []);

  useEffect(() => {
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [handleKeyDown]);

  useEffect(() => {
    loadCreators();
  }, [addedByFilter]);

  useEffect(() => {
    if (isAdmin) {
      loadProfiles();
    }
  }, [isAdmin]);

  const loadCreators = async () => {
    setLoading(true);
    try {
      const addedById = addedByFilter !== "all" ? addedByFilter : null;
      const data = await fetchCreatorsQuery(supabase, addedById);
      setCreators(data.creators);
    } catch (error) {
      console.error("Error fetching creators:", error);
    } finally {
      setLoading(false);
    }
  };

  const loadProfiles = async () => {
    try {
      const data = await fetchVAs(supabase);
      setProfiles(data.vas || []);
    } catch (error) {
      // Non-admins will get an error, which is expected
    }
  };

  const toggleStatus = (status: string) => {
    setSelectedStatuses((prev) =>
      prev.includes(status)
        ? prev.filter((s) => s !== status)
        : [...prev, status],
    );
  };

  const handleRowClick = (creator: Creator) => {
    setSelectedCreatorId(creator.id);
    setCreatorModalOpen(true);
  };

  const handleStatusChanged = (creatorId: string, newStatus: string) => {
    setCreators((prev) =>
      prev.map((c) => (c.id === creatorId ? { ...c, status: newStatus } : c)),
    );
  };

  const handleCreatorDeleted = (creatorId: string) => {
    setCreators((prev) => prev.filter((c) => c.id !== creatorId));
  };

  const handleToggleStar = useCallback(async (id: string, newStarred: boolean) => {
    // Optimistic update — flip immediately in local state
    setCreators((prev) =>
      prev.map((c) => (c.id === id ? { ...c, starred: newStarred } : c))
    );
    try {
      await toggleStarred(supabase, id, newStarred);
    } catch (err) {
      // Revert on failure
      console.error("Failed to toggle star:", err);
      setCreators((prev) =>
        prev.map((c) => (c.id === id ? { ...c, starred: !newStarred } : c))
      );
    }
  }, [supabase]);

  const handleDatePreset = (days: number) => {
    const end = new Date();
    end.setHours(23, 59, 59, 999);
    const start = new Date();
    start.setDate(start.getDate() - days);
    start.setHours(0, 0, 0, 0);
    setDateRange({ from: start, to: end });
  };

  const isPresetActive = (days: number) => {
    const expectedStart = new Date();
    expectedStart.setDate(expectedStart.getDate() - days);
    expectedStart.setHours(0, 0, 0, 0);
    const expectedEnd = new Date();
    expectedEnd.setHours(23, 59, 59, 999);
    return (
      dateRange.from.toDateString() === expectedStart.toDateString() &&
      dateRange.to.toDateString() === expectedEnd.toDateString()
    );
  };

  const isThisWeekActive = () => {
    const thisWeek = getThisWeekRange();
    return (
      dateRange.from.toDateString() === thisWeek.from.toDateString() &&
      dateRange.to.toDateString() === thisWeek.to.toDateString()
    );
  };

  const isAllTimeActive = () => {
    return dateRange.from.getFullYear() <= 2020;
  };

  const handleAllTime = () => {
    const start = new Date(2020, 0, 1);
    start.setHours(0, 0, 0, 0);
    const end = new Date();
    end.setHours(23, 59, 59, 999);
    setDateRange({ from: start, to: end });
  };

  const columns = useMemo(() => getColumns(isAdmin, handleToggleStar), [isAdmin, handleToggleStar]);

  const stats = useMemo(() => {
    const filtered = creators.filter((c) => {
      const createdAt = new Date(c.created_at);
      const inDateRange = createdAt >= dateRange.from && createdAt <= dateRange.to;
      const matchesMyAccounts =
        !myAccountsOnly ||
        (userAccountIds.instagramId != null && c.instagram_outreach_account?.id === userAccountIds.instagramId) ||
        (userAccountIds.tiktokId != null && c.tiktok_outreach_account?.id === userAccountIds.tiktokId);
      return inDateRange && matchesMyAccounts;
    });

    const starredCount = creators.filter((c) => c.starred).length;

    return { total: filtered.length, starredCount };
  }, [creators, dateRange, myAccountsOnly, userAccountIds]);

  const chartData = useMemo(() => {
    const filtered = creators.filter((c) => {
      const createdAt = new Date(c.created_at);
      const inRange = createdAt >= dateRange.from && createdAt <= dateRange.to;
      const matchesAddedBy =
        addedByFilter === "all" || c.added_by?.id === addedByFilter;
      const matchesStatus =
        selectedStatuses.length === 0 || selectedStatuses.includes(c.status);
      return inRange && matchesAddedBy && matchesStatus;
    });

    const byDay = new Map<string, number>();
    filtered.forEach((c) => {
      const day = getDayKey(c.created_at);
      byDay.set(day, (byDay.get(day) || 0) + 1);
    });

    return Array.from(byDay.entries())
      .map(([date, count]) => ({ date, count }))
      .sort((a, b) => a.date.localeCompare(b.date));
  }, [creators, dateRange, addedByFilter, selectedStatuses]);

  const filteredCreators = useMemo(() => {
    const q = searchQuery.toLowerCase();
    return creators.filter((creator) => {
      const matchesSearch =
        !q ||
        (creator.full_name?.toLowerCase().includes(q) ?? false) ||
        (creator.instagram_handle?.toLowerCase().includes(q) ?? false) ||
        (creator.tiktok_handle?.toLowerCase().includes(q) ?? false);
      const createdAt = new Date(creator.created_at);
      const matchesDateRange =
        createdAt >= dateRange.from && createdAt <= dateRange.to;
      const matchesStatus =
        selectedStatuses.length === 0 || selectedStatuses.includes(creator.status);
      const matchesMyAccounts =
        !myAccountsOnly ||
        (userAccountIds.instagramId != null && creator.instagram_outreach_account?.id === userAccountIds.instagramId) ||
        (userAccountIds.tiktokId != null && creator.tiktok_outreach_account?.id === userAccountIds.tiktokId);

      return matchesSearch && matchesDateRange && matchesStatus && matchesMyAccounts;
    });
  }, [creators, searchQuery, dateRange, selectedStatuses, myAccountsOnly, userAccountIds]);

  return (
    <div>
      <div className="flex items-center justify-between mb-4 sm:mb-6">
        <h1 className="text-xl sm:text-2xl font-semibold">Creator Outreach</h1>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <div className="rounded-md border">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 p-3 sm:p-4 border-b">
            <TabsList className="w-full sm:w-auto">
              <TabsTrigger value="main" className="flex-1 sm:flex-none">Table</TabsTrigger>
              <TabsTrigger value="chart" className="flex-1 sm:flex-none">Chart</TabsTrigger>
            </TabsList>
            <div className="flex items-center gap-1 overflow-x-auto pb-1 sm:pb-0">
              <Button
                variant={isAllTimeActive() ? "default" : "outline"}
                size="sm"
                onClick={handleAllTime}
              >
                All
              </Button>
              <Button
                variant={!isAllTimeActive() && isPresetActive(0) ? "default" : "outline"}
                size="sm"
                onClick={() => handleDatePreset(0)}
                className="whitespace-nowrap"
              >
                Today
              </Button>
              <Button
                variant={!isAllTimeActive() && isThisWeekActive() ? "default" : "outline"}
                size="sm"
                onClick={() => setDateRange(getThisWeekRange())}
                className="whitespace-nowrap"
              >
                This week
              </Button>
              <Button
                variant={!isAllTimeActive() && isPresetActive(7) ? "default" : "outline"}
                size="sm"
                onClick={() => handleDatePreset(7)}
              >
                7d
              </Button>
              <Button
                variant={!isAllTimeActive() && isPresetActive(30) ? "default" : "outline"}
                size="sm"
                onClick={() => handleDatePreset(30)}
              >
                30d
              </Button>
              <Button
                variant={!isAllTimeActive() && isPresetActive(90) ? "default" : "outline"}
                size="sm"
                onClick={() => handleDatePreset(90)}
              >
                90d
              </Button>
            </div>
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  size="sm"
                  className="min-w-0 sm:min-w-[200px] justify-start text-left text-xs sm:text-sm w-full sm:w-auto"
                >
                  <CalendarIcon className="mr-1 sm:mr-2 h-4 w-4 flex-shrink-0" />
                  <span className="truncate">
                    {format(dateRange.from, "MMM d")} - {format(dateRange.to, "MMM d")}
                  </span>
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="end">
                <Calendar
                  mode="range"
                  selected={dateRange}
                  onSelect={(range: { from?: Date; to?: Date } | undefined) => {
                    if (range?.from && range?.to) {
                      setDateRange({ from: range.from, to: range.to });
                    }
                  }}
                  numberOfMonths={2}
                />
              </PopoverContent>
            </Popover>
          </div>

          <div className="p-3 sm:p-4 border-b">
            <div className="flex items-center gap-2 sm:gap-3 overflow-x-auto pb-1 sm:pb-0">
              <div className="bg-gray-50 rounded-lg p-3 sm:p-4 flex-shrink-0">
                <div className="text-xs sm:text-sm text-gray-600">Total Reached</div>
                <div className="text-xl sm:text-2xl font-bold">{stats.total}</div>
              </div>
              <div className="bg-yellow-50 rounded-lg p-3 sm:p-4 flex-shrink-0">
                <div className="flex items-center gap-1 text-xs sm:text-sm text-yellow-700">
                  <Star className="h-3.5 w-3.5 fill-yellow-400 text-yellow-400" />
                  Starred
                </div>
                <div className="text-xl sm:text-2xl font-bold text-yellow-800">{stats.starredCount}</div>
              </div>
              {selectedStatuses.map((status) => {
                const config = statusConfig[status];
                const count = filteredCreators.filter((c) => c.status === status).length;
                return (
                  <div key={status} className={cn("rounded-lg p-3 sm:p-4 flex-shrink-0", config.selectedClassName)}>
                    <div className="text-xs sm:text-sm opacity-80">{config.label}</div>
                    <div className="text-xl sm:text-2xl font-bold">{count}</div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="flex flex-col gap-3 p-3 sm:p-4 border-b">
            <div className="flex flex-wrap items-center gap-2">
              <div className="flex flex-wrap gap-1.5">
                {statusOptions.map((option) => {
                  const config = statusConfig[option.value];
                  const isSelected = selectedStatuses.includes(option.value);
                  return (
                    <button
                      key={option.value}
                      onClick={() => toggleStatus(option.value)}
                      className={cn(
                        "inline-flex items-center px-2 sm:px-2.5 py-1 rounded text-xs font-medium border transition-colors",
                        isSelected
                          ? config.selectedClassName
                          : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100",
                      )}
                    >
                      {option.label}
                    </button>
                  );
                })}
              </div>
              {isAdmin && profiles.length > 0 && (
                <>
                  <div className="w-px h-6 bg-gray-200 hidden sm:block" />
                  <Select value={addedByFilter} onValueChange={setAddedByFilter}>
                    <SelectTrigger className="w-[160px] h-8 text-xs">
                      <SelectValue placeholder="Added by" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All people</SelectItem>
                      {profiles.map((profile) => (
                        <SelectItem key={profile.id} value={profile.id}>
                          {profile.full_name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </>
              )}
            </div>
            {activeTab === "main" && (
              <div className="relative w-full">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  ref={searchInputRef}
                  placeholder="Search by name or handle... (⌘F)"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-9"
                />
              </div>
            )}
          </div>

          <TabsContent value="main" className="mt-0">
            {loading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
              </div>
            ) : (
              <CreatorsTable
                columns={columns}
                data={filteredCreators}
                onRowClick={handleRowClick}
                isRowSelected={(c) => c.id === selectedCreatorId}
              />
            )}
          </TabsContent>

          <TabsContent value="chart" className="mt-0">
            <div className="p-3 sm:p-4 h-[300px] sm:h-[400px]">
              <CreatorsChart data={chartData} />
            </div>
          </TabsContent>
        </div>
      </Tabs>

      <CreatorModal
        creatorId={selectedCreatorId}
        creatorIds={filteredCreators.map((c) => c.id)}
        open={creatorModalOpen}
        onOpenChange={setCreatorModalOpen}
        onStatusChanged={handleStatusChanged}
        onCreatorDeleted={handleCreatorDeleted}
        onNavigate={setSelectedCreatorId}
      />
    </div>
  );
}
