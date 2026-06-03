import { useAuth } from "@/lib/auth-provider";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarHeader,
  SidebarFooter,
  SidebarTrigger,
  useSidebar,
} from "@/components/ui/sidebar";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  Home,
  Settings,
  List,
  Plus,
  ChevronRight,
  Shield,
  LogOut,
} from "lucide-react";
import { Link, useLocation } from "react-router-dom";

const creatorsItems = [
  {
    title: "Outreach",
    url: "/creators/outreach",
    icon: List,
  },
  {
    title: "Add",
    url: "/creators/add",
    icon: Plus,
  },
];

export function AppSidebar() {
  const location = useLocation();
  const pathname = location.pathname;
  const { isMobile, setOpenMobile } = useSidebar();
  const { role, signOut } = useAuth();

  const closeSidebarOnMobile = () => {
    if (isMobile) {
      setOpenMobile(false);
    }
  };

  const handleSignOut = async () => {
    await signOut();
  };

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader className="border-b px-6 py-4 group-data-[collapsible=icon]:hidden">
        <h2 className="text-lg font-semibold whitespace-nowrap">
          Clear30 Influencers
        </h2>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton
                  asChild
                  isActive={pathname === "/"}
                  onClick={closeSidebarOnMobile}
                >
                  <Link to="/">
                    <Home />
                    <span>Home</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <Collapsible defaultOpen className="group/collapsible">
          <SidebarGroup>
            <SidebarGroupLabel asChild>
              <CollapsibleTrigger className="flex w-full items-center">
                Creators
                <ChevronRight className="ml-auto h-4 w-4 transition-transform group-data-[state=open]/collapsible:rotate-90" />
              </CollapsibleTrigger>
            </SidebarGroupLabel>
            <CollapsibleContent>
              <SidebarGroupContent>
                <SidebarMenu>
                  {creatorsItems.map((item) => (
                    <SidebarMenuItem key={item.title}>
                      <SidebarMenuButton
                        asChild
                        isActive={pathname.startsWith(item.url)}
                        onClick={closeSidebarOnMobile}
                      >
                        <Link to={item.url}>
                          <item.icon />
                          <span>{item.title}</span>
                        </Link>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </CollapsibleContent>
          </SidebarGroup>
        </Collapsible>

        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton
                  asChild
                  isActive={pathname.startsWith("/settings")}
                  onClick={closeSidebarOnMobile}
                >
                  <Link to="/settings">
                    <Settings />
                    <span>Settings</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              {role === "admin" && (
                <SidebarMenuItem>
                  <SidebarMenuButton
                    asChild
                    isActive={pathname.startsWith("/admin")}
                    onClick={closeSidebarOnMobile}
                  >
                    <Link to="/admin">
                      <Shield />
                      <span>Admin</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              )}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter className="border-t">
        <div className="flex items-center justify-between px-2 py-2 group-data-[collapsible=icon]:justify-center">
          <button
            onClick={handleSignOut}
            className="flex items-center gap-2 text-xs text-muted-foreground hover:text-foreground transition-colors group-data-[collapsible=icon]:hidden"
          >
            <LogOut className="h-3 w-3" />
            <span>Sign Out</span>
          </button>
          <SidebarTrigger className="group-data-[collapsible=icon]:mx-auto" />
        </div>
      </SidebarFooter>
    </Sidebar>
  );
}
