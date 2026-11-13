import { Button, Input, Spinner, useLanguage } from "@acbooks/ui";
import { Search } from "lucide-react";
import {
  useEffect,
  useId,
  useRef,
  useState,
  type KeyboardEvent,
  type RefObject,
} from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation, useNavigate } from "react-router-dom";

import { getBooks, type BookSummary } from "../library/books-api";
import { BookCover } from "../library/BookCover";
import { joinNames } from "../library/presentation";
import { isAbortError, toApiError, type ApiError } from "../api/client";
import { useAppShell } from "./app-shell-context";
import { bookDetailHref, libraryHref, rememberDetailReturn } from "./routes";

export interface GlobalSearchProps {
  focusOnMount?: boolean;
  inputRef?: RefObject<HTMLInputElement | null>;
  onFocusHandled?: () => void;
  replaceNavigation?: boolean;
}

export function GlobalSearch({
  focusOnMount = false,
  inputRef,
  onFocusHandled,
  replaceNavigation = false,
}: GlobalSearchProps) {
  const { t } = useTranslation("app");
  const { resolvedLanguage } = useLanguage();
  const { expireSession } = useAppShell();
  const location = useLocation();
  const routerNavigate = useNavigate();
  const [value, setValue] = useState("");
  const [term, setTerm] = useState("");
  const [results, setResults] = useState<BookSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [reloadKey, setReloadKey] = useState(0);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const localInputRef = useRef<HTMLInputElement>(null);
  const timerRef = useRef<number | undefined>(undefined);
  const controllerRef = useRef<AbortController | null>(null);
  const requestIdRef = useRef(0);
  const requestedTermRef = useRef("");
  const composingRef = useRef(false);
  const listId = useId();
  const input = inputRef ?? localInputRef;

  useEffect(
    () => () => {
      if (timerRef.current !== undefined) {
        window.clearTimeout(timerRef.current);
      }
      controllerRef.current?.abort();
    },
    [],
  );

  useEffect(() => {
    if (!focusOnMount) {
      return;
    }
    const frame = window.requestAnimationFrame(() => {
      input.current?.focus();
      onFocusHandled?.();
    });
    return () => {
      window.cancelAnimationFrame(frame);
    };
  }, [focusOnMount, input, onFocusHandled]);

  useEffect(() => {
    if (term.length === 0) {
      return;
    }
    const controller = new AbortController();
    controllerRef.current?.abort();
    controllerRef.current = controller;
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    requestedTermRef.current = term;
    getBooks({ q: term, limit: 5, offset: 0 }, controller.signal)
      .then((page) => {
        if (requestId !== requestIdRef.current) {
          return;
        }
        setResults(page.items);
        setTotal(page.total);
        setError(null);
        setActiveIndex(-1);
      })
      .catch((cause) => {
        if (isAbortError(cause) || requestId !== requestIdRef.current) {
          return;
        }
        const apiError = toApiError(cause);
        if (apiError.status === 401) {
          expireSession();
          return;
        }
        setResults([]);
        setTotal(0);
        setError(apiError);
      })
      .finally(() => {
        if (requestId === requestIdRef.current) {
          setLoading(false);
          if (controllerRef.current === controller) {
            controllerRef.current = null;
          }
        }
      });
    return () => controller.abort();
  }, [expireSession, reloadKey, term]);

  useEffect(() => {
    const handlePointerDown = (event: PointerEvent) => {
      if (
        wrapperRef.current !== null &&
        !wrapperRef.current.contains(event.target as Node)
      ) {
        setOpen(false);
      }
    };
    document.addEventListener("pointerdown", handlePointerDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
    };
  }, []);

  const schedule = (nextValue: string, immediate = false) => {
    const trimmed = nextValue.trim();
    if (trimmed.length === 0) {
      if (timerRef.current !== undefined) {
        window.clearTimeout(timerRef.current);
        timerRef.current = undefined;
      }
      controllerRef.current?.abort();
      controllerRef.current = null;
      requestIdRef.current += 1;
      requestedTermRef.current = "";
      setTerm("");
      setResults([]);
      setTotal(0);
      setError(null);
      setOpen(false);
      setLoading(false);
      setActiveIndex(-1);
      return;
    }
    if (trimmed === term) {
      if (timerRef.current !== undefined) {
        window.clearTimeout(timerRef.current);
        timerRef.current = undefined;
      }
      setOpen(true);
      const requestActive =
        controllerRef.current !== null &&
        !controllerRef.current.signal.aborted &&
        requestedTermRef.current === term;
      // Restoring a keyword can land while a newer term's request was aborted.
      // The old results were cleared, so the current term must be fetched again.
      if (loading && !requestActive) {
        setResults([]);
        setTotal(0);
        setError(null);
        setActiveIndex(-1);
        setReloadKey((current) => current + 1);
      }
      return;
    }

    if (timerRef.current !== undefined) {
      window.clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
    controllerRef.current?.abort();
    controllerRef.current = null;
    requestIdRef.current += 1;
    setResults([]);
    setTotal(0);
    setError(null);
    setActiveIndex(-1);
    setOpen(true);
    setLoading(true);
    const run = () => {
      timerRef.current = undefined;
      setTerm(trimmed);
    };
    if (immediate) {
      run();
    } else {
      timerRef.current = window.setTimeout(run, 250);
    }
  };

  const handleChange = (nextValue: string) => {
    setValue(nextValue);
    if (!composingRef.current) {
      schedule(nextValue);
    }
  };

  const goTo = (href: string, detail: boolean) => {
    if (detail) {
      rememberDetailReturn(`${location.pathname}${location.search}`);
    }
    setOpen(false);
    setActiveIndex(-1);
    routerNavigate(href, replaceNavigation ? { replace: true } : undefined);
  };

  const currentResult = results[activeIndex];

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      if (results.length === 0) {
        return;
      }
      event.preventDefault();
      setOpen(true);
      setActiveIndex((current) => (current + 1) % results.length);
      return;
    }
    if (event.key === "ArrowUp") {
      if (results.length === 0) {
        return;
      }
      event.preventDefault();
      setOpen(true);
      setActiveIndex((current) =>
        current <= 0 ? results.length - 1 : current - 1,
      );
      return;
    }
    if (event.key === "Enter") {
      if (event.nativeEvent.isComposing) {
        return;
      }
      event.preventDefault();
      if (open && currentResult !== undefined) {
        goTo(bookDetailHref(currentResult.id), true);
        return;
      }
      const query = value.trim();
      if (query.length > 0) {
        goTo(libraryHref(query), false);
      }
      return;
    }
    if (event.key === "Escape") {
      setOpen(false);
      setActiveIndex(-1);
    }
  };

  return (
    <div className="relative z-30" ref={wrapperRef}>
      <div className="relative">
        <Search
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 left-3 size-[18px] -translate-y-1/2 text-muted-foreground"
          strokeWidth={2}
        />
        <Input
          aria-activedescendant={
            open && currentResult !== undefined
              ? `${listId}-option-${activeIndex}`
              : undefined
          }
          aria-autocomplete="list"
          aria-controls={open ? listId : undefined}
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-label={t("search.label")}
          className="pr-16 pl-9 max-lg:h-11 [&::-webkit-search-cancel-button]:appearance-none"
          onChange={(event) => {
            handleChange(event.target.value);
          }}
          onCompositionEnd={(event) => {
            composingRef.current = false;
            schedule(event.currentTarget.value, true);
          }}
          onCompositionStart={() => {
            composingRef.current = true;
          }}
          onFocus={() => {
            if (value.trim().length > 0) {
              setOpen(true);
            }
          }}
          onKeyDown={handleKeyDown}
          placeholder={t("search.placeholder")}
          ref={input}
          role="combobox"
          type="search"
          value={value}
        />
        <kbd className="pointer-events-none absolute top-1/2 right-2 -translate-y-1/2 rounded border border-border bg-surface px-1.5 py-0.5 text-[10px] leading-4 text-muted-foreground">
          {t("search.shortcut")}
        </kbd>
      </div>

      {open ? (
        <div className="absolute top-full right-0 left-0 z-40 mt-2 w-full rounded-menu border border-border bg-background p-1 shadow-overlay lg:w-[340px]">
          {loading && results.length === 0 ? (
            <div className="flex items-center gap-2 px-3 py-3 text-sm text-muted-foreground">
              <Spinner label={t("search.loading")} />
              <span>{t("search.loading")}</span>
            </div>
          ) : null}

          {error !== null ? (
            <div className="flex flex-col gap-2 px-3 py-3">
              <p className="text-sm text-destructive-text" role="alert">
                {t("search.error")}
              </p>
              <Button
                onClick={() => {
                  setLoading(true);
                  setError(null);
                  setReloadKey((current) => current + 1);
                }}
                size="sm"
                variant="outline"
              >
                {t("search.retry")}
              </Button>
            </div>
          ) : null}

          {!loading &&
          error === null &&
          term.length > 0 &&
          results.length === 0 ? (
            <p className="px-3 py-3 text-sm text-muted-foreground">
              {t("search.empty")}
            </p>
          ) : null}

          {results.length > 0 ? (
            <ul aria-label={t("search.resultLabel")} id={listId} role="listbox">
              {results.map((book, index) => {
                const authors =
                  joinNames(book.authors, resolvedLanguage) ||
                  t("search.unknownAuthor");
                return (
                  <li key={book.id} role="none">
                    <button
                      aria-selected={activeIndex === index}
                      className={`flex w-full items-start gap-2 rounded px-2 py-2 text-left transition-colors duration-150 ease-out focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none ${
                        activeIndex === index
                          ? "bg-surface-hover"
                          : "hover:bg-surface-hover"
                      }`}
                      id={`${listId}-option-${index}`}
                      onClick={() => {
                        goTo(bookDetailHref(book.id), true);
                      }}
                      onMouseEnter={() => {
                        setActiveIndex(index);
                      }}
                      role="option"
                      type="button"
                    >
                      <BookCover
                        book={book}
                        className="w-9 shrink-0"
                        titleClassName="text-[8px] leading-tight"
                      />
                      <span className="min-w-0 flex-1">
                        <span className="line-clamp-2 text-sm font-medium">
                          {book.title}
                        </span>
                        <span className="mt-0.5 block truncate text-xs text-muted-foreground">
                          {authors}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          ) : null}

          {total > 0 ? (
            <Link
              className="mt-1 flex items-center justify-between rounded px-3 py-2 text-sm font-medium text-accent-text transition-colors duration-150 ease-out hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
              onClick={() => {
                setOpen(false);
                setActiveIndex(-1);
              }}
              replace={replaceNavigation}
              to={libraryHref(term)}
            >
              <span>{t("search.viewAll", { count: total })}</span>
            </Link>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
