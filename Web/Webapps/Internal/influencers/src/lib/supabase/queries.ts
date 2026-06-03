import type { SupabaseClient } from "@supabase/supabase-js";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type AnySupabaseClient = SupabaseClient<any, any, any>;
import { parseCreatorUrl } from "@/lib/outreach/url-parser";

// ---- Accounts ----

export async function fetchAccounts(supabase: AnySupabaseClient) {
  const { data: accounts, error } = await supabase
    .from("outreach_accounts")
    .select("id, platform, handle, display_name")
    .is("deleted_at", null)
    .order("display_name", { ascending: true });

  if (error) throw new Error("Failed to fetch accounts");
  return { accounts: accounts || [] };
}

// ---- Creators List ----

const CREATORS_PAGE_SIZE = 1000;

export async function fetchCreators(
  supabase: AnySupabaseClient,
  addedById?: string | null
) {
  const selectCols = `
      id,
      instagram_handle,
      tiktok_handle,
      instagram_url,
      tiktok_url,
      full_name,
      status,
      starred,
      email,
      created_at,
      added_by:profiles!added_by_profile_id(id, full_name),
      instagram_outreach_account:outreach_accounts!instagram_outreach_account_id(id, handle, display_name),
      tiktok_outreach_account:outreach_accounts!tiktok_outreach_account_id(id, handle, display_name)
    `;

  const filterByAddedBy =
    addedById &&
    addedById !== "all" &&
    (await supabase.rpc("get_user_role").then(({ data }) => data === "admin"));

  let allCreators: unknown[] = [];
  let offset = 0;
  let hasMore = true;

  while (hasMore) {
    let query = supabase
      .from("creators")
      .select(selectCols)
      .is("deleted_at", null)
      .order("created_at", { ascending: false })
      .range(offset, offset + CREATORS_PAGE_SIZE - 1);

    if (filterByAddedBy && addedById) {
      query = query.eq("added_by_profile_id", addedById);
    }

    const { data: creators, error } = await query;
    if (error) throw new Error("Failed to fetch creators");

    const page = creators || [];
    allCreators = allCreators.concat(page);
    hasMore = page.length === CREATORS_PAGE_SIZE;
    offset += CREATORS_PAGE_SIZE;
  }

  // Supabase joins may return arrays for FK relations; flatten to single objects
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const normalized = (allCreators as any[]).map((c: any) => ({
    ...c,
    added_by: Array.isArray(c.added_by) ? c.added_by[0] || null : c.added_by,
    instagram_outreach_account: Array.isArray(c.instagram_outreach_account)
      ? c.instagram_outreach_account[0] || null
      : c.instagram_outreach_account,
    tiktok_outreach_account: Array.isArray(c.tiktok_outreach_account)
      ? c.tiktok_outreach_account[0] || null
      : c.tiktok_outreach_account,
  }));
  return { creators: normalized };
}

// ---- Creator Detail ----

