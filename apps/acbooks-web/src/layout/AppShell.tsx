import type { ReactNode, RefObject } from "react";

export interface AppShellProps {
  children: ReactNode;
  header: ReactNode;
  mainRef?: RefObject<HTMLElement | null>;
  sidebar: ReactNode;
}

export function AppShell({
  children,
  header,
  mainRef,
  sidebar,
}: AppShellProps) {
  return (
    <div className="flex h-dvh overflow-hidden bg-frame text-foreground">
      {sidebar}
      <div className="flex min-w-0 flex-1 flex-col">
        {header}
        <main
          className="m-0 mr-2 mb-2 flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto rounded-panel bg-background p-6 max-md:m-0 max-md:rounded-none max-md:p-4"
          ref={mainRef}
        >
          {children}
        </main>
      </div>
    </div>
  );
}
