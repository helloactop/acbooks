import { useEffect, useRef, useState } from "react";

import {
  EMPTY_FILTER_RULES,
  serializeSmartFilterRules,
  summarizeSmartFilterRules,
  type BookFilterField,
  type SmartFilterRules,
  type SmartFilterSummary,
} from "./filter-model";

const COMMIT_DELAY_MS = 300;

export interface LibraryFilters {
  changeRules: (rules: SmartFilterRules, immediate?: boolean) => void;
  removeCondition: (id: string) => void;
  resetDraft: () => void;
  rules: SmartFilterRules;
  summary: SmartFilterSummary;
}

export interface LibraryFiltersOptions {
  appliedFilters: string;
  appliedRules: SmartFilterRules;
  externalRevision: number;
  fields: readonly BookFilterField[];
  onApply: (rules: SmartFilterRules) => void;
}

export function useLibraryFilters({
  appliedFilters,
  appliedRules,
  externalRevision,
  fields,
  onApply,
}: LibraryFiltersOptions): LibraryFilters {
  const [rules, setRules] = useState<SmartFilterRules>(appliedRules);
  const timer = useRef<number | undefined>(undefined);
  const lastExternalRevision = useRef(externalRevision);
  const summary = summarizeSmartFilterRules(rules, fields);

  useEffect(
    () => () => {
      if (timer.current !== undefined) {
        window.clearTimeout(timer.current);
      }
    },
    [],
  );

  useEffect(() => {
    if (externalRevision === lastExternalRevision.current) {
      return;
    }
    lastExternalRevision.current = externalRevision;
    if (timer.current !== undefined) {
      window.clearTimeout(timer.current);
      timer.current = undefined;
    }
    setRules(appliedRules);
  }, [externalRevision, appliedRules]);

  const changeRules = (next: SmartFilterRules, immediate = false) => {
    setRules(next);
    if (timer.current !== undefined) {
      window.clearTimeout(timer.current);
      timer.current = undefined;
    }
    const serialized = serializeSmartFilterRules(next, fields);
    if (serialized === appliedFilters) {
      return;
    }
    if (immediate || serialized.length === 0) {
      onApply(next);
      return;
    }
    timer.current = window.setTimeout(() => {
      timer.current = undefined;
      onApply(next);
    }, COMMIT_DELAY_MS);
  };

  const removeCondition = (id: string) => {
    changeRules(
      {
        ...rules,
        conditions: rules.conditions.filter(
          (condition) => condition.id !== id,
        ),
      },
      true,
    );
  };

  const resetDraft = () => {
    if (timer.current !== undefined) {
      window.clearTimeout(timer.current);
      timer.current = undefined;
    }
    setRules(EMPTY_FILTER_RULES);
  };

  return {
    changeRules,
    removeCondition,
    resetDraft,
    rules,
    summary,
  };
}
