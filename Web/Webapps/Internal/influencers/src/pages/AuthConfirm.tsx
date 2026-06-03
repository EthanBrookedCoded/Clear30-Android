import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createClient } from "@/lib/supabase/client";
import { Loader2 } from "lucide-react";
import type { EmailOtpType } from "@supabase/supabase-js";

export default function AuthConfirmPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const tokenHash = searchParams.get("token_hash");
    const type = searchParams.get("type") as EmailOtpType | null;

    if (!tokenHash || !type) {
      navigate("/login?error=invalid_token", { replace: true });
      return;
    }

    const verify = async () => {
      const supabase = createClient();
      const { error } = await supabase.auth.verifyOtp({
        type,
        token_hash: tokenHash,
      });

      if (error) {
        navigate("/login?error=invalid_token", { replace: true });
        return;
      }

      if (type === "recovery") {
        navigate("/login?mode=reset", { replace: true });
      } else {
        navigate("/", { replace: true });
      }
    };

    verify();
  }, [searchParams, navigate]);

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p className="text-red-500 text-sm">{error}</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center">
      <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
    </div>
  );
}
