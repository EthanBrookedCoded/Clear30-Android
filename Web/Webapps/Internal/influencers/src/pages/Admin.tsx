import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Loader2, Plus, CheckCircle, XCircle, Trash2 } from "lucide-react";
import { createClient } from "@/lib/supabase/client";
import {
  fetchVAs,
  fetchAdminAccounts,
  createAdminAccount,
  deleteAdminAccount,
} from "@/lib/supabase/queries";

interface VA {
  id: string;
  full_name: string;
  email: string;
  role: "va" | "admin";
  is_active: boolean;
  created_at: string;
}

interface Account {
  id: string;
  platform: string;
  handle: string;
  niche: string;
  display_name: string | null;
  is_active: boolean;
  created_at: string;
}

export default function AdminPage() {
  const supabase = createClient();
  const [vas, setVas] = useState<VA[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [accountPlatform, setAccountPlatform] = useState("");
  const [accountHandle, setAccountHandle] = useState("");
  const [accountNiche, setAccountNiche] = useState("");
  const [accountDisplayName, setAccountDisplayName] = useState("");
  const [creatingAccount, setCreatingAccount] = useState(false);
  const [accountCreateError, setAccountCreateError] = useState<string | null>(null);
  const [accountCreateSuccess, setAccountCreateSuccess] = useState(false);
  const [deletingAccountId, setDeletingAccountId] = useState<string | null>(null);

  useEffect(() => {
    loadVAs();
    loadAccounts();
  }, []);

  const loadVAs = async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await fetchVAs(supabase);
      setVas(data.vas || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load VAs");
    } finally {
      setLoading(false);
    }
  };

  const loadAccounts = async () => {
    try {
      const data = await fetchAdminAccounts(supabase);
      setAccounts(data.accounts || []);
    } catch (err) {
      console.error("Failed to fetch accounts:", err);
    }
  };

  const handleCreateAccount = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!accountPlatform || !accountHandle.trim() || !accountNiche.trim()) return;

    setCreatingAccount(true);
    setAccountCreateError(null);
    setAccountCreateSuccess(false);

    try {
      await createAdminAccount(
        supabase,
        accountPlatform,
        accountHandle.trim(),
        accountNiche.trim(),
        accountDisplayName.trim() || null,
      );

      setAccountCreateSuccess(true);
      setAccountPlatform("");
      setAccountHandle("");
      setAccountNiche("");
      setAccountDisplayName("");
      loadAccounts();

      setTimeout(() => setAccountCreateSuccess(false), 3000);
    } catch (err) {
      setAccountCreateError(
        err instanceof Error ? err.message : "Failed to create account",
      );
    } finally {
      setCreatingAccount(false);
    }
  };

  const handleDeleteAccount = async (id: string) => {
    if (!confirm("Are you sure you want to delete this account?")) return;

    setDeletingAccountId(id);

    try {
      await deleteAdminAccount(supabase, id);
      loadAccounts();
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to delete account");
    } finally {
      setDeletingAccountId(null);
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-2xl mx-auto">
        <Alert className="border-red-200 bg-red-50">
          <XCircle className="h-4 w-4 text-red-600" />
          <AlertDescription className="text-red-700">{error}</AlertDescription>
        </Alert>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-2xl font-semibold mb-6">Admin</h1>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="text-lg">Team</CardTitle>
        </CardHeader>
        <CardContent>
          {vas.length === 0 ? (
            <p className="text-gray-500 text-sm">No VAs yet</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {vas.map((va) => (
                  <TableRow key={va.id}>
                    <TableCell className="font-medium">{va.full_name}</TableCell>
                    <TableCell className="text-gray-600">{va.email}</TableCell>
                    <TableCell>
                      {va.role === "admin" ? (
                        <span className="text-xs bg-purple-100 text-purple-700 px-2 py-1 rounded">
                          Admin
                        </span>
                      ) : (
                        <span className="text-xs bg-gray-100 text-gray-600 px-2 py-1 rounded">
                          VA
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-gray-600 text-sm">
                      {formatDate(va.created_at)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="text-lg">Add Outreach Account</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleCreateAccount} className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <Select
                value={accountPlatform}
                onValueChange={setAccountPlatform}
                disabled={creatingAccount}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Platform" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="instagram">Instagram</SelectItem>
                  <SelectItem value="tiktok">TikTok</SelectItem>
                </SelectContent>
              </Select>
              <Input
                type="text"
                value={accountHandle}
                onChange={(e) => setAccountHandle(e.target.value)}
                placeholder="Handle (e.g. username)"
                disabled={creatingAccount}
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Input
                type="text"
                value={accountNiche}
                onChange={(e) => setAccountNiche(e.target.value)}
                placeholder="Niche"
                disabled={creatingAccount}
              />
              <Input
                type="text"
                value={accountDisplayName}
                onChange={(e) => setAccountDisplayName(e.target.value)}
                placeholder="Display Name (optional)"
                disabled={creatingAccount}
              />
            </div>

            {accountCreateError && (
              <Alert className="border-red-200 bg-red-50">
                <AlertDescription className="text-red-700 text-sm">
                  {accountCreateError}
                </AlertDescription>
              </Alert>
            )}

            {accountCreateSuccess && (
              <Alert className="border-green-200 bg-green-50">
                <CheckCircle className="h-4 w-4 text-green-600" />
                <AlertDescription className="text-green-700 text-sm">
                  Account created successfully
                </AlertDescription>
              </Alert>
            )}

            <Button
              type="submit"
              disabled={
                creatingAccount ||
                !accountPlatform ||
                !accountHandle.trim() ||
                !accountNiche.trim()
              }
              size="sm"
            >
              {creatingAccount ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin mr-2" />
                  Creating...
                </>
              ) : (
                <>
                  <Plus className="h-4 w-4 mr-2" />
                  Add Account
                </>
              )}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Outreach Accounts</CardTitle>
        </CardHeader>
        <CardContent>
          {accounts.length === 0 ? (
            <p className="text-gray-500 text-sm">No accounts yet</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Platform</TableHead>
                  <TableHead>Handle</TableHead>
                  <TableHead>Niche</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {accounts.map((account) => (
                  <TableRow key={account.id}>
                    <TableCell>
                      <span
                        className={`text-xs px-2 py-1 rounded ${
                          account.platform === "instagram"
                            ? "bg-pink-100 text-pink-700"
                            : "bg-gray-100 text-gray-700"
                        }`}
                      >
                        {account.platform}
                      </span>
                    </TableCell>
                    <TableCell className="font-medium">
                      @{account.handle}
                      {account.display_name && (
                        <span className="text-gray-500 ml-2">
                          ({account.display_name})
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-gray-600">{account.niche}</TableCell>
                    <TableCell className="text-gray-600 text-sm">
                      {formatDate(account.created_at)}
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDeleteAccount(account.id)}
                        disabled={deletingAccountId === account.id}
                        className="text-red-600 hover:text-red-700 hover:bg-red-50"
                      >
                        {deletingAccountId === account.id ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <Trash2 className="h-4 w-4" />
                        )}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
