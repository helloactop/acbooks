import type { LucideIcon } from "lucide-react";
import {
  useEffect,
  useId,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from "react";

import { cn } from "../lib/utils";
import { IconButton } from "./icon-button";
import type { ButtonProps } from "./button";

export interface DropdownMenuAction {
  disabled?: boolean;
  icon?: LucideIcon;
  label: string;
  onSelect: () => void;
}

export interface DropdownMenuProps {
  actions: readonly DropdownMenuAction[];
  align?: "start" | "end";
  children: ReactNode;
  className?: string;
  label: string;
  side?: "bottom" | "top";
  triggerClassName?: string;
  triggerVariant?: ButtonProps["variant"];
}

export function DropdownMenu({
  actions,
  align = "end",
  children,
  className,
  label,
  side = "bottom",
  triggerClassName,
  triggerVariant = "ghost",
}: DropdownMenuProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

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
      if (
        containerRef.current !== null &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setOpen(false);
      }
    };
    const handleKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        triggerRef.current?.focus();
      }
    };
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

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

  return (
    <div className={cn("relative", className)} ref={containerRef}>
      <IconButton
        aria-controls={open ? menuId : undefined}
        aria-expanded={open}
        aria-haspopup="menu"
        className={triggerClassName}
        label={label}
        onClick={() => {
          setOpen((current) => !current);
        }}
        ref={triggerRef}
        variant={triggerVariant}
      >
        {children}
      </IconButton>

      {open ? (
        <div
          className={cn(
            "absolute z-30 min-w-44 rounded-menu border border-border bg-background p-1 shadow-overlay",
            side === "top" ? "bottom-full mb-2" : "top-full mt-2",
            align === "end" ? "right-0" : "left-0",
          )}
          id={menuId}
          onKeyDown={handleMenuKeyDown}
          ref={menuRef}
          role="menu"
        >
          {actions.map((action) => {
            const Icon = action.icon;
            return (
              <button
                className="flex min-h-9 w-full items-center gap-2 rounded px-3 py-2 text-left text-sm text-foreground hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none disabled:cursor-not-allowed disabled:text-disabled-foreground disabled:hover:bg-transparent max-md:min-h-11"
                disabled={action.disabled}
                key={action.label}
                onClick={() => {
                  action.onSelect();
                  setOpen(false);
                  triggerRef.current?.focus();
                }}
                role="menuitem"
                type="button"
              >
                {Icon !== undefined ? (
                  <Icon aria-hidden="true" className="size-[18px]" />
                ) : null}
                {action.label}
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}
