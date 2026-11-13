import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useLocation, useNavigate, useNavigationType } from "react-router-dom";

import { AppRail } from "./AppRail";
import { AppShellContext } from "./app-shell-context";
import { AppTopBar } from "./AppTopBar";
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

const SCROLL_SAVE_INTERVAL = 150;

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

export function AppShell({
  accountName,
  children,
  expireSession,
  loggingOut,
  logoutError,
  onLogout,
}: AppShellProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const navigationType = useNavigationType();
  const [focusSearchOnOpen, setFocusSearchOnOpen] = useState(false);
  const pendingSearchFocus = useRef(false);
  const mainRef = useRef<HTMLElement>(null);
  const desktopSearchRef = useRef<HTMLInputElement>(null);
  const lastScrollSave = useRef(0);
  const previousPathname = useRef(location.pathname);
  const isTabletUp = useMediaQuery("(min-width: 768px)");
  const pathname = location.pathname;
  const search = location.search;
  const drawerOpen = isDrawerEntry(location.state);
  const navigationOpen = !isTabletUp && drawerOpen;

  const closeNavigation = useCallback(() => {
    setFocusSearchOnOpen(false);
    if (drawerOpen) {
      navigate(-1);
    }
  }, [drawerOpen, navigate]);

  const openNavigation = useCallback(() => {
    if (isTabletUp || drawerOpen) {
      return;
    }
    navigate(`${location.pathname}${location.search}${location.hash}`, {
      state: withDrawerEntry(location.state),
    });
  }, [drawerOpen, isTabletUp, location, navigate]);

  const handleFocusSearchHandled = useCallback(() => {
    setFocusSearchOnOpen(false);
  }, []);

  useEffect(() => {
    if (isTabletUp && drawerOpen) {
      navigate(-1);
    }
  }, [drawerOpen, isTabletUp, navigate]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (
        !(event.ctrlKey || event.metaKey) ||
        event.key.toLowerCase() !== "k"
      ) {
        return;
      }
      event.preventDefault();
      if (isTabletUp) {
        desktopSearchRef.current?.focus();
      } else {
        // Opening the drawer commits on a later router update; requesting focus
        // in the same commit lets showModal() move focus back to the brand link.
        pendingSearchFocus.current = true;
        openNavigation();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isTabletUp, openNavigation]);

  useEffect(() => {
    if (!navigationOpen || !pendingSearchFocus.current) {
      return;
    }
    pendingSearchFocus.current = false;
    setFocusSearchOnOpen(true);
  }, [navigationOpen]);

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
        {isTabletUp ? <AppRail pathname={pathname} /> : null}

        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          {isTabletUp ? (
            <AppTopBar
              accountName={accountName}
              searchInputRef={desktopSearchRef}
            />
          ) : null}

          <main
            className="m-2 min-h-0 flex-1 overflow-y-auto rounded-panel bg-background p-2 md:px-5 md:py-4"
            ref={mainRef}
          >
            <div className="flex min-h-full w-full flex-col gap-4">
              {children}
            </div>
          </main>
        </div>

        {!isTabletUp ? (
          <MobileNavigation
            accountName={accountName}
            focusSearchOnOpen={focusSearchOnOpen}
            onClose={closeNavigation}
            onFocusSearchHandled={handleFocusSearchHandled}
            onOpen={openNavigation}
            open={navigationOpen}
            pathname={pathname}
            replaceNavigation={navigationOpen}
          />
        ) : null}
      </div>
    </AppShellContext.Provider>
  );
}
