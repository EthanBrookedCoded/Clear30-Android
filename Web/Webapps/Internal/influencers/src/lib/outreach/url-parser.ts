export type Platform = "instagram" | "tiktok";

export interface ParsedUrl {
  platform: Platform;
  handle: string;
  normalizedUrl: string;
}

export interface ParseResult {
  success: true;
  data: ParsedUrl;
}

export interface ParseError {
  success: false;
  error: string;
}

export type UrlParseResult = ParseResult | ParseError;

/**
 * Parse and normalize a creator profile URL
 * Extracts platform and handle from Instagram/TikTok URLs
 */
export function parseCreatorUrl(rawUrl: string): UrlParseResult {
  const trimmed = rawUrl.trim();

  if (!trimmed) {
    return { success: false, error: "Please enter a URL" };
  }

  // Try to parse as URL
  let url: URL;
  try {
    // Add https:// if missing
    const urlWithProtocol = trimmed.startsWith("http")
      ? trimmed
      : `https://${trimmed}`;
    url = new URL(urlWithProtocol);
  } catch {
    return { success: false, error: "Invalid URL format" };
  }

  const hostname = url.hostname.toLowerCase();

  // Instagram
  if (hostname.includes("instagram.com")) {
    return parseInstagramUrl(url);
  }

  // TikTok
  if (hostname.includes("tiktok.com")) {
    return parseTikTokUrl(url);
  }

  return {
    success: false,
    error: "Only Instagram and TikTok URLs are supported",
  };
}

function parseInstagramUrl(url: URL): UrlParseResult {
  const pathParts = url.pathname.split("/").filter(Boolean);

  if (pathParts.length === 0) {
    return { success: false, error: "Could not find Instagram username in URL" };
  }

  const firstPart = pathParts[0].toLowerCase();

  // Reserved paths that aren't usernames
  const reservedPaths = [
    "p",
    "reel",
    "reels",
    "stories",
    "explore",
    "direct",
    "accounts",
    "tv",
    "live",
  ];

  if (reservedPaths.includes(firstPart)) {
    if (firstPart === "p" || firstPart === "reel" || firstPart === "reels") {
      return {
        success: false,
        error: "Please paste the creator's profile URL, not a post or reel URL",
      };
    }
    return { success: false, error: "Could not find Instagram username in URL" };
  }

  // Normalize handle: lowercase, no leading @
  let handle = firstPart;
  if (handle.startsWith("@")) {
    handle = handle.substring(1);
  }

  // Validate handle format (Instagram: 1-30 chars, letters, numbers, periods, underscores)
  if (!/^[a-z0-9._]{1,30}$/.test(handle)) {
    return { success: false, error: "Invalid Instagram username format" };
  }

  return {
    success: true,
    data: {
      platform: "instagram",
      handle,
      normalizedUrl: `https://www.instagram.com/${handle}/`,
    },
  };
}

function parseTikTokUrl(url: URL): UrlParseResult {
  const hostname = url.hostname.toLowerCase();

  // Reject short URLs (vm.tiktok.com)
  if (hostname === "vm.tiktok.com") {
    return {
      success: false,
      error: "Please paste the full TikTok profile URL, not a shortened link",
    };
  }

  const pathParts = url.pathname.split("/").filter(Boolean);

  if (pathParts.length === 0) {
    return { success: false, error: "Could not find TikTok username in URL" };
  }

  let handle = pathParts[0];

  // TikTok usernames start with @
  if (!handle.startsWith("@")) {
    // Check if it's a video URL
    if (handle === "video" || pathParts.some((p) => p === "video")) {
      return {
        success: false,
        error: "Please paste the creator's profile URL, not a video URL",
      };
    }
    return {
      success: false,
      error: "TikTok profile URLs should contain @username",
    };
  }

  // Remove @ prefix for storage, lowercase
  handle = handle.substring(1).toLowerCase();

  // Validate handle format (TikTok: 2-24 chars, letters, numbers, periods, underscores)
  if (!/^[a-z0-9._]{2,24}$/.test(handle)) {
    return { success: false, error: "Invalid TikTok username format" };
  }

  return {
    success: true,
    data: {
      platform: "tiktok",
      handle,
      normalizedUrl: `https://www.tiktok.com/@${handle}`,
    },
  };
}

