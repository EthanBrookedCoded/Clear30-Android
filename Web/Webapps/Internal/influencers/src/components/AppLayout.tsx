import { Outlet } from "react-router-dom";
import { SidebarInset, SidebarTrigger, SidebarProvider } from "@/components/ui/sidebar";
import { AppSidebar } from "./AppSidebar";
import { useAuth } from "@/lib/auth-provider";
import { cn } from "@/lib/utils";

export function AppLayout() {
  const { myAccountsOnly, setMyAccountsOnly, role } = useAuth();
  const isAdmin = role === "admin";

  return (
    <SidebarProvider defaultOpen={true}>
      <AppSidebar />
      <SidebarInset>
        <header className="flex items-center justify-between gap-2 border-b px-4 py-3">
          <div className="flex items-center gap-2">
            <SidebarTrigger />
            <span className="font-semibold md:hidden">Clear30 Influencers</span>
          </div>
          {isAdmin && (
            <button
              onClick={() => setMyAccountsOnly(!myAccountsOnly)}
              className={cn(
                "inline-flex items-center px-3 py-1.5 rounded-full text-xs font-medium border transition-colors",
                myAccountsOnly
                  ? "bg-blue-100 text-blue-700 border-blue-300 hover:bg-blue-200"
                  : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100"
              )}
            >
              My Accounts{myAccountsOnly ? ": On" : ""}
            </button>
          )}
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
