import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Loader2, CheckCircle, LogOut, Plus, Trash2 } from "lucide-react";
import { createClient } from "@/lib/supabase/client";
import {
  fetchAccounts,
  fetchSettings,
  updateSettings,
  fetchVariants,
  createVariant,
  updateVariant,
  deleteVariant,
  type OutreachMessageVariant,
  type OutreachPlatform,
} from "@/lib/supabase/queries";
import { useAuth } from "@/lib/auth-provider";

interface OutreachAccount {
  id: string;
  platform: string;
  handle: string;
  niche?: string | null;
  display_name: string;
}

interface UserSettings {
  id: string;
  full_name: string;
  email: string;
  role: "va" | "admin";
  instagram_outreach_account_id: string | null;
  tiktok_outreach_account_id: string | null;
  instagram_outreach_message: string | null;
  tiktok_outreach_message: string | null;
}

const PLACEHOLDERS: Record<OutreachPlatform, string> = {
  instagram: `Paid promo?
Our app helps people who are struggling with weed take a 30-day break with daily check-ins and personalized support.
Let me know if you're interested and we can send over details!
So far we've been lucky to help over 100,000 people and your voice could make a real difference for so many more :)
Asher from the Clear30 Team`,
  tiktok: `Paid promo?
Our app helps people who are struggling with weed take a 30-day break with daily check-ins and personalized support.
Let me know if you're interested and we can send over details!
So far we've been lucky to help over 100,000 people and your voice could make a real difference for so many more :)
Thatcher from the Clear30 Team`,
};

