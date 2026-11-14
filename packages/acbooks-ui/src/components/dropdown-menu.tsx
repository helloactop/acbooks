import type { LucideIcon } from "lucide-react";
import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";

import { cn } from "../lib/utils";
import { resolveOverlayContainer } from "../lib/overlay-container";
import { IconButton } from "./icon-button";
import type { ButtonProps } from "./button";

export interface DropdownMenuAction {
  danger?: boolean;
  disabled?: boolean;
  icon?: LucideIcon;
  label: string;
  onSelect: () => void;
}

export interface DropdownMenuProps {
  actions: readonly DropdownMenuAction[];
  align?: "start" | "end";
  children?: ReactNode;
  className?: string;
  disabled?: boolean;
  heading?: string;
  label: string;
  side?: "bottom" | "top";
  trigger?: ReactNode;
  triggerClassName?: string;
  triggerVariant?: ButtonProps["variant"];
}

interface MenuPosition {
  bottom?: number;
  left: number;
  maxHeight: number;
  top?: number;
  width: number;
}

const VIEWPORT_PADDING = 12;
const MENU_GAP = 8;
const MENU_MAX_HEIGHT = 320;
const MENU_MIN_WIDTH = 176;
const MENU_MIN_SPACE = 176;

export function DropdownMenu({
  actions,
  align = "end",
  children,
  className,
  disabled = false,
  heading,
  label,
  side = "bottom",
  trigger,
  triggerClassName,
  triggerVariant = "ghost",
}: DropdownMenuProps) {
  const [open, setOpen] = useState(false);
  const [menuPosition, setMenuPosition] = useState<MenuPosition | null>(null);
  const [portalContainer, setPortalContainer] = useState<HTMLElement | null>(
    null,
  );
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  const updatePosition = useCallback(() => {
    const triggerElement = triggerRef.current;
    if (triggerElement === null) {
      return;
    }
    const rect = triggerElement.getBoundingClientRect();
    const width = Math.min(
      Math.max(rect.width, MENU_MIN_WIDTH),
      window.innerWidth - VIEWPORT_PADDING * 2,
    );
    const spaceBelow =
      window.innerHeight - rect.bottom - MENU_GAP - VIEWPORT_PADDING;
    const spaceAbove = rect.top - MENU_GAP - VIEWPORT_PADDING;
    const openUp =
      side === "top"
        ? spaceAbove >= MENU_MIN_SPACE || spaceAbove >= spaceBelow
        : spaceBelow < MENU_MIN_SPACE && spaceAbove > spaceBelow;
    const maxHeight = Math.max(
      96,
      Math.min(MENU_MAX_HEIGHT, openUp ? spaceAbove : spaceBelow),
    );
    const desiredLeft = align === "end" ? rect.right - width : rect.left;
    const left = Math.min(
      Math.max(desiredLeft, VIEWPORT_PADDING),
      window.innerWidth - width - VIEWPORT_PADDING,
    );
    setMenuPosition({
      left,
      maxHeight,
      width,
      ...(openUp
        ? { bottom: window.innerHeight - rect.top + MENU_GAP }
        : { top: rect.bottom + MENU_GAP }),
    });
  }, [align, side]);

  const closeMenu = useCallback((restoreFocus = false) => {
    setOpen(false);
    setMenuPosition(null);
    setPortalContainer(null);
    if (restoreFocus) {
      triggerRef.current?.focus();
    }
  }, []);

  const openMenu = () => {
    if (disabled) return;
    updatePosition();
    setPortalContainer(resolveOverlayContainer(triggerRef.current));
    setOpen(true);
  };

  useEffect(() => {
    if (!open) {
      return;
    }
    const frame = window.requestAnimationFrame(() => {
      menuRef.current
        ?.querySelector<HTMLButtonElement>('[role="menuitem"]:not(:disabled)')
        ?.focus();
    });
    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (
        containerRef.current?.contains(target) !== true &&
        menuRef.current?.contains(target) !== true
      ) {
        closeMenu();
      }
    };
    const handleKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeMenu(true);
        return;
      }
      if (event.key === "Tab") {
        event.preventDefault();
        const trigger = triggerRef.current;
        closeMenu();
        if (trigger !== null) {
          const focusables = Array.from(
            document.querySelectorAll<HTMLElement>(
              'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])',
            ),
          ).filter((element) => element.closest('[role="menu"]') === null);
          const index = focusables.indexOf(trigger);
          const target = focusables[index + (event.shiftKey ? -1 : 1)];
          (target ?? trigger).focus();
        }
        return;
      }
    };
    const reposition = () => {
      updatePosition();
    };
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    window.addEventListener("resize", reposition);
    window.addEventListener("scroll", reposition, true);
    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("resize", reposition);
      window.removeEventListener("scroll", reposition, true);
    };
  }, [closeMenu, open, updatePosition]);

  const handleMenuKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (
      event.key !== "ArrowDown" &&
      event.key !== "ArrowUp" &&
      event.key !== "Home" &&
      event.key !== "End"
    ) {
      return;
    }
    const items = Array.from(
      menuRef.current?.querySelectorAll<HTMLButtonElement>(
        '[role="menuitem"]:not(:disabled)',
      ) ?? [],
    );
    if (items.length === 0) {
      return;
    }
    event.preventDefault();
    const current = items.indexOf(document.activeElement as HTMLButtonElement);
    let next: number;
    if (event.key === "Home") {
      next = 0;
    } else if (event.key === "End") {
      next = items.length - 1;
    } else if (event.key === "ArrowDown") {
      next = current < 0 ? 0 : (current + 1) % items.length;
    } else {
      next = current <= 0 ? items.length - 1 : current - 1;
    }
    items[next]?.focus();
  };

  const menuStyle: CSSProperties | undefined =
    menuPosition === null
      ? undefined
      : {
          bottom: menuPosition.bottom,
          left: menuPosition.left,
          maxHeight: menuPosition.maxHeight,
          top: menuPosition.top,
          width: menuPosition.width,
        };

  return (
    <div className={cn("relative", className)} ref={containerRef}>
      {trigger !== undefined ? (
        <button
          aria-controls={open ? menuId : undefined}
          aria-expanded={open}
          aria-haspopup="menu"
          aria-label={label}
          disabled={disabled}
          className={triggerClassName}
          onClick={() => {
            if (open) {
              closeMenu();
            } else {
              openMenu();
            }
          }}
          ref={triggerRef}
          type="button"
        >
          {trigger}
        </button>
      ) : (
        <IconButton
          disabled={disabled}
          aria-controls={open ? menuId : undefined}
          aria-expanded={open}
          aria-haspopup="menu"
          className={triggerClassName}
          label={label}
          onClick={() => {
            if (open) {
              closeMenu();
            } else {
              openMenu();
            }
          }}
          ref={triggerRef}
          variant={triggerVariant}
        >
          {children}
        </IconButton>
      )}

      {open && menuPosition !== null && portalContainer !== null
        ? createPortal(
            <div
              className="fixed z-40 overflow-y-auto overscroll-contain rounded-menu border border-border bg-background p-1 shadow-overlay"
              id={menuId}
              onKeyDown={handleMenuKeyDown}
              ref={menuRef}
              role="menu"
              style={menuStyle}
            >
              {heading !== undefined ? (
                <div className="border-b border-border px-2.5 py-1.5 text-xs font-medium text-muted-foreground" role="presentation">
                  {heading}
                </div>
              ) : null}
              {actions.map((action, index) => {
                const Icon = action.icon;
                return (
                  <button
                    className={cn(
                      "flex min-h-8 w-full items-center gap-2 rounded px-2.5 py-1.5 text-left text-[13px] hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none disabled:cursor-not-allowed disabled:text-disabled-foreground disabled:hover:bg-transparent max-md:min-h-11 md:min-h-8",
                      action.danger ? "text-destructive-text" : "text-foreground",
                      action.danger && index > 0 && !actions[index - 1]?.danger &&
                        "mt-1 rounded-t-none border-t border-border",
                    )}
                    disabled={action.disabled}
                    key={action.label}
                    onClick={() => {
                      action.onSelect();
                      closeMenu(true);
                    }}
                    role="menuitem"
                    type="button"
                  >
                    {Icon !== undefined ? (
                      <Icon aria-hidden="true" className="size-4" />
                    ) : null}
                    {action.label}
                  </button>
                );
              })}
            </div>,
            portalContainer,
          )
        : null}
    </div>
  );
}
