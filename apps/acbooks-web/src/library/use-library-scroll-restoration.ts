import { useEffect, useRef, type RefObject } from "react";
import { useLocation, useNavigationType } from "react-router-dom";

import {
  readScrollPosition,
  takeReturnScroll,
} from "../layout/scroll-restoration";

export interface LibraryScrollRestorationOptions {
  loading: boolean;
  mainRef: RefObject<HTMLElement | null>;
}

export function useLibraryScrollRestoration({
  loading,
  mainRef,
}: LibraryScrollRestorationOptions): void {
  const location = useLocation();
  const navigationType = useNavigationType();
  const restored = useRef(false);

  useEffect(() => {
    if (loading || restored.current) {
      return;
    }
    restored.current = true;
    const returnScroll = takeReturnScroll();
    // Browser back/forward restores the entry's own position; waiting for the loaded
    // list avoids clamping it against the loading placeholder.
    const entryScroll =
      navigationType === "POP" ? readScrollPosition(location) : 0;
    const scrollTop = Math.max(returnScroll, entryScroll);
    if (scrollTop <= 0) {
      return;
    }
    const frame = window.requestAnimationFrame(() => {
      mainRef.current?.scrollTo({ top: scrollTop });
    });
    return () => {
      window.cancelAnimationFrame(frame);
    };
  }, [loading, location, mainRef, navigationType]);
}
