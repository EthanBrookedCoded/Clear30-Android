import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  ReactNode,
} from "react";
import { createClient } from "@/lib/supabase/client";
import { useNavigate, useLocation } from "react-router-dom";
import type { User } from "@supabase/supabase-js";

type UserRole = "va" | "admin" | null;

interface UserAccountIds {
  instagramId: string | null;
  tiktokId: string | null;
}

interface AuthContextType {
  user: User | null;
  role: UserRole;
  loading: boolean;
  signOut: () => Promise<void>;
  userAccountIds: UserAccountIds;
  myAccountsOnly: boolean;
  setMyAccountsOnly: (v: boolean) => void;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  role: null,
  loading: true,
  signOut: async () => {},
  userAccountIds: { instagramId: null, tiktokId: null },
  myAccountsOnly: true,
  setMyAccountsOnly: () => {},
});

export function useAuth() {
  return useContext(AuthContext);
}

const PUBLIC_ROUTES = ["/login", "/auth/confirm"];

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [role, setRole] = useState<UserRole>(null);
  const [loading, setLoading] = useState(true);
  const [userAccountIds, setUserAccountIds] = useState<UserAccountIds>({ instagramId: null, tiktokId: null });
  const [myAccountsOnly, setMyAccountsOnly] = useState(true);
  const navigate = useNavigate();
  const location = useLocation();
  const supabase = createClient();

  useEffect(() => {
    let cancelled = false;
    let initialized = false;

    const fetchRole = async (userId: string) => {
      const { data: profile } = await supabase
        .from("profiles")
        .select("role, instagram_outreach_account_id, tiktok_outreach_account_id")
        .eq("auth_user_id", userId)
        .single();
      if (!cancelled) {
        setRole((profile?.role as UserRole) ?? null);
        setUserAccountIds({
          instagramId: profile?.instagram_outreach_account_id ?? null,
          tiktokId: profile?.tiktok_outreach_account_id ?? null,
        });
        // Force VAs to always have myAccountsOnly = true
        if ((profile?.role as UserRole) === "va") {
          setMyAccountsOnly(true);
        }
      }
    };

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, session) => {
      const newUser = session?.user ?? null;
      setUser(newUser);

      if (newUser) {
        fetchRole(newUser.id);
      } else {
        setRole(null);
      }

      if (!initialized) {
        initialized = true;
        if (!cancelled) {
          setLoading(false);
        }
      }
    });

    // Safety timeout - if onAuthStateChange never fires, stop loading
    const timeout = setTimeout(() => {
      if (!initialized && !cancelled) {
        initialized = true;
        setLoading(false);
      }
    }, 5000);

    return () => {
      cancelled = true;
      clearTimeout(timeout);
      subscription.unsubscribe();
    };
  }, [supabase]);

  // Redirect logic (replaces middleware)
  useEffect(() => {
    if (loading) return;

    const isPublic = PUBLIC_ROUTES.some((r) => location.pathname.startsWith(r));

    if (!user && !isPublic) {
      navigate("/login", { replace: true });
    } else if (user && location.pathname === "/login") {
      const params = new URLSearchParams(window.location.search);
      if (params.get("mode") !== "reset") {
        navigate("/", { replace: true });
      }
    }
  }, [user, loading, location.pathname, navigate]);

  const signOut = useCallback(async () => {
    await supabase.auth.signOut();
    setUser(null);
    setRole(null);
    navigate("/login");
  }, [supabase, navigate]);

  const guardedSetMyAccountsOnly = useCallback((v: boolean) => {
    if (role === "va") return; // VAs are locked to myAccountsOnly = true
    setMyAccountsOnly(v);
  }, [role]);

  return (
    <AuthContext.Provider value={{ user, role, loading, signOut, userAccountIds, myAccountsOnly, setMyAccountsOnly: guardedSetMyAccountsOnly }}>
      {children}
    </AuthContext.Provider>
  );
}
