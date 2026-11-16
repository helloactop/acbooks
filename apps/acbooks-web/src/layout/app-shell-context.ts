import { createContext, useContext, type RefObject } from "react";

import type { Account } from "../auth/auth-api";

export interface AppShellContextValue {
  account: Account;
  accountName: string;
  avatarUrl: string | null;
  closeNavigation: () => void;
  expireSession: () => void;
  loggingOut: boolean;
  logoutError?: string;
  mainRef: RefObject<HTMLElement | null>;
  navigationOpen: boolean;
  onAccountChange: (account: Account) => void;
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
