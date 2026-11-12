import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";

import { SidebarContent, type SidebarContentProps } from "./AppSidebar";

export interface MobileNavigationProps
  extends Omit<SidebarContentProps, "variant"> {
  onClose: () => void;
  open: boolean;
}

export function MobileNavigation({
  onClose,
  open,
  ...contentProps
}: MobileNavigationProps) {
  const { t } = useTranslation("app");
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog === null) {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      aria-label={t("nav.sidebar")}
      className="fixed inset-y-0 left-0 m-0 h-dvh max-h-none w-[min(82vw,320px)] max-w-none rounded-none border-0 bg-background p-0 text-foreground shadow-overlay backdrop:bg-black/40 [&[open]]:animate-[acbooks-drawer-in_240ms_ease-out]"
      id="app-navigation-drawer"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
      ref={dialogRef}
    >
      <div className="h-full">
        <SidebarContent
          {...contentProps}
          onCloseNavigation={onClose}
          variant="mobile"
        />
      </div>
    </dialog>
  );
}
