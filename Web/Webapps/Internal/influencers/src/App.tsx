import { Routes, Route } from "react-router-dom";
import { AuthProvider } from "@/lib/auth-provider";
import { RequireRole } from "@/lib/require-role";
import { AppLayout } from "@/components/AppLayout";
import HomePage from "@/pages/Home";
import LoginPage from "@/pages/Login";
import AuthConfirmPage from "@/pages/AuthConfirm";
import SettingsPage from "@/pages/Settings";
import AdminPage from "@/pages/Admin";
import OutreachPage from "@/pages/creators/Outreach";
import QuickSubmitPage from "@/pages/creators/QuickSubmit";
import AddCreatorPage from "@/pages/creators/AddCreator";

export function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* Public routes — no sidebar */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/auth/confirm" element={<AuthConfirmPage />} />

        {/* Protected routes — with sidebar layout */}
        <Route element={<AppLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/settings" element={<SettingsPage />} />

          {/* Requires va or admin */}
          <Route element={<RequireRole allowedRoles={["va", "admin"]} />}>
            <Route path="/creators/outreach" element={<OutreachPage />} />
            <Route path="/creators/outreach/quick" element={<QuickSubmitPage />} />
            <Route path="/creators/add" element={<AddCreatorPage />} />
          </Route>

          {/* Requires admin */}
          <Route element={<RequireRole allowedRoles={["admin"]} />}>
            <Route path="/admin" element={<AdminPage />} />
          </Route>
        </Route>
      </Routes>
    </AuthProvider>
  );
}
