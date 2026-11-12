import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate, useNavigationType } from "react-router-dom";

import { SidebarContent } from "./AppSidebar";
import { AppShellContext } from "./app-shell-context";
import { MobileNavigation } from "./MobileNavigation";
import {
  readScrollPosition,
  saveScrollPosition,
} from "./scroll-restoration";
import { useMediaQuery } from "./use-media-query";

export interface AppShellProps {
  accountName: string;
  children: ReactNode;
  expireSession: () => void;
  loggingOut: boolean;
  logoutError?: string;
  onLogout: () => void;
}

function isDrawerEntry(state: unknown): boolean {
  return (
    typeof state === "object" &&
    state !== null &&
    (state as { acbooksDrawer?: unknown }).acbooksDrawer === true
  );
}

function withDrawerEntry(state: unknown): Record<string, unknown> {
  return {
    ...(typeof state === "object" && state !== null ? state : {}),
    acbooksDrawer: true,
  };
}

const SCROLL_SAVE_INTERVAL = 150;

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
  const navigate = useNavigate();
  const navigationType = useNavigationType();
  const [focusSearchOnOpen, setFocusSearchOnOpen] = useState(false);
  const mainRef = useRef<HTMLElement>(null);
  const desktopSearchRef = useRef<HTMLInputElement>(null);
  const lastScrollSave = useRef(0);
  const previousPathname = useRef(location.pathname);
  const isDesktop = useMediaQuery("(min-width: 1024px)");
  const pathname = location.pathname;
  const search = location.search;
  const settingsMode = pathname === "/settings";
  const drawerOpen = isDrawerEntry(location.state);
  const navigationOpen = !isDesktop && drawerOpen;

  const dismissNavigation = useCallback(() => {
    setFocusSearchOnOpen(false);
  }, []);

  const closeNavigation = useCallback(() => {
    setFocusSearchOnOpen(false);
    if (drawerOpen) {
      navigate(-1);
    }
  }, [drawerOpen, navigate]);

  const openNavigation = useCallback(() => {
    if (isDesktop || drawerOpen) {
      return;
    }
    navigate(`${location.pathname}${location.search}${location.hash}`, {
      state: withDrawerEntry(location.state),
    });
  }, [drawerOpen, isDesktop, location, navigate]);

  const handleFocusSearchHandled = useCallback(() => {
    setFocusSearchOnOpen(false);
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
    const main = mainRef.current;
    if (main === null) {
      return;
    }
    const handleScroll = () => {
      const now = Date.now();
      if (now - lastScrollSave.current < SCROLL_SAVE_INTERVAL) {
        return;
      }
      lastScrollSave.current = now;
      saveScrollPosition(location, main.scrollTop);
    };
    main.addEventListener("scroll", handleScroll, { passive: true });
    return () => {
      main.removeEventListener("scroll", handleScroll);
    };
  }, [location]);

  useEffect(() => {
    const main = mainRef.current;
    const changedPath = previousPathname.current !== location.pathname;
    previousPathname.current = location.pathname;
    if (main === null) {
      return;
    }
    const frame = window.requestAnimationFrame(() => {
      if (navigationType === "POP") {
        main.scrollTo({ top: readScrollPosition(location) });
        return;
      }
      if (changedPath) {
        main.scrollTo({ top: 0 });
      }
    });
    return () => {
      window.cancelAnimationFrame(frame);
    };
  }, [location, navigationType]);

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
          replaceNavigation={navigationOpen}
          search={search}
        />

        <main
          className="min-h-0 flex-1 overflow-y-auto bg-background p-4 [scrollbar-gutter:stable] md:p-5 lg:m-2 lg:rounded-panel lg:p-6 2xl:p-8"
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
