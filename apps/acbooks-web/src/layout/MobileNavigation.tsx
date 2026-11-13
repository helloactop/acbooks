import {
  useEffect,
  useRef,
  type TouchEvent as ReactTouchEvent,
} from "react";
import { useTranslation } from "react-i18next";

import { AppSidebar, type AppSidebarProps } from "./AppSidebar";

const EDGE_ZONE = 28;
const SWIPE_DISTANCE = 56;
const HORIZONTAL_RATIO = 1.5;
const VERTICAL_CANCEL = 10;

interface SwipeStart {
  x: number;
  y: number;
}

function isFormField(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.tagName === "INPUT" ||
      target.tagName === "TEXTAREA" ||
      target.tagName === "SELECT")
  );
}

function hasHorizontalScroll(target: EventTarget | null): boolean {
  let node = target instanceof Element ? target : null;
  while (node !== null && node !== document.body) {
    const style = window.getComputedStyle(node);
    if (
      (style.overflowX === "auto" || style.overflowX === "scroll") &&
      node.scrollWidth > node.clientWidth + 1
    ) {
      return true;
    }
    node = node.parentElement;
  }
  return false;
}

export interface MobileNavigationProps extends AppSidebarProps {
  onOpen: () => void;
  open: boolean;
}

export function MobileNavigation({
  onClose,
  onOpen,
  open,
  ...contentProps
}: MobileNavigationProps) {
  const { t } = useTranslation("app");
  const dialogRef = useRef<HTMLDialogElement>(null);
  const edgeSwipe = useRef<SwipeStart | null>(null);
  const closeSwipe = useRef<SwipeStart | null>(null);

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

  useEffect(() => {
    if (open) {
      return;
    }
    const handleStart = (event: TouchEvent) => {
      const touch = event.touches[0];
      if (
        event.touches.length !== 1 ||
        touch === undefined ||
        isFormField(event.target) ||
        hasHorizontalScroll(event.target) ||
        touch.clientX > EDGE_ZONE
      ) {
        edgeSwipe.current = null;
        return;
      }
      edgeSwipe.current = { x: touch.clientX, y: touch.clientY };
    };
    const handleMove = (event: TouchEvent) => {
      const start = edgeSwipe.current;
      const touch = event.touches[0];
      if (start === null || touch === undefined) {
        return;
      }
      const dx = touch.clientX - start.x;
      const dy = touch.clientY - start.y;
      if (
        Math.abs(dy) > VERTICAL_CANCEL &&
        Math.abs(dy) > Math.abs(dx) * HORIZONTAL_RATIO
      ) {
        edgeSwipe.current = null;
        return;
      }
      if (dx >= SWIPE_DISTANCE && dx > Math.abs(dy) * HORIZONTAL_RATIO) {
        edgeSwipe.current = null;
        onOpen();
      }
    };
    const handleEnd = () => {
      edgeSwipe.current = null;
    };
    document.addEventListener("touchstart", handleStart, { passive: true });
    document.addEventListener("touchmove", handleMove, { passive: true });
    document.addEventListener("touchend", handleEnd);
    document.addEventListener("touchcancel", handleEnd);
    return () => {
      document.removeEventListener("touchstart", handleStart);
      document.removeEventListener("touchmove", handleMove);
      document.removeEventListener("touchend", handleEnd);
      document.removeEventListener("touchcancel", handleEnd);
    };
  }, [onOpen, open]);

  const handleTouchStart = (event: ReactTouchEvent<HTMLDialogElement>) => {
    const touch = event.touches[0];
    if (
      event.touches.length !== 1 ||
      touch === undefined ||
      isFormField(event.target)
    ) {
      closeSwipe.current = null;
      return;
    }
    closeSwipe.current = { x: touch.clientX, y: touch.clientY };
  };

  const handleTouchMove = (event: ReactTouchEvent<HTMLDialogElement>) => {
    const start = closeSwipe.current;
    const touch = event.touches[0];
    if (start === null || touch === undefined) {
      return;
    }
    const dx = touch.clientX - start.x;
    const dy = touch.clientY - start.y;
    if (
      Math.abs(dy) > VERTICAL_CANCEL &&
      Math.abs(dy) > Math.abs(dx) * HORIZONTAL_RATIO
    ) {
      closeSwipe.current = null;
      return;
    }
    if (dx <= -SWIPE_DISTANCE && -dx > Math.abs(dy) * HORIZONTAL_RATIO) {
      closeSwipe.current = null;
      onClose?.();
    }
  };

  return (
    <dialog
      aria-label={t("nav.sidebar")}
      className="fixed inset-y-0 left-0 m-0 h-dvh max-h-none w-[min(82vw,280px)] max-w-none rounded-none border-0 bg-background p-0 text-foreground shadow-overlay backdrop:bg-black/40 [&[open]]:animate-[acbooks-drawer-in_240ms_ease-out]"
      id="app-navigation-drawer"
      onCancel={(event) => {
        event.preventDefault();
        onClose?.();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose?.();
        }
      }}
      onTouchCancel={() => {
        closeSwipe.current = null;
      }}
      onTouchEnd={() => {
        closeSwipe.current = null;
      }}
      onTouchMove={handleTouchMove}
      onTouchStart={handleTouchStart}
      ref={dialogRef}
    >
      <div className="h-full [padding-bottom:env(safe-area-inset-bottom)] [padding-left:env(safe-area-inset-left)] [padding-top:env(safe-area-inset-top)]">
        <AppSidebar {...contentProps} onClose={onClose} />
      </div>
    </dialog>
  );
}