export async function fetchCreatorDetail(
  supabase: AnySupabaseClient,
  id: string
) {
  const { data: creator, error: creatorError } = await supabase
    .from("creators")
    .select(
      `id, instagram_handle, tiktok_handle, instagram_url, tiktok_url,
       full_name, status, niche, email, first_name, phone_number,
       created_at, added_by_profile_id, would_add`
    )
    .eq("id", id)
    .single();

  if (creatorError || !creator) throw new Error("Creator not found");

  const { data: addedByProfile } = await supabase
    .from("profiles")
    .select("full_name")
    .eq("id", creator.added_by_profile_id)
    .single();

  const { data: notes } = await supabase
    .from("creator_notes")
    .select(
      `id, content, created_at, profile:created_by_profile_id (full_name)`
    )
    .eq("creator_id", id);

  const { data: statusChanges } = await supabase
    .from("creator_status_changes")
    .select(
      `id, old_status, new_status, created_at, profile:changed_by_profile_id (full_name)`
    )
    .eq("creator_id", id);

  // Build timeline by merging notes and status changes
  const timeline: Array<{
    type: "note" | "status_change";
    id: string;
    createdAt: string;
    authorName: string;
    content?: string;
    oldStatus?: string;
    newStatus?: string;
  }> = [];

  for (const note of notes || []) {
    const profile = note.profile as unknown as { full_name: string } | null;
    timeline.push({
      type: "note",
      id: note.id,
      createdAt: note.created_at,
      authorName: profile?.full_name || "Unknown",
      content: note.content,
    });
  }

  for (const change of statusChanges || []) {
    const profile = change.profile as unknown as { full_name: string } | null;
    timeline.push({
      type: "status_change",
      id: change.id,
      createdAt: change.created_at,
      authorName: profile?.full_name || "Unknown",
      oldStatus: change.old_status,
      newStatus: change.new_status,
    });
  }

  timeline.sort(
    (a, b) =>
      new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  );

  return {
    creator: {
      id: creator.id,
      instagramHandle: creator.instagram_handle,
      tiktokHandle: creator.tiktok_handle,
      instagramUrl: creator.instagram_url,
      tiktokUrl: creator.tiktok_url,
      fullName: creator.full_name,
      status: creator.status,
      niche: creator.niche,
      email: creator.email,
      firstName: creator.first_name,
      phoneNumber: creator.phone_number,
      createdAt: creator.created_at,
      addedByName: addedByProfile?.full_name || "Unknown",
      wouldAdd: creator.would_add,
    },
    timeline,
  };
}

// ---- Update Creator Status ----

export async function updateCreatorStatus(
  supabase: AnySupabaseClient,
  id: string,
  status: string
) {
  const validStatuses = [
    "sent",
    "replied",
    "maybe_follow_up",
    "need_to_decline",
    "declined",
    "approved",
    "sent_more_info",
    "call_scheduled",
    "contract_sent",
    "guidelines_sent",
    "references_sent",
    "active",
    "delayed",
    "followup",
    "loose_ends",
  ];
  if (!status || !validStatuses.includes(status))
    throw new Error("Invalid status");

  const { data: creator, error: creatorError } = await supabase
    .from("creators")
    .select("id, status")
    .eq("id", id)
    .single();
  if (creatorError || !creator) throw new Error("Creator not found");

  const oldStatus = creator.status;

  const { error: updateError } = await supabase
    .from("creators")
    .update({ status })
    .eq("id", id);
  if (updateError) throw new Error("Failed to update creator");

  if (oldStatus !== status) {
    const { data: profileId } = await supabase.rpc("get_profile_id");
    await supabase.from("creator_status_changes").insert({
      creator_id: id,
      old_status: oldStatus,
      new_status: status,
      changed_by_profile_id: profileId,
    });
  }

  return { success: true };
}

// ---- Delete Creator (soft delete) ----

export async function deleteCreator(supabase: AnySupabaseClient, id: string) {
  const { error } = await supabase
    .from("creators")
    .update({ deleted_at: new Date().toISOString() })
    .eq("id", id);
  if (error) throw new Error(`Failed to delete creator: ${error.message}`);
  return { success: true };
}

// ---- Toggle Starred ----

export async function toggleStarred(
  supabase: AnySupabaseClient,
  id: string,
  starred: boolean
) {
  const { error } = await supabase
    .from("creators")
    .update({ starred })
    .eq("id", id);
  if (error) throw new Error(`Failed to update starred: ${error.message}`);
  return { success: true, starred };
}

// ---- Update Creator Email/Name ----

export async function updateCreatorEmail(
  supabase: AnySupabaseClient,
  id: string,
  email?: string | null,
  first_name?: string | null,
  phone_number?: string | null
) {
  if (email && typeof email === "string" && email.trim()) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email.trim())) throw new Error("Invalid email format");
  }

  const { error } = await supabase
    .from("creators")
    .update({
      email: email?.trim() || null,
      first_name: first_name?.trim() || null,
      phone_number: phone_number?.trim() || null,
    })
    .eq("id", id);
  if (error) throw new Error("Failed to update email");

  return {
    success: true,
    email: email?.trim() || null,
    first_name: first_name?.trim() || null,
  };
}

