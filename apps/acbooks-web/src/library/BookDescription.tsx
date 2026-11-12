import { Button, cn } from "@acbooks/ui";
import { ChevronDown, ChevronUp } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import {
  readDescriptionExpanded,
  saveDescriptionExpanded,
} from "./library-view-state";

const COLLAPSED_LINES = 3;

export interface BookDescriptionProps {
  bookId: string;
  description: string | null;
}

export function BookDescription({
  bookId,
  description,
}: BookDescriptionProps) {
  const { t } = useTranslation("library");
  const text = description?.trim() ?? "";
  const [expanded, setExpanded] = useState(() =>
    readDescriptionExpanded(bookId),
  );
  const [overflowing, setOverflowing] = useState(false);
  const measureRef = useRef<HTMLParagraphElement>(null);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const descriptionId = useId();

  useEffect(() => {
    if (text.length === 0) {
      return;
    }

    let frame = 0;
    const measure = () => {
      const node = measureRef.current;
      if (node === null) {
        return;
      }
      const lineHeight = Number.parseFloat(
        window.getComputedStyle(node).lineHeight,
      );
      const collapsedHeight = Number.isFinite(lineHeight)
        ? lineHeight * COLLAPSED_LINES
        : 0;
      setOverflowing(
        collapsedHeight > 0 && node.scrollHeight > collapsedHeight + 1,
      );
    };
    const schedule = () => {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(measure);
    };

    schedule();
    const observer =
      typeof ResizeObserver === "undefined"
        ? null
        : new ResizeObserver(schedule);
    if (observer !== null && measureRef.current !== null) {
      observer.observe(measureRef.current);
    }
    window.addEventListener("resize", schedule);
    const fonts = document.fonts;
    if (fonts !== undefined) {
      void fonts.ready.then(schedule).catch(() => undefined);
    }

    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener("resize", schedule);
      observer?.disconnect();
    };
  }, [text]);

  if (text.length === 0) {
    return (
      <p className="text-sm leading-[1.7] text-muted-foreground">
        {t("detail.noDescription")}
      </p>
    );
  }

  const toggleExpanded = () => {
    const next = !expanded;
    setExpanded(next);
    saveDescriptionExpanded(bookId, next);
    window.requestAnimationFrame(() => {
      toggleRef.current?.scrollIntoView?.({ block: "nearest" });
    });
  };

  return (
    <div className="relative w-full">
      <p
        aria-hidden="true"
        className="invisible absolute inset-x-0 top-0 text-sm leading-[1.7] break-words whitespace-pre-line"
        data-description-measure=""
        ref={measureRef}
      >
        {text}
      </p>

      <p
        className={cn(
          "text-sm leading-[1.7] break-words whitespace-pre-line text-muted-foreground",
          !expanded && "line-clamp-3",
        )}
        id={descriptionId}
      >
        {text}
      </p>

      {overflowing ? (
        <Button
          aria-controls={descriptionId}
          aria-expanded={expanded}
          className="-ml-2 mt-1 text-[13px] text-accent-text hover:bg-surface-hover"
          onClick={toggleExpanded}
          ref={toggleRef}
          size="sm"
          variant="ghost"
        >
          {expanded ? t("detail.collapseDescription") : t("detail.expandDescription")}
          {expanded ? (
            <ChevronUp aria-hidden="true" className="size-4" />
          ) : (
            <ChevronDown aria-hidden="true" className="size-4" />
          )}
        </Button>
      ) : null}
    </div>
  );
}
