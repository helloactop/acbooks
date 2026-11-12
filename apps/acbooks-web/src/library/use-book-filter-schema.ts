import { useEffect, useState } from "react";

import { isAbortError, toApiError, type ApiError } from "../api/client";
import { getBookFilterSchema } from "./books-api";
import type { BookFilterSchema } from "./filter-model";

interface FilterSchemaState {
  error: ApiError | null;
  loaded: boolean;
  schema: BookFilterSchema | null;
}

export interface BookFilterSchemaResult extends FilterSchemaState {
  loading: boolean;
  retry: () => void;
}

export function useBookFilterSchema(
  active: boolean,
  onSessionExpired: () => void,
): BookFilterSchemaResult {
  const [state, setState] = useState<FilterSchemaState>({
    error: null,
    loaded: false,
    schema: null,
  });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!active || state.loaded) {
      return;
    }
    const controller = new AbortController();
    getBookFilterSchema(controller.signal)
      .then((schema) => {
        setState({ error: null, loaded: true, schema });
      })
      .catch((cause) => {
        if (isAbortError(cause)) {
          return;
        }
        const apiError = toApiError(cause);
        if (apiError.status === 401) {
          onSessionExpired();
          return;
        }
        setState((current) => ({
          ...current,
          error: apiError,
          loaded: false,
        }));
      });
    return () => controller.abort();
  }, [active, attempt, onSessionExpired, state.loaded]);

  return {
    ...state,
    loading: active && !state.loaded && state.error === null,
    retry: () => {
      setState((current) => ({
        ...current,
        error: null,
        loaded: false,
      }));
      setAttempt((current) => current + 1);
    },
  };
}
