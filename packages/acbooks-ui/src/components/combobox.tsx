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
import { Input } from "./input";
import { Spinner } from "./spinner";

export interface ComboboxOption {
  count?: number;
  label: string;
  value: string;
}

export interface ComboboxProps {
  ariaLabel: string;
  className?: string;
  disabled?: boolean;
  emptyLabel?: ReactNode;
  invalid?: boolean;
  loading?: boolean;
  onChange: (value: string) => void;
  options: readonly ComboboxOption[];
  placeholder?: string;
  status?: ReactNode;
  value: string;
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

export function Combobox({
  ariaLabel,
  className,
  disabled = false,
  emptyLabel,
  invalid = false,
  loading = false,
  onChange,
  options,
  placeholder,
  status,
  value,
}: ComboboxProps) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [menuPosition, setMenuPosition] = useState<MenuPosition | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const listId = useId();
  const normalized = value.trim().toLocaleLowerCase();
  const visibleOptions =
    normalized.length === 0
      ? options
      : options.filter((option) => {
          const label = option.label.toLocaleLowerCase();
          const optionValue = option.value.toLocaleLowerCase();
          return (
            label.includes(normalized) || optionValue.includes(normalized)
          );
        });
  const activeOption = visibleOptions[activeIndex];

  const updatePosition = useCallback(() => {
    const input = inputRef.current;
    if (input === null) {
      return;
    }
    const rect = input.getBoundingClientRect();
    const width = Math.min(
      rect.width,
      window.innerWidth - VIEWPORT_PADDING * 2,
    );
    const availableBelow =
      window.innerHeight - rect.bottom - MENU_GAP - VIEWPORT_PADDING;
    const availableAbove =
      rect.top - MENU_GAP - VIEWPORT_PADDING;
    const openUp =
      availableBelow < 176 && availableAbove > availableBelow;
    const maxHeight = Math.max(
      88,
      Math.min(
        MENU_MAX_HEIGHT,
        openUp ? availableAbove : availableBelow,
      ),
    );
    setMenuPosition({
      left: Math.max(
        VIEWPORT_PADDING,
        Math.min(
          rect.left,
          window.innerWidth - width - VIEWPORT_PADDING,
        ),
      ),
      maxHeight,
      width,
      ...(openUp
        ? { bottom: window.innerHeight - rect.top + MENU_GAP }
        : { top: rect.bottom + MENU_GAP }),
    });
  }, []);

  const openMenu = useCallback(() => {
    updatePosition();
    setOpen(true);
  }, [updatePosition]);

  const closeMenu = useCallback(() => {
    setOpen(false);
    setActiveIndex(-1);
    setMenuPosition(null);
  }, []);

  useEffect(() => {
    if (!open) {
      return;
    }
    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node;
      if (
        rootRef.current?.contains(target) !== true &&
        menuRef.current?.contains(target) !== true
      ) {
        closeMenu();
      }
    };
    const reposition = () => {
      updatePosition();
    };
    document.addEventListener("pointerdown", handlePointerDown);
    window.addEventListener("resize", reposition);
    window.addEventListener("scroll", reposition, true);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      window.removeEventListener("resize", reposition);
      window.removeEventListener("scroll", reposition, true);
    };
  }, [closeMenu, open, updatePosition]);

  const selectOption = (nextValue: string) => {
    onChange(nextValue);
    closeMenu();
    inputRef.current?.focus();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      if (visibleOptions.length === 0) {
        return;
      }
      event.preventDefault();
      if (!open) {
        openMenu();
      }
      setActiveIndex((current) => (current + 1) % visibleOptions.length);
      return;
    }
    if (event.key === "ArrowUp") {
      if (visibleOptions.length === 0) {
        return;
      }
      event.preventDefault();
      if (!open) {
        openMenu();
      }
      setActiveIndex((current) =>
        current <= 0 ? visibleOptions.length - 1 : current - 1,
      );
      return;
    }
    if (event.key === "Enter") {
      if (!open) {
        return;
      }
      event.preventDefault();
      if (activeOption !== undefined) {
        selectOption(activeOption.value);
      } else {
        closeMenu();
      }
      return;
    }
    if (event.key === "Escape") {
      if (open) {
        event.preventDefault();
        closeMenu();
      }
      return;
    }
    if (event.key === "Tab") {
      closeMenu();
    }
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
    <div className={cn("relative min-w-0", className)} ref={rootRef}>
      <Input
        aria-activedescendant={
          open && activeOption !== undefined
            ? `${listId}-option-${activeIndex}`
            : undefined
        }
        aria-autocomplete="list"
        aria-controls={open ? listId : undefined}
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-invalid={invalid || undefined}
        aria-label={ariaLabel}
        autoComplete="off"
        disabled={disabled}
        onChange={(event) => {
          openMenu();
          setActiveIndex(-1);
          onChange(event.target.value);
        }}
        onClick={openMenu}
        onFocus={openMenu}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        ref={inputRef}
        role="combobox"
        spellCheck={false}
        type="text"
        value={value}
      />

      {open && menuPosition !== null
        ? createPortal(
            <div
              className="fixed z-40 overflow-y-auto overscroll-contain rounded-menu border border-border bg-background p-1 shadow-overlay"
              id={listId}
              ref={menuRef}
              role="listbox"
              style={menuStyle}
            >
              {loading ? (
                <div className="flex min-h-9 items-center gap-2 px-3 text-sm text-muted-foreground">
                  <Spinner />
                  {status}
                </div>
              ) : visibleOptions.length === 0 ? (
                <div
                  className="px-3 py-2 text-sm text-muted-foreground"
                  role="status"
                >
                  {status ?? emptyLabel}
                </div>
              ) : (
                visibleOptions.map((option, index) => (
                  <div
                    aria-selected={index === activeIndex}
                    className={cn(
                      "flex min-h-9 cursor-pointer items-center gap-2 rounded px-3 py-2 text-sm text-foreground",
                      index === activeIndex
                        ? "bg-accent-soft text-accent-text"
                        : "hover:bg-surface-hover",
                    )}
                    id={`${listId}-option-${index}`}
                    key={option.value}
                    onClick={() => {
                      selectOption(option.value);
                    }}
                    onPointerDown={(event) => {
                      event.preventDefault();
                    }}
                    onPointerMove={() => {
                      setActiveIndex(index);
                    }}
                    role="option"
                  >
                    <span className="min-w-0 flex-1 truncate">
                      {option.label}
                    </span>
                    {option.count !== undefined ? (
                      <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                        {option.count}
                      </span>
                    ) : null}
                  </div>
                ))
              )}
              {!loading && visibleOptions.length > 0 && status ? (
                <div
                  className="border-t border-border px-3 py-2 text-xs text-muted-foreground"
                  role="status"
                >
                  {status}
                </div>
              ) : null}
            </div>,
            document.body,
          )
        : null}
    </div>
  );
}