export async function updateCreatorFullName(
  supabase: AnySupabaseClient,
  id: string,
  full_name: string | null
) {
  const { error } = await supabase
    .from("creators")
    .update({
      full_name: full_name?.trim() || null,
    })
    .eq("id", id);

  if (error) throw new Error("Failed to update full name");
  return { success: true };
}

// ---- Creator Notes ----

export async function addCreatorNote(
  supabase: AnySupabaseClient,
  creatorId: string,
  content: string
) {
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const { data: va } = await supabase
    .from("profiles")
    .select("id, full_name")
    .eq("auth_user_id", user.id)
    .single();
  if (!va) throw new Error("Profile not found");

  const { data: creator } = await supabase
    .from("creators")
    .select("id")
    .eq("id", creatorId)
    .single();
  if (!creator) throw new Error("Creator not found");

  if (!content || typeof content !== "string" || content.trim().length === 0) {
    throw new Error("Note content is required");
  }

  const { data: note, error } = await supabase
    .from("creator_notes")
    .insert({
      creator_id: creatorId,
      content: content.trim(),
      created_by_profile_id: va.id,
    })
    .select()
    .single();
  if (error) throw new Error("Failed to add note");

  return {
    note: {
      id: note.id,
      content: note.content,
      createdAt: note.created_at,
      authorName: va.full_name,
    },
  };
}

export async function deleteCreatorNote(
  supabase: AnySupabaseClient,
  creatorId: string,
  noteId: string
) {
  const { data: note } = await supabase
    .from("creator_notes")
    .select("id, creator_id")
    .eq("id", noteId)
    .single();
  if (!note) throw new Error("Note not found");
  if (note.creator_id !== creatorId)
    throw new Error("Note does not belong to this creator");

  const { error } = await supabase
    .from("creator_notes")
    .delete()
    .eq("id", noteId);
  if (error) throw new Error("Failed to delete note");
  return { success: true };
}

// ---- Would Add (admin only) ----

export async function updateWouldAdd(
  supabase: AnySupabaseClient,
  creatorId: string,
  wouldAdd: boolean | null
) {
  const { data: role } = await supabase.rpc("get_user_role");
  if (role !== "admin") throw new Error("Only admins can update this field");

  const { error } = await supabase
    .from("creators")
    .update({ would_add: wouldAdd })
    .eq("id", creatorId);
  if (error) throw new Error("Failed to update would_add");
  return { success: true, wouldAdd };
}

// ---- Settings ----

export async function fetchSettings(supabase: AnySupabaseClient) {
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const { data: va, error } = await supabase
    .from("profiles")
    .select(
      `id, full_name, email, role, is_active,
       instagram_outreach_account_id, tiktok_outreach_account_id,
       instagram_outreach_message, tiktok_outreach_message`
    )
    .eq("auth_user_id", user.id)
    .single();
  if (error) throw new Error("Failed to fetch settings");

  let instagramAccount = null;
  let tiktokAccount = null;

  if (va?.instagram_outreach_account_id) {
    const { data } = await supabase
      .from("outreach_accounts")
      .select("id, platform, handle, display_name")
      .eq("id", va.instagram_outreach_account_id)
      .single();
    instagramAccount = data;
  }
  if (va?.tiktok_outreach_account_id) {
    const { data } = await supabase
      .from("outreach_accounts")
      .select("id, platform, handle, display_name")
      .eq("id", va.tiktok_outreach_account_id)
      .single();
    tiktokAccount = data;
  }

  return { va, instagramAccount, tiktokAccount };
}

