import { useState, useEffect, useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import { Loader2 } from "lucide-react";
import {
  AddCreatorResultDisplay,
  type AddCreatorResult,
} from "@/components/creators/add-creator-result";
import { CreatorModal } from "@/components/creators/creator-modal";
import { createClient } from "@/lib/supabase/client";
import { submitCreator } from "@/lib/supabase/queries";

export default function QuickSubmitPage() {
  const [searchParams] = useSearchParams();
  const initialUrl = searchParams.get("url");
  const supabase = createClient();

  const [loading, setLoading] = useState(true);
  const [result, setResult] = useState<AddCreatorResult | null>(null);
  const [selectedCreatorId, setSelectedCreatorId] = useState<string | null>(null);
  const [creatorModalOpen, setCreatorModalOpen] = useState(false);

  const submitUrl = useCallback(async (url: string) => {
    setLoading(true);
    setResult(null);

    try {
      const data = await submitCreator(supabase, url);
      setResult(data);
    } catch (err) {
      setResult({
        status: "error",
        error: err instanceof Error ? err.message : "Request failed",
      });
    } finally {
      setLoading(false);
    }
  }, []);

  const handlePasteAndSubmit = useCallback(async () => {
    try {
      const clipboardText = await navigator.clipboard.readText();
      if (clipboardText.trim()) {
        await submitUrl(clipboardText.trim());
      } else {
        setResult({ status: "error", error: "Clipboard is empty" });
      }
    } catch {
      setResult({ status: "error", error: "Could not read clipboard" });
    }
  }, [submitUrl]);

  const handleOpenCreator = (creatorId: string) => {
    setSelectedCreatorId(creatorId);
    setCreatorModalOpen(true);
  };

  useEffect(() => {
    if (!initialUrl) {
      setResult({ status: "error", error: "No URL provided" });
      setLoading(false);
      return;
    }

    submitUrl(initialUrl);
  }, [initialUrl, submitUrl]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white p-4">
        <Loader2 className="h-8 w-8 animate-spin text-gray-400" />
      </div>
    );
  }

  if (!result) {
    return null;
  }

  return (
    <div className="-m-6 min-h-dvh bg-white flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        <AddCreatorResultDisplay
          result={result}
          variant="minimal"
          onReset={handlePasteAndSubmit}
          onOpenCreator={handleOpenCreator}
        />
      </div>

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
