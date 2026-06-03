import { useAuth } from "@/lib/auth-provider";
import { useNavigate, Outlet } from "react-router-dom";
import { useEffect } from "react";
import { Loader2 } from "lucide-react";

interface RequireRoleProps {
  allowedRoles: ("va" | "admin")[];
}

export function RequireRole({ allowedRoles }: RequireRoleProps) {
  const { user, role, loading } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    // Only redirect if auth is done loading AND role has resolved (non-null)
    if (!loading && role && !allowedRoles.includes(role)) {
      navigate("/", { replace: true });
    }
  }, [loading, role, allowedRoles, navigate]);

  // Auth still loading
  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  // User is authenticated but role hasn't loaded yet — wait
  if (user && !role) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
      </div>
    );
  }

  // Role loaded but not authorized
  if (!role || !allowedRoles.includes(role)) {
    return null;
  }

  return <Outlet />;
}
