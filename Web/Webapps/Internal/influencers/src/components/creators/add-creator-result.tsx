"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  CheckCircle,
  AlertTriangle,
  XCircle,
  ExternalLink,
  Copy,
  Check,
} from "lucide-react";

const OUTREACH_MESSAGES: Record<string, string> = {
  tiktok: `Paid promo?
Our app helps people who are struggling with weed take a 30-day break with daily check-ins and personalized support.
Let me know if you're interested and we can send over details!
So far we've been lucky to help over 100,000 people and your voice could make a real difference for so many more :)
Thatcher from the Clear30 Team`,
  instagram: `Paid promo?
Our app helps people who are struggling with weed take a 30-day break with daily check-ins and personalized support.
Let me know if you're interested and we can send over details!
So far we've been lucky to help over 100,000 people and your voice could make a real difference for so many more :)
Asher from the Clear30 Team`,
};

export type AddCreatorResult =
  | {
      status: "inserted";
      creator: {
        id?: string;
        platform: string;
        handle: string;
        profileUrl?: string;
      };
      outreachMessage?: string | null;
    }
  | {
      status: "duplicate";
      existingCreator: {
        id?: string;
        instagramHandle?: string | null;
        tiktokHandle?: string | null;
        platform: string;
        firstReachedOutAt?: string;
        addedByName?: string;
        creatorStatus?: string;
      };
      outreachMessage?: string | null;
    }
  | {
      status: "blocked";
      existingCreator: {
        id?: string;
        instagramHandle?: string | null;
        tiktokHandle?: string | null;
        platform: string;
        addedByName?: string;
        creatorStatus?: string;
      };
      outreachMessage?: string | null;
    }
  | {
      status: "invalid";
      error: string;
    }
  | {
      status: "error";
      error: string;
    };

export type AddCreatorResultVariant = "card" | "minimal";

interface AddCreatorResultDisplayProps {
  result: AddCreatorResult;
  variant?: AddCreatorResultVariant;
  onReset?: () => void;
  onOpenCreator?: (creatorId: string) => void;
}

