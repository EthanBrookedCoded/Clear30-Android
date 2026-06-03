import { useState, useEffect } from "react";
import { Link } from "react-router-dom";

const pages = [
  {
    title: "Creator Outreach",
    description: "View creator profiles and track outreach status.",
    href: "/creators/outreach",
    emoji: "\uD83D\uDCAC",
  },
  {
    title: "Add Creators",
    description: "Add new creators to your outreach list.",
    href: "/creators/add",
    emoji: "\u2795",
  },
  {
    title: "Settings",
    description: "Configure your outreach accounts.",
    href: "/settings",
    emoji: "\u2699\uFE0F",
  },
];

export default function HomePage() {
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => setLoading(false), 100);
    return () => clearTimeout(timer);
  }, []);

  return (
    <div className="flex flex-col items-center justify-center min-h-[calc(100vh-3.5rem)] p-6">
      <h1 className="text-3xl font-semibold text-gray-800 mb-8">
        {"Welcome! \uD83D\uDC4B"}
      </h1>

      <div className="min-h-[200px] w-full max-w-md flex items-start justify-center">
        {loading ? (
          <div className="grid grid-cols-1 gap-3 w-full">
            {Array.from({ length: 3 }).map((_, i) => (
              <div
                key={i}
                className="flex items-center gap-3 p-4 rounded-lg border bg-card opacity-0"
              >
                <span className="text-xl">{"\uD83D\uDCE6"}</span>
                <div>
                  <h2 className="text-sm font-semibold">Loading</h2>
                  <p className="text-xs text-muted-foreground">Loading...</p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3 w-full">
            {pages.map((page) => (
              <Link
                key={page.href}
                to={page.href}
                className="flex items-center gap-3 p-4 rounded-lg border bg-card hover:bg-accent transition-colors"
              >
                <span className="text-xl">{page.emoji}</span>
                <div>
                  <h2 className="text-sm font-semibold">{page.title}</h2>
                  <p className="text-xs text-muted-foreground">
                    {page.description}
                  </p>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
