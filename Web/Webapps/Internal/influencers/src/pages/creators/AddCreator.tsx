import { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Loader2,
  XCircle,
  Settings,
  ClipboardPaste,
} from "lucide-react";
import {
  AddCreatorResultDisplay,
  type AddCreatorResult,
} from "@/components/creators/add-creator-result";
import { CreatorModal } from "@/components/creators/creator-modal";
import { createClient } from "@/lib/supabase/client";
import { fetchSettings, submitCreator } from "@/lib/supabase/queries";

export default function AddCreatorPage() {
  const supabase = createClient();
  const [loading, setLoading] = useState(false);
  const [checkingSettings, setCheckingSettings] = useState(true);
  const [hasAccount, setHasAccount] = useState(false);
  const [result, setResult] = useState<AddCreatorResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedCreatorId, setSelectedCreatorId] = useState<string | null>(null);
  const [creatorModalOpen, setCreatorModalOpen] = useState(false);

  useEffect(() => {
    checkSettings();
  }, []);

  const checkSettings = async () => {
    try {
      const data = await fetchSettings(supabase);
      setHasAccount(
        !!data.va?.instagram_outreach_account_id || !!data.va?.tiktok_outreach_account_id,
      );
    } catch {
      // Ignore errors
    } finally {
      setCheckingSettings(false);
    }
  };

  const handlePasteAndSubmit = async () => {
    setLoading(true);
    setResult(null);
    setError(null);

    try {
      const clipboardText = await navigator.clipboard.readText();

      if (!clipboardText.trim()) {
        throw new Error("Clipboard is empty");
      }

      const data = await submitCreator(supabase, clipboardText.trim());
      setResult(data);
    } catch (err) {
      if (err instanceof Error && err.name === "NotAllowedError") {
        setError("Clipboard access denied. Please allow clipboard access.");
      } else {
        setError(err instanceof Error ? err.message : "Something went wrong");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setResult(null);
    setError(null);
  };

  const handleOpenCreator = (creatorId: string) => {
    setSelectedCreatorId(creatorId);
    setCreatorModalOpen(true);
  };

  if (checkingSettings) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  if (!hasAccount) {
    return (
      <div className="max-w-md mx-auto mt-8 sm:mt-12 px-4 sm:px-0">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base sm:text-lg">
              <Settings className="h-5 w-5" />
              Setup Required
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm sm:text-base text-gray-600 mb-4">
              Please set up at least one outreach account (Instagram or TikTok)
              before adding creators.
            </p>
            <Link to="/settings">
              <Button>Go to Settings</Button>
            </Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="max-w-xl mx-auto px-4 sm:px-0">
      <h1 className="text-xl sm:text-2xl font-semibold mb-4 sm:mb-6">Add Creator</h1>

      {!result && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Paste Creator URL</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Copy an Instagram or TikTok profile URL, then tap the button below.
            </p>

            {error && (
              <Alert className="border-red-200 bg-red-50">
                <XCircle className="h-4 w-4 text-red-600" />
                <AlertDescription className="text-red-700">
                  {error}
                </AlertDescription>
              </Alert>
            )}

            <Button
              onClick={handlePasteAndSubmit}
              disabled={loading}
              size="lg"
              className="w-full"
            >
              {loading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin mr-2" />
                  Checking...
                </>
              ) : (
                <>
                  <ClipboardPaste className="h-4 w-4 mr-2" />
                  Paste & Submit
                </>
              )}
            </Button>
          </CardContent>
        </Card>
      )}

      {result && (
        <AddCreatorResultDisplay
          result={result}
          variant="card"
          onReset={handleReset}
          onOpenCreator={handleOpenCreator}
        />
      )}

      <CreatorModal
        creatorId={selectedCreatorId}
        creatorIds={selectedCreatorId ? [selectedCreatorId] : []}
        open={creatorModalOpen}
        onOpenChange={setCreatorModalOpen}
        onStatusChanged={() => {}}
        onCreatorDeleted={() => {}}
      />
    </div>
  );
}