export function AddCreatorResultDisplay({
  result,
  variant = "card",
  onReset,
  onOpenCreator,
}: AddCreatorResultDisplayProps) {
  const [copied, setCopied] = useState(false);

  const getOutreachMessage = (platform: string, customMessage?: string | null) => {
    if (customMessage && customMessage.trim()) return customMessage;
    return OUTREACH_MESSAGES[platform] || OUTREACH_MESSAGES.instagram;
  };

  const copyToClipboard = async (text: string) => {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand("copy");
      document.body.removeChild(textarea);
    }
  };

  const copyMessage = async (platform: string, customMessage?: string | null) => {
    await copyToClipboard(getOutreachMessage(platform, customMessage));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const copyAndOpenProfile = async (creator: { platform: string; handle: string; profileUrl?: string }, customMessage?: string | null) => {
    await copyToClipboard(getOutreachMessage(creator.platform, customMessage));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    window.open(getProfileUrl(creator), "_blank");
  };

  const getProfileUrl = (creator: { platform: string; handle: string; profileUrl?: string }) => {
    if (creator.profileUrl) return creator.profileUrl;
    return creator.platform === "instagram"
      ? `https://instagram.com/${creator.handle}`
      : `https://www.tiktok.com/@${creator.handle}`;
  };

  // Success state
  if (result.status === "inserted") {
    const message = getOutreachMessage(result.creator.platform, result.outreachMessage);

    if (variant === "minimal") {
      return (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <CheckCircle className="h-6 w-6 text-green-600" />
            <h1 className="text-lg font-semibold text-green-800">New Creator Added</h1>
          </div>

          <p className="text-sm text-black font-medium">
            @{result.creator.handle} ·{" "}
            <span className="capitalize">{result.creator.platform}</span>
          </p>

          <div className="bg-gray-50 border border-gray-200 rounded-lg p-3">
            <p className="text-xs text-gray-500 mb-1 font-medium">
              {result.creator.platform === "tiktok" ? "TikTok" : "Instagram"} Message
            </p>
            <p className="text-sm text-gray-700 whitespace-pre-line">{message}</p>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => copyMessage(result.creator.platform, result.outreachMessage)}
            >
              {copied ? <Check className="h-4 w-4 mr-2" /> : <Copy className="h-4 w-4 mr-2" />}
              {copied ? "Copied!" : "Copy Message"}
            </Button>
            <Button
              size="sm"
              onClick={() => copyAndOpenProfile(result.creator, result.outreachMessage)}
            >
              <ExternalLink className="h-4 w-4 mr-2" />
              {copied ? "Copied!" : "Copy & Open Profile"}
            </Button>
          </div>

          {onReset && (
            <Button variant="ghost" size="sm" onClick={onReset} className="w-full">
              Add Another
            </Button>
          )}
        </div>
      );
    }

    return (
      <Card className="border-green-200 bg-green-50">
        <CardContent className="space-y-3 pt-6">
          <div className="flex items-center gap-2">
            <CheckCircle className="h-6 w-6 text-green-600" />
            <p className="font-semibold text-green-800">New Creator Added</p>
          </div>

          <p className="text-sm text-black font-medium">
            @{result.creator.handle} ·{" "}
            <span className="capitalize">{result.creator.platform}</span>
          </p>

          <div className="bg-white border border-green-200 rounded-lg p-3">
            <p className="text-xs text-gray-500 mb-1 font-medium">
              {result.creator.platform === "tiktok" ? "TikTok" : "Instagram"} Message
            </p>
            <p className="text-sm text-gray-700 whitespace-pre-line">{message}</p>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => copyMessage(result.creator.platform, result.outreachMessage)}
            >
              {copied ? <Check className="h-4 w-4 mr-2" /> : <Copy className="h-4 w-4 mr-2" />}
              {copied ? "Copied!" : "Copy Message"}
            </Button>
            <Button
              size="sm"
              onClick={() => copyAndOpenProfile(result.creator, result.outreachMessage)}
            >
              <ExternalLink className="h-4 w-4 mr-2" />
              {copied ? "Copied!" : "Copy & Open Profile"}
            </Button>
          </div>

          {onReset && <Button variant="ghost" size="sm" onClick={onReset} className="w-full mt-1">Add Another</Button>}
        </CardContent>
      </Card>
    );
  }

  // Duplicate state
  if (result.status === "duplicate") {
    const displayHandle = result.existingCreator.instagramHandle
      ? `@${result.existingCreator.instagramHandle}`
      : result.existingCreator.tiktokHandle
        ? `@${result.existingCreator.tiktokHandle}`
        : "Unknown";

    const platforms: string[] = [];
    if (result.existingCreator.instagramHandle) platforms.push("Instagram");
    if (result.existingCreator.tiktokHandle) platforms.push("TikTok");

    if (variant === "minimal") {
      return (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-6 w-6 text-amber-600" />
            <h1 className="text-lg font-semibold text-amber-800">Duplicate - Do Not Message</h1>
          </div>

          <p className="text-sm text-black font-medium">
            {displayHandle} · {platforms.join(" & ")}
          </p>

          {result.existingCreator.addedByName && (
            <p className="text-xs text-gray-500">
              Added by {result.existingCreator.addedByName}
            </p>
          )}

          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              onClick={() => copyMessage(result.existingCreator.platform, result.outreachMessage)}
            >
              {copied ? <Check className="h-4 w-4 mr-2" /> : <Copy className="h-4 w-4 mr-2" />}
              {copied ? "Copied!" : "Copy Message"}
            </Button>
            {onOpenCreator && result.existingCreator.id && (
              <Button
                variant="outline"
                onClick={() => onOpenCreator(result.existingCreator.id!)}
              >
                Open Creator Info
              </Button>
            )}
            {onReset && (
              <Button onClick={onReset}>
                Try Another
              </Button>
            )}
          </div>
        </div>
      );
    }

    return (
      <Card className="border-amber-200 bg-amber-50">
        <CardContent className="space-y-4 pt-6">
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-6 w-6 text-amber-600" />
            <p className="font-semibold text-amber-800">
              Duplicate - Do Not Message
            </p>
          </div>

          <p className="text-sm text-black font-medium">
            {displayHandle} · {platforms.join(" & ")}
          </p>

          {result.existingCreator.addedByName && (
            <p className="text-xs text-gray-500">
              Added by {result.existingCreator.addedByName}
            </p>
          )}

          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              onClick={() => copyMessage(result.existingCreator.platform, result.outreachMessage)}
            >
              {copied ? <Check className="h-4 w-4 mr-2" /> : <Copy className="h-4 w-4 mr-2" />}
              {copied ? "Copied!" : "Copy Message"}
            </Button>
            {onOpenCreator && result.existingCreator.id && (
              <Button
                variant="outline"
                onClick={() => onOpenCreator(result.existingCreator.id!)}
              >
                Open Creator Info
              </Button>
            )}
            {onReset && <Button onClick={onReset}>Try Another</Button>}
          </div>
        </CardContent>
      </Card>
    );
  }

  // Blocked state (active/declined across accounts)
  if (result.status === "blocked") {
    const displayHandle = result.existingCreator.instagramHandle
      ? `@${result.existingCreator.instagramHandle}`
      : result.existingCreator.tiktokHandle
        ? `@${result.existingCreator.tiktokHandle}`
        : "Unknown";

    const statusLabel = result.existingCreator.creatorStatus === "active" ? "active" : "declined";
    const message = statusLabel === "active"
      ? `This creator is already active (added by ${result.existingCreator.addedByName || "Unknown"}). No need to reach out again.`
      : `This creator has declined (added by ${result.existingCreator.addedByName || "Unknown"}). Skipping outreach.`;

    if (variant === "minimal") {
      return (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <XCircle className="h-6 w-6 text-red-600" />
            <h1 className="text-lg font-semibold text-red-800">Blocked - Do Not Message</h1>
          </div>

          <p className="text-sm text-black font-medium">{displayHandle}</p>
          <p className="text-sm text-gray-600">{message}</p>

          <div className="flex flex-wrap gap-2">
            {onOpenCreator && result.existingCreator.id && (
              <Button
                variant="outline"
                onClick={() => onOpenCreator(result.existingCreator.id!)}
              >
                Open Creator Info
              </Button>
            )}
            {onReset && <Button onClick={onReset}>Try Another</Button>}
          </div>
        </div>
      );
    }

    return (
      <Card className="border-red-200 bg-red-50">
        <CardContent className="space-y-4 pt-6">
          <div className="flex items-center gap-2">
            <XCircle className="h-6 w-6 text-red-600" />
            <p className="font-semibold text-red-800">Blocked - Do Not Message</p>
          </div>

          <p className="text-sm text-black font-medium">{displayHandle}</p>
          <p className="text-sm text-gray-600">{message}</p>

          <div className="flex flex-wrap gap-2">
            {onOpenCreator && result.existingCreator.id && (
              <Button
                variant="outline"
                onClick={() => onOpenCreator(result.existingCreator.id!)}
              >
                Open Creator Info
              </Button>
            )}
            {onReset && <Button onClick={onReset}>Try Another</Button>}
          </div>
        </CardContent>
      </Card>
    );
  }

  // Invalid URL state
  if (result.status === "invalid") {
    if (variant === "minimal") {
      return (
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <XCircle className="h-6 w-6 text-red-600" />
            <h1 className="text-lg font-semibold text-red-800">Invalid URL</h1>
          </div>
          <p className="text-sm text-gray-600">{result.error}</p>
          {onReset && (
            <Button onClick={onReset}>
              Try Again
            </Button>
          )}
        </div>
      );
    }

    return (
      <Card className="border-red-200 bg-red-50">
        <CardHeader className="pb-2">
          <CardTitle className="text-lg flex items-center gap-2 text-red-800">
            <XCircle className="h-5 w-5" />
            Invalid URL
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-red-700">{result.error}</p>

          <div className="bg-white rounded-lg p-3 border border-red-200 text-sm text-gray-600">
            <p className="font-medium mb-1">Valid URL examples:</p>
            <ul className="list-disc list-inside space-y-1">
              <li>instagram.com/username</li>
              <li>tiktok.com/@username</li>
            </ul>
          </div>

          {onReset && <Button onClick={onReset}>Try Again</Button>}
        </CardContent>
      </Card>
    );
  }

  // Error state
  if (result.status === "error") {
    if (variant === "minimal") {
      return (
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <XCircle className="h-6 w-6 text-red-600" />
            <h1 className="text-lg font-semibold text-red-800">Error</h1>
          </div>
          <p className="text-sm text-gray-600">{result.error}</p>
          {onReset && (
            <Button onClick={onReset}>
              Try Again
            </Button>
          )}
        </div>
      );
    }

    return (
      <Card className="border-red-200 bg-red-50">
        <CardContent className="space-y-3 pt-6">
          <div className="flex items-center gap-2">
            <XCircle className="h-6 w-6 text-red-600" />
            <p className="font-semibold text-red-800">Error</p>
          </div>
          <p className="text-sm text-gray-600">{result.error}</p>
          {onReset && <Button onClick={onReset}>Try Again</Button>}
        </CardContent>
      </Card>
    );
  }

  return null;
}