export async function updateSettings(
  supabase: AnySupabaseClient,
  fields: {
    instagramAccountId?: string | null;
    tiktokAccountId?: string | null;
    instagramMessage?: string | null;
    tiktokMessage?: string | null;
  }
) {
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const update: Record<string, unknown> = {};
  if ("instagramAccountId" in fields) {
    update.instagram_outreach_account_id = fields.instagramAccountId || null;
  }
  if ("tiktokAccountId" in fields) {
    update.tiktok_outreach_account_id = fields.tiktokAccountId || null;
  }
  if ("instagramMessage" in fields) {
    update.instagram_outreach_message = fields.instagramMessage?.trim() || null;
  }
  if ("tiktokMessage" in fields) {
    update.tiktok_outreach_message = fields.tiktokMessage?.trim() || null;
  }

  const { data: va, error } = await supabase
    .from("profiles")
    .update(update)
    .eq("auth_user_id", user.id)
    .select()
    .single();
  if (error) throw new Error("Failed to update settings");
  return { va };
}

// ---- Outreach Message Variants ----

export type OutreachPlatform = "instagram" | "tiktok";

export interface OutreachMessageVariant {
  id: string;
  profile_id: string;
  platform: OutreachPlatform;
  label: string;
  message: string;
  is_active: boolean;
  created_at: string;
}

export async function fetchVariants(supabase: AnySupabaseClient) {
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const { data: profile } = await supabase
    .from("profiles")
    .select("id")
    .eq("auth_user_id", user.id)
    .single();
  if (!profile) throw new Error("Profile not found");

  const { data, error } = await supabase
    .from("outreach_message_variants")
    .select("id, profile_id, platform, label, message, is_active, created_at")
    .eq("profile_id", profile.id)
    .order("created_at", { ascending: true });
  if (error) throw new Error("Failed to fetch variants");
  return { variants: (data || []) as OutreachMessageVariant[] };
}

function nextVariantLabel(existingLabels: string[]): string {
  const used = new Set(existingLabels.map((l) => l.toUpperCase()));
  let n = 0;
  while (true) {
    let label = "";
    let x = n;
    do {
      label = String.fromCharCode(65 + (x % 26)) + label;
      x = Math.floor(x / 26) - 1;
    } while (x >= 0);
    if (!used.has(label)) return label;
    n++;
  }
}

export async function createVariant(
  supabase: AnySupabaseClient,
  platform: OutreachPlatform,
  message: string
) {
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const { data: profile } = await supabase
    .from("profiles")
    .select("id")
    .eq("auth_user_id", user.id)
    .single();
  if (!profile) throw new Error("Profile not found");

  const { data: existing } = await supabase
    .from("outreach_message_variants")
    .select("label")
    .eq("profile_id", profile.id)
    .eq("platform", platform);

  const label = nextVariantLabel((existing || []).map((r) => r.label as string));

  const { data, error } = await supabase
    .from("outreach_message_variants")
    .insert({ profile_id: profile.id, platform, label, message })
    .select()
    .single();
  if (error) throw new Error("Failed to create variant");
  return { variant: data as OutreachMessageVariant };
}

export async function updateVariant(
  supabase: AnySupabaseClient,
  id: string,
  fields: { message?: string; is_active?: boolean }
) {
  const { data, error } = await supabase
    .from("outreach_message_variants")
    .update(fields)
    .eq("id", id)
    .select()
    .single();
  if (error) throw new Error("Failed to update variant");
  return { variant: data as OutreachMessageVariant };
}

export async function deleteVariant(supabase: AnySupabaseClient, id: string) {
  const { error } = await supabase
    .from("outreach_message_variants")
    .delete()
    .eq("id", id);
  if (error) throw new Error("Failed to delete variant");
}

// ---- Submit Creator ----