export default function SettingsPage() {
  const { signOut } = useAuth();
  const [accounts, setAccounts] = useState<OutreachAccount[]>([]);
  const [user, setUser] = useState<UserSettings | null>(null);
  const [selectedInstagramId, setSelectedInstagramId] = useState<string>("");
  const [selectedTiktokId, setSelectedTiktokId] = useState<string>("");
  const [variants, setVariants] = useState<OutreachMessageVariant[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [newDrafts, setNewDrafts] = useState<Record<OutreachPlatform, string>>({
    instagram: "",
    tiktok: "",
  });
  const [standardMessages, setStandardMessages] = useState<Record<OutreachPlatform, string>>({
    instagram: "",
    tiktok: "",
  });
  const [savedStandardMessages, setSavedStandardMessages] = useState<
    Record<OutreachPlatform, string>
  >({ instagram: "", tiktok: "" });
  const [savingStandardPlatform, setSavingStandardPlatform] =
    useState<OutreachPlatform | null>(null);
  const [standardSavedAt, setStandardSavedAt] = useState<OutreachPlatform | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [variantsError, setVariantsError] = useState<string | null>(null);
  const [busyVariantId, setBusyVariantId] = useState<string | null>(null);
  const [creatingPlatform, setCreatingPlatform] = useState<OutreachPlatform | null>(null);

  const handleSignOut = async () => {
    setSigningOut(true);
    await signOut();
  };

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    setError(null);

    try {
      const supabase = createClient();
      const [accountsData, settingsData, variantsData] = await Promise.all([
        fetchAccounts(supabase),
        fetchSettings(supabase),
        fetchVariants(supabase),
      ]);

      setAccounts(accountsData.accounts || []);
      setUser(settingsData.va);
      if (settingsData.va?.instagram_outreach_account_id) {
        setSelectedInstagramId(settingsData.va.instagram_outreach_account_id);
      }
      if (settingsData.va?.tiktok_outreach_account_id) {
        setSelectedTiktokId(settingsData.va.tiktok_outreach_account_id);
      }
      const ig = settingsData.va?.instagram_outreach_message ?? "";
      const tt = settingsData.va?.tiktok_outreach_message ?? "";
      setStandardMessages({ instagram: ig, tiktok: tt });
      setSavedStandardMessages({ instagram: ig, tiktok: tt });
      setVariants(variantsData.variants);
      setDrafts(
        Object.fromEntries(variantsData.variants.map((v) => [v.id, v.message])),
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load data");
    } finally {
      setLoading(false);
    }
  };

  const saveSettings = async (instagramId: string, tiktokId: string) => {
    setSaving(true);
    setError(null);
    setSuccess(false);

    try {
      const supabase = createClient();
      const data = await updateSettings(supabase, {
        instagramAccountId: instagramId || null,
        tiktokAccountId: tiktokId || null,
      });

      setUser(data.va);
      setSuccess(true);
      setTimeout(() => setSuccess(false), 2000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save settings");
    } finally {
      setSaving(false);
    }
  };

  const handleInstagramChange = (value: string) => {
    setSelectedInstagramId(value);
    saveSettings(value, selectedTiktokId);
  };

  const handleTiktokChange = (value: string) => {
    setSelectedTiktokId(value);
    saveSettings(selectedInstagramId, value);
  };

  const saveStandardMessage = async (platform: OutreachPlatform) => {
    setSavingStandardPlatform(platform);
    setError(null);
    try {
      const supabase = createClient();
      const value = standardMessages[platform];
      const data = await updateSettings(
        supabase,
        platform === "instagram"
          ? { instagramMessage: value }
          : { tiktokMessage: value },
      );
      setUser(data.va);
      setSavedStandardMessages((prev) => ({ ...prev, [platform]: value }));
      setStandardSavedAt(platform);
      setTimeout(
        () =>
          setStandardSavedAt((current) => (current === platform ? null : current)),
        2000,
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save message");
    } finally {
      setSavingStandardPlatform(null);
    }
  };

  const saveVariant = async (variant: OutreachMessageVariant) => {
    const draft = drafts[variant.id] ?? variant.message;
    if (draft === variant.message) return;
    setBusyVariantId(variant.id);
    setVariantsError(null);
    try {
      const supabase = createClient();
      const { variant: updated } = await updateVariant(supabase, variant.id, {
        message: draft,
      });
      setVariants((prev) => prev.map((v) => (v.id === updated.id ? updated : v)));
    } catch (err) {
      setVariantsError(err instanceof Error ? err.message : "Failed to save variant");
    } finally {
      setBusyVariantId(null);
    }
  };

  const toggleActive = async (variant: OutreachMessageVariant) => {
    setBusyVariantId(variant.id);
    setVariantsError(null);
    try {
      const supabase = createClient();
      const { variant: updated } = await updateVariant(supabase, variant.id, {
        is_active: !variant.is_active,
      });
      setVariants((prev) => prev.map((v) => (v.id === updated.id ? updated : v)));
    } catch (err) {
      setVariantsError(err instanceof Error ? err.message : "Failed to update variant");
    } finally {
      setBusyVariantId(null);
    }
  };

  const removeVariant = async (variant: OutreachMessageVariant) => {
    if (!confirm(`Delete variant ${variant.label}?`)) return;
    setBusyVariantId(variant.id);
    setVariantsError(null);
    try {
      const supabase = createClient();
      await deleteVariant(supabase, variant.id);
      setVariants((prev) => prev.filter((v) => v.id !== variant.id));
      setDrafts((prev) => {
        const next = { ...prev };
        delete next[variant.id];
        return next;
      });
    } catch (err) {
      setVariantsError(err instanceof Error ? err.message : "Failed to delete variant");
    } finally {
      setBusyVariantId(null);
    }
  };

  const addVariant = async (platform: OutreachPlatform) => {
    const message = newDrafts[platform].trim();
    if (!message) return;
    setCreatingPlatform(platform);
    setVariantsError(null);
    try {
      const supabase = createClient();
      const { variant } = await createVariant(supabase, platform, message);
      setVariants((prev) => [...prev, variant]);
      setDrafts((prev) => ({ ...prev, [variant.id]: variant.message }));
      setNewDrafts((prev) => ({ ...prev, [platform]: "" }));
    } catch (err) {
      setVariantsError(err instanceof Error ? err.message : "Failed to create variant");
    } finally {
      setCreatingPlatform(null);
    }
  };

  const instagramAccounts = accounts.filter((a) => a.platform === "instagram");
  const tiktokAccounts = accounts.filter((a) => a.platform === "tiktok");
  const selectedInstagramAccount = accounts.find((a) => a.id === selectedInstagramId);
  const selectedTiktokAccount = accounts.find((a) => a.id === selectedTiktokId);

  const renderVariantList = (platform: OutreachPlatform) => {
    const platformVariants = variants
      .filter((v) => v.platform === platform)
      .sort((a, b) => a.label.localeCompare(b.label));

    return (
      <div className="space-y-4">
        {platformVariants.map((variant) => {
          const draft = drafts[variant.id] ?? variant.message;
          const dirty = draft !== variant.message;
          const busy = busyVariantId === variant.id;
          return (
            <div
              key={variant.id}
              className={`border rounded-lg p-3 space-y-2 ${
                variant.is_active ? "bg-white" : "bg-gray-50 border-dashed"
              }`}
            >
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold">Variant {variant.label}</span>
                  <button
                    type="button"
                    className={`text-xs px-2 py-0.5 rounded-full border ${
                      variant.is_active
                        ? "bg-green-50 text-green-700 border-green-200"
                        : "bg-gray-100 text-gray-500 border-gray-200"
                    } disabled:opacity-50`}
                    onClick={() => toggleActive(variant)}
                    disabled={busy}
                  >
                    {variant.is_active ? "Active" : "Inactive"}
                  </button>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => removeVariant(variant)}
                  disabled={busy}
                  className="text-red-600 hover:text-red-700"
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
              <Textarea
                value={draft}
                onChange={(e) =>
                  setDrafts((prev) => ({ ...prev, [variant.id]: e.target.value }))
                }
                rows={6}
                disabled={busy}
              />
              {dirty && (
                <div className="flex justify-end">
                  <Button size="sm" onClick={() => saveVariant(variant)} disabled={busy}>
                    {busy ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                    Save
                  </Button>
                </div>
              )}
            </div>
          );
        })}

        <div className="border border-dashed rounded-lg p-3 space-y-2">
          <p className="text-xs text-gray-500 font-medium">New variant</p>
          <Textarea
            value={newDrafts[platform]}
            onChange={(e) =>
              setNewDrafts((prev) => ({ ...prev, [platform]: e.target.value }))
            }
            placeholder={PLACEHOLDERS[platform]}
            rows={6}
            disabled={creatingPlatform === platform}
          />
          <div className="flex justify-end">
            <Button
              size="sm"
              onClick={() => addVariant(platform)}
              disabled={
                creatingPlatform === platform || !newDrafts[platform].trim()
              }
            >
              {creatingPlatform === platform ? (
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
              ) : (
                <Plus className="h-4 w-4 mr-2" />
              )}
              Add Variant
            </Button>
          </div>
        </div>
      </div>
    );
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  return (
    <div className="max-w-xl">
      <h1 className="text-2xl font-semibold mb-6">Settings</h1>

      {error && (
        <Alert className="mb-4 border-red-200 bg-red-50">
          <AlertDescription className="text-red-700">{error}</AlertDescription>
        </Alert>
      )}

      {(user?.role === "va" || user?.role === "admin") && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Outreach Accounts</CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium text-gray-700 mb-2 block">
                  Instagram Account
                </label>
                <Select value={selectedInstagramId} onValueChange={handleInstagramChange} disabled={saving}>
                  <SelectTrigger className="w-full min-w-[280px]">
                    <SelectValue placeholder="Select an Instagram account" />
                  </SelectTrigger>
                  <SelectContent>
                    {instagramAccounts.map((account) => (
                      <SelectItem key={account.id} value={account.id}>
                        {account.display_name
                          ? `${account.display_name} · @${account.handle}${account.niche ? ` · ${account.niche}` : ""}`
                          : `@${account.handle}${account.niche ? ` · ${account.niche}` : ""}`}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {selectedInstagramAccount && (
                <div className="bg-gray-50 rounded-lg p-3 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-600">Handle</span>
                    <span className="font-medium">@{selectedInstagramAccount.handle}</span>
                  </div>
                  {selectedInstagramAccount.niche && (
                    <div className="flex justify-between mt-1">
                      <span className="text-gray-600">Niche</span>
                      <span className="font-medium capitalize">{selectedInstagramAccount.niche}</span>
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium text-gray-700 mb-2 block">
                  TikTok Account
                </label>
                <Select value={selectedTiktokId} onValueChange={handleTiktokChange} disabled={saving}>
                  <SelectTrigger className="w-full min-w-[280px]">
                    <SelectValue placeholder="Select a TikTok account" />
                  </SelectTrigger>
                  <SelectContent>
                    {tiktokAccounts.map((account) => (
                      <SelectItem key={account.id} value={account.id}>
                        {account.display_name
                          ? `${account.display_name} · @${account.handle}${account.niche ? ` · ${account.niche}` : ""}`
                          : `@${account.handle}${account.niche ? ` · ${account.niche}` : ""}`}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {selectedTiktokAccount && (
                <div className="bg-gray-50 rounded-lg p-3 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-600">Handle</span>
                    <span className="font-medium">@{selectedTiktokAccount.handle}</span>
                  </div>
                  {selectedTiktokAccount.niche && (
                    <div className="flex justify-between mt-1">
                      <span className="text-gray-600">Niche</span>
                      <span className="font-medium capitalize">{selectedTiktokAccount.niche}</span>
                    </div>
                  )}
                </div>
              )}
            </div>

            {success && (
              <p className="text-sm text-green-600 flex items-center gap-1">
                <CheckCircle className="h-4 w-4" />
                Saved
              </p>
            )}
          </CardContent>
        </Card>
      )}

      {(user?.role === "va" || user?.role === "admin") && (
        <Card className="mt-4">
          <CardHeader>
            <CardTitle className="text-lg">Standard Message</CardTitle>
            <p className="text-xs text-gray-500">
              Used when you have no active A/B variants for a platform. Leave blank to use the team default.
            </p>
          </CardHeader>
          <CardContent className="space-y-6">
            {(["instagram", "tiktok"] as OutreachPlatform[]).map((platform) => {
              const value = standardMessages[platform];
              const dirty = value !== savedStandardMessages[platform];
              const busy = savingStandardPlatform === platform;
              return (
                <div key={platform} className="space-y-2">
                  <label className="text-sm font-medium text-gray-700 capitalize">
                    {platform}
                  </label>
                  <Textarea
                    value={value}
                    onChange={(e) =>
                      setStandardMessages((prev) => ({
                        ...prev,
                        [platform]: e.target.value,
                      }))
                    }
                    placeholder={PLACEHOLDERS[platform]}
                    rows={6}
                    disabled={busy}
                  />
                  <div className="flex items-center justify-end gap-2">
                    {standardSavedAt === platform && (
                      <span className="text-xs text-green-600 flex items-center gap-1">
                        <CheckCircle className="h-3 w-3" />
                        Saved
                      </span>
                    )}
                    {dirty && (
                      <Button
                        size="sm"
                        onClick={() => saveStandardMessage(platform)}
                        disabled={busy}
                      >
                        {busy ? (
                          <Loader2 className="h-4 w-4 animate-spin mr-2" />
                        ) : null}
                        Save
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </CardContent>
        </Card>
      )}

      {(user?.role === "va" || user?.role === "admin") && (
        <Card className="mt-4">
          <CardHeader>
            <CardTitle className="text-lg">A/B Test Variants</CardTitle>
            <p className="text-xs text-gray-500">
              Optional. When you have one or more active variants for a platform, a random one is picked instead of your standard message.
            </p>
          </CardHeader>
          <CardContent className="space-y-6">
            {variantsError && (
              <Alert className="border-red-200 bg-red-50">
                <AlertDescription className="text-red-700">{variantsError}</AlertDescription>
              </Alert>
            )}

            <div className="space-y-3">
              <h3 className="text-sm font-semibold text-gray-700">Instagram</h3>
              {renderVariantList("instagram")}
            </div>

            <div className="space-y-3">
              <h3 className="text-sm font-semibold text-gray-700">TikTok</h3>
              {renderVariantList("tiktok")}
            </div>
          </CardContent>
        </Card>
      )}

      {user && (
        <Card className="mt-4">
          <CardHeader>
            <CardTitle className="text-lg">Your Profile</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-sm space-y-1">
              <div className="flex justify-between">
                <span className="text-gray-600">Name</span>
                <span className="font-medium">{user.full_name}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">Email</span>
                <span className="font-medium">{user.email}</span>
              </div>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={handleSignOut}
              disabled={signingOut}
              className="mt-4 w-full"
            >
              {signingOut ? (
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
              ) : (
                <LogOut className="h-4 w-4 mr-2" />
              )}
              Sign Out
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
