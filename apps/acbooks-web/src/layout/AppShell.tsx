import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useTranslation } from "react-i18next";
import { useLocation } from "react-router-dom";

import { SidebarContent } from "./AppSidebar";
import { AppShellContext } from "./app-shell-context";
import { MobileNavigation } from "./MobileNavigation";
import { useMediaQuery } from "./use-media-query";

export interface AppShellProps {
  accountName: string;
  children: ReactNode;
  expireSession: () => void;
  loggingOut: boolean;
  logoutError?: string;
  onLogout: () => void;
}

export function AppShell({
  accountName,
  children,
  expireSession,
  loggingOut,
  logoutError,
  onLogout,
}: AppShellProps) {
  const { t } = useTranslation("app");
  const location = useLocation();
  const [navigationOpen, setNavigationOpen] = useState(false);
  const [focusSearchOnOpen, setFocusSearchOnOpen] = useState(false);
  const mainRef = useRef<HTMLElement>(null);
  const desktopSearchRef = useRef<HTMLInputElement>(null);
  const isDesktop = useMediaQuery("(min-width: 1024px)");
  const pathname = location.pathname;
  const search = location.search;
  const settingsMode = pathname === "/settings";

  const dismissNavigation = useCallback(() => {
    setNavigationOpen(false);
    setFocusSearchOnOpen(false);
  }, []);

  const closeNavigation = useCallback(() => {
    setNavigationOpen(false);
    setFocusSearchOnOpen(false);
    if (window.history.state?.acbooksDrawer === true) {
      window.history.back();
    }
  }, []);

  const openNavigation = useCallback(() => {
    if (isDesktop || navigationOpen) {
      return;
    }
    if (window.history.state?.acbooksDrawer !== true) {
      window.history.pushState(
        { ...window.history.state, acbooksDrawer: true },
        "",
      );
    }
    setNavigationOpen(true);
  }, [isDesktop, navigationOpen]);

  const handleFocusSearchHandled = useCallback(() => {
    setFocusSearchOnOpen(false);
  }, []);

  useEffect(() => {
    const closeDrawer = () => {
      setNavigationOpen(false);
      setFocusSearchOnOpen(false);
    };
    window.addEventListener("popstate", closeDrawer);
    return () => {
      window.removeEventListener("popstate", closeDrawer);
    };
  }, []);

  useEffect(() => {
    if (typeof window.matchMedia !== "function") {
      return;
    }
    const media = window.matchMedia("(min-width: 1024px)");
    const handleChange = (event: MediaQueryListEvent) => {
      if (event.matches) {
        closeNavigation();
      }
    };
    media.addEventListener("change", handleChange);
    return () => {
      media.removeEventListener("change", handleChange);
    };
  }, [closeNavigation]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (
        settingsMode ||
        !(event.ctrlKey || event.metaKey) ||
        event.key.toLowerCase() !== "k"
      ) {
        return;
      }
      event.preventDefault();
      if (isDesktop) {
        desktopSearchRef.current?.focus();
      } else {
        setFocusSearchOnOpen(true);
        openNavigation();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isDesktop, openNavigation, settingsMode]);

  const contextValue = useMemo(
    () => ({
      accountName,
      closeNavigation,
      expireSession,
      loggingOut,
      logoutError,
      mainRef,
      navigationOpen,
      onLogout,
      openNavigation,
      pathname,
      search,
    }),
    [
      accountName,
      closeNavigation,
      expireSession,
      loggingOut,
      logoutError,
      navigationOpen,
      onLogout,
      openNavigation,
      pathname,
      search,
    ],
  );

  return (
    <AppShellContext.Provider value={contextValue}>
      <div className="flex h-dvh overflow-hidden bg-frame text-foreground">
        <aside
          aria-label={t("nav.sidebar")}
          className="hidden w-[clamp(236px,16vw,264px)] shrink-0 flex-col lg:flex"
        >
          <SidebarContent
            accountName={accountName}
            loggingOut={loggingOut}
            logoutError={logoutError}
            onLogout={onLogout}
            pathname={pathname}
            search={search}
            searchInputRef={desktopSearchRef}
            variant="desktop"
          />
        </aside>

        <MobileNavigation
          accountName={accountName}
          focusSearchOnOpen={focusSearchOnOpen}
          loggingOut={loggingOut}
          logoutError={logoutError}
          onClose={closeNavigation}
          onFocusSearchHandled={handleFocusSearchHandled}
          onLogout={onLogout}
          onNavigate={dismissNavigation}
          open={navigationOpen}
          pathname={pathname}
          search={search}
        />

        <main
          className="min-h-0 flex-1 overflow-y-auto bg-background p-4 md:p-5 lg:m-2 lg:rounded-panel lg:p-6 2xl:p-8"
          ref={mainRef}
        >
          <div className="mx-auto flex min-h-full w-full max-w-[1280px] flex-col gap-6">
            {children}
          </div>
        </main>
      </div>
    </AppShellContext.Provider>
  );
}