export async function submitCreator(supabase: AnySupabaseClient, url: string) {
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  if (!url) throw new Error("URL is required");

  const { data: profile } = await supabase
    .from("profiles")
    .select(
      "id, instagram_outreach_account_id, tiktok_outreach_account_id, instagram_outreach_message, tiktok_outreach_message"
    )
    .eq("auth_user_id", user.id)
    .single();
  if (!profile) throw new Error("Profile not found. Please contact an admin.");

  const parseResult = parseCreatorUrl(url);
  if (!parseResult.success) {
    return { status: "invalid" as const, error: parseResult.error };
  }

  const { platform, handle, normalizedUrl } = parseResult.data;
  const outreachAccountId =
    platform === "instagram"
      ? profile.instagram_outreach_account_id
      : profile.tiktok_outreach_account_id;

  if (!outreachAccountId) {
    throw new Error(
      `Please select ${platform === "instagram" ? "an Instagram" : "a TikTok"} outreach account in Settings first`
    );
  }

  // Resolve the outreach message for this user+platform up front so we can
  // return it on every result type (inserted, duplicate, blocked) and the UI
  // never has to fall back to the hardcoded global default for the current
  // user's own message.
  //
  // Order: random active A/B variant → user's standard profile message →
  // hardcoded default in the display component.
  const { data: activeVariants } = await supabase
    .from("outreach_message_variants")
    .select("id, message")
    .eq("profile_id", profile.id)
    .eq("platform", platform)
    .eq("is_active", true);

  const selectedVariant =
    activeVariants && activeVariants.length > 0
      ? activeVariants[Math.floor(Math.random() * activeVariants.length)]
      : null;

  const standardMessage =
    platform === "instagram"
      ? (profile as { instagram_outreach_message?: string | null }).instagram_outreach_message
      : (profile as { tiktok_outreach_message?: string | null }).tiktok_outreach_message;

  const outreachMessage =
    selectedVariant?.message ?? (standardMessage?.trim() ? standardMessage : null);

  // Check if creator is blocked (active/declined) across ALL outreach accounts
  const { data: blockedCreators } = await supabase.rpc(
    "check_creator_blocked_status",
    { p_platform: platform, p_handle: handle }
  );
  const blockedCreator = blockedCreators?.[0] ?? null;

  if (blockedCreator) {
    let addedByName = "Unknown";
    if (blockedCreator.added_by_profile_id) {
      const { data: p } = await supabase
        .from("profiles")
        .select("full_name")
        .eq("id", blockedCreator.added_by_profile_id)
        .single();
      addedByName = p?.full_name || "Unknown";
    }
    return {
      status: "blocked" as const,
      existingCreator: {
        id: blockedCreator.id,
        instagramHandle: blockedCreator.instagram_handle,
        tiktokHandle: blockedCreator.tiktok_handle,
        platform,
        addedByName,
        creatorStatus: blockedCreator.status,
      },
      outreachMessage,
    };
  }

  // Check if creator already exists on this platform (scoped to outreach account)
  const { data: existingCreators } = await supabase.rpc(
    "check_creator_exists",
    { p_platform: platform, p_handle: handle, p_outreach_account_id: outreachAccountId }
  );
  const existingCreator = existingCreators?.[0] ?? null;

  if (existingCreator) {
    let addedByName = "Unknown";
    if (existingCreator.added_by_profile_id) {
      const { data: p } = await supabase
        .from("profiles")
        .select("full_name")
        .eq("id", existingCreator.added_by_profile_id)
        .single();
      addedByName = p?.full_name || "Unknown";
    }
    return {
      status: "duplicate" as const,
      existingCreator: {
        id: existingCreator.id,
        instagramHandle: existingCreator.instagram_handle,
        tiktokHandle: existingCreator.tiktok_handle,
        platform,
        firstReachedOutAt: existingCreator.first_reached_out_at,
        addedByName,
        creatorStatus: existingCreator.status,
      },
      outreachMessage,
    };
  }

  // Insert new creator with the correct platform columns
  const insertData: Record<string, unknown> = {
    added_by_profile_id: profile.id,
    outreach_variant_id: selectedVariant?.id ?? null,
  };

  if (platform === "instagram") {
    insertData.instagram_handle = handle;
    insertData.instagram_url = normalizedUrl;
    insertData.instagram_outreach_account_id = outreachAccountId;
  } else {
    insertData.tiktok_handle = handle;
    insertData.tiktok_url = normalizedUrl;
    insertData.tiktok_outreach_account_id = outreachAccountId;
  }

  const { data: newCreator, error: insertError } = await supabase
    .from("creators")
    .insert(insertData)
    .select()
    .single();

  if (insertError) {
    if (insertError.code === "23505") {
      const { data: raceCreators } = await supabase.rpc(
        "check_creator_exists",
        { p_platform: platform, p_handle: handle, p_outreach_account_id: outreachAccountId }
      );
      const rc = raceCreators?.[0] ?? null;
      return {
        status: "duplicate" as const,
        existingCreator: {
          id: rc?.id,
          instagramHandle: rc?.instagram_handle,
          tiktokHandle: rc?.tiktok_handle,
          platform,
          firstReachedOutAt: rc?.first_reached_out_at,
          addedByName: "Another user (just now)",
          creatorStatus: rc?.status,
        },
        outreachMessage,
      };
    }
    throw new Error("Failed to add creator");
  }

  return {
    status: "inserted" as const,
    creator: {
      id: newCreator.id,
      platform,
      handle,
      profileUrl: normalizedUrl,
    },
    outreachMessage,
  };
}

