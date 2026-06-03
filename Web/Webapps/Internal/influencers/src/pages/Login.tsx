import { useState, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createClient } from "@/lib/supabase/client";

type PageMode = "login" | "forgot" | "forgot-sent" | "reset" | "reset-success";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState<PageMode>("login");
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const supabase = createClient();

  useEffect(() => {
    const modeParam = searchParams.get("mode");
    if (modeParam === "reset") {
      setMode("reset");
      return;
    }

    const errorParam = searchParams.get("error");
    if (errorParam === "invalid_token") {
      setError(
        "Password reset link is invalid or has expired. Please request a new one.",
      );
      return;
    }

    const hash = window.location.hash.substring(1);
    const params = new URLSearchParams(hash);
    const type = params.get("type");

    if (type === "recovery") {
      setMode("reset");
    }
  }, [searchParams]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    const { error } = await supabase.auth.signInWithPassword({
      email,
      password,
    });

    if (error) {
      setError(error.message);
      setLoading(false);
    } else {
      navigate("/");
    }
  };

  const handleForgotPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    const { error } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo: `${window.location.origin}/influencers/auth/confirm`,
    });

    if (error) {
      setError(error.message);
      setLoading(false);
    } else {
      setMode("forgot-sent");
      setLoading(false);
    }
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (password.length < 6) {
      setError("Password must be at least 6 characters");
      return;
    }

    if (password !== confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    setLoading(true);

    const { error } = await supabase.auth.updateUser({ password });

    if (error) {
      setError(error.message);
      setLoading(false);
    } else {
      setMode("reset-success");
    }
  };

  // Reset success
  if (mode === "reset-success") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white pb-20">
        <div className="w-full max-w-xs text-center">
          <h1 className="text-lg font-medium text-neutral-900 mb-4">
            Password Updated
          </h1>
          <p className="text-sm text-neutral-600 mb-6">
            Your password has been successfully updated.
          </p>
          <button
            onClick={() => {
              navigate("/");
            }}
            className="w-full font-medium py-2 text-sm bg-neutral-900 text-white rounded-md hover:bg-neutral-800 transition-colors"
          >
            Continue to App
          </button>
        </div>
      </div>
    );
  }

  // Reset password form
  if (mode === "reset") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white pb-20">
        <div className="w-full max-w-xs">
          <h1 className="text-lg font-medium text-neutral-900 mb-6">
            Reset Password
          </h1>
          <form onSubmit={handleResetPassword} className="space-y-3">
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="New password"
              className="w-full px-3 py-2 text-sm border border-neutral-200 rounded-md focus:outline-none focus:border-neutral-400 transition-colors"
              autoFocus
            />
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="Confirm password"
              className="w-full px-3 py-2 text-sm border border-neutral-200 rounded-md focus:outline-none focus:border-neutral-400 transition-colors"
            />
            <button
              type="submit"
              disabled={loading}
              className="w-full font-medium py-2 text-sm bg-neutral-900 text-white rounded-md hover:bg-neutral-800 disabled:opacity-50 transition-colors"
            >
              {loading ? "Updating..." : "Update Password"}
            </button>
            <div className="h-4">
              {error && <p className="text-red-500 text-xs">{error}</p>}
            </div>
          </form>
        </div>
      </div>
    );
  }

  // Forgot password email sent
  if (mode === "forgot-sent") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white pb-20">
        <div className="w-full max-w-xs text-center">
          <h1 className="text-lg font-medium text-neutral-900 mb-4">
            Check Your Email
          </h1>
          <p className="text-sm text-neutral-600 mb-6">
            We sent a password reset link to {email}
          </p>
          <button
            onClick={() => {
              setMode("login");
              setEmail("");
              setError("");
            }}
            className="text-sm text-neutral-500 hover:text-neutral-700 transition-colors"
          >
            Back to login
          </button>
        </div>
      </div>
    );
  }

  // Forgot password form
  if (mode === "forgot") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white pb-20">
        <div className="w-full max-w-xs">
          <h1 className="text-lg font-medium text-neutral-900 mb-6">
            Forgot Password
          </h1>
          <form onSubmit={handleForgotPassword} className="space-y-3">
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="Email"
              className="w-full px-3 py-2 text-sm border border-neutral-200 rounded-md focus:outline-none focus:border-neutral-400 transition-colors"
              autoFocus
            />
            <button
              type="submit"
              disabled={loading}
              className="w-full font-medium py-2 text-sm bg-neutral-900 text-white rounded-md hover:bg-neutral-800 disabled:opacity-50 transition-colors"
            >
              {loading ? "Sending..." : "Send Reset Link"}
            </button>
            <div className="h-4">
              {error && <p className="text-red-500 text-xs">{error}</p>}
            </div>
            <button
              type="button"
              onClick={() => {
                setMode("login");
                setError("");
              }}
              className="w-full text-sm text-neutral-500 hover:text-neutral-700 transition-colors"
            >
              Back to login
            </button>
          </form>
        </div>
      </div>
    );
  }

  // Login form (default)
  return (
    <div className="min-h-screen flex items-center justify-center bg-white pb-20">
      <div className="w-full max-w-xs">
        <h1 className="text-lg font-medium text-neutral-900 mb-6">
          Clear30 Influencers
        </h1>
        <form onSubmit={handleLogin} className="space-y-3">
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Email"
            className="w-full px-3 py-2 text-sm border border-neutral-200 rounded-md focus:outline-none focus:border-neutral-400 transition-colors"
            autoFocus
          />
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Password"
            className="w-full px-3 py-2 text-sm border border-neutral-200 rounded-md focus:outline-none focus:border-neutral-400 transition-colors"
          />
          <button
            type="submit"
            disabled={loading}
            className="w-full font-medium py-2 text-sm bg-neutral-900 text-white rounded-md hover:bg-neutral-800 disabled:opacity-50 transition-colors"
          >
            {loading ? "Signing in..." : "Sign In"}
          </button>
          <div className="h-4">
            {error && <p className="text-red-500 text-xs">{error}</p>}
          </div>
          <button
            type="button"
            onClick={() => {
              setMode("forgot");
              setError("");
            }}
            className="w-full text-sm text-neutral-500 hover:text-neutral-700 transition-colors"
          >
            Forgot password?
          </button>
        </form>
      </div>
    </div>
  );
}
