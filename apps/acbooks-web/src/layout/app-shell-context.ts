import { createContext, useContext, type RefObject } from "react";

export interface AppShellContextValue {
  accountName: string;
  closeNavigation: () => void;
  expireSession: () => void;
  loggingOut: boolean;
  logoutError?: string;
  mainRef: RefObject<HTMLElement | null>;
  navigationOpen: boolean;
  onLogout: () => void;
  openNavigation: () => void;
  pathname: string;
  search: string;
}

export const AppShellContext =
  createContext<AppShellContextValue | null>(null);

export function useAppShell(): AppShellContextValue {
  const context = useContext(AppShellContext);
  if (context === null) {
    throw new Error("useAppShell must be used within AppShell.");
  }
  return context;
}