// ---- Add Platform to Creator ----

export async function addPlatformToCreator(
  supabase: AnySupabaseClient,
  creatorId: string,
  platform: string,
  handle: string,
  url: string
) {
  const { error } = await supabase.rpc("add_platform_to_creator", {
    p_creator_id: creatorId,
    p_platform: platform,
    p_handle: handle,
    p_url: url,
  });
  if (error) throw new Error("Failed to add platform");
  return { success: true };
}

// ---- Admin: VAs ----

export async function fetchVAs(supabase: AnySupabaseClient) {
  const { data: role } = await supabase.rpc("get_user_role");
  if (role !== "admin") throw new Error("Unauthorized");

  const { data: vas, error } = await supabase
    .from("profiles")
    .select("id, full_name, email, role, is_active, created_at")
    .in("role", ["admin", "va"])
    .order("created_at", { ascending: false });
  if (error) throw new Error("Failed to fetch VAs");
  return { vas: vas || [] };
}

// ---- Admin: Accounts CRUD ----

export async function fetchAdminAccounts(supabase: AnySupabaseClient) {
  const { data: role } = await supabase.rpc("get_user_role");
  if (role !== "admin") throw new Error("Unauthorized");

  const { data: accounts, error } = await supabase
    .from("outreach_accounts")
    .select("id, platform, handle, niche, display_name, is_active, created_at")
    .is("deleted_at", null)
    .order("created_at", { ascending: false });
  if (error) throw new Error("Failed to fetch accounts");
  return { accounts: accounts || [] };
}

export async function createAdminAccount(
  supabase: AnySupabaseClient,
  platform: string,
  handle: string,
  niche: string,
  displayName?: string | null
) {
  const { data: role } = await supabase.rpc("get_user_role");
  if (role !== "admin") throw new Error("Unauthorized");

  if (!platform || !handle || !niche) {
    throw new Error("Platform, handle, and niche are required");
  }
  if (!["instagram", "tiktok"].includes(platform)) {
    throw new Error("Platform must be 'instagram' or 'tiktok'");
  }

  const { data: newAccount, error } = await supabase
    .from("outreach_accounts")
    .insert({
      platform,
      handle: handle.toLowerCase().replace(/^@/, ""),
      niche,
      display_name: displayName || null,
    })
    .select()
    .single();

  if (error) {
    if (error.code === "23505")
      throw new Error(
        "An account with this platform and handle already exists"
      );
    throw new Error("Failed to create account");
  }
  return { account: newAccount };
}

export async function deleteAdminAccount(
  supabase: AnySupabaseClient,
  id: string
) {
  const { data: role } = await supabase.rpc("get_user_role");
  if (role !== "admin") throw new Error("Unauthorized");

  const { error } = await supabase
    .from("outreach_accounts")
    .update({ deleted_at: new Date().toISOString() })
    .eq("id", id);
  if (error) throw new Error("Failed to delete account");
  return { success: true };
}

// ---- Auth helpers ----

export async function getUserRole(supabase: AnySupabaseClient) {
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return null;

  const { data: profile } = await supabase
    .from("profiles")
    .select("role")
    .eq("auth_user_id", user.id)
    .single();
  return (profile?.role as "va" | "admin") ?? null;
}
