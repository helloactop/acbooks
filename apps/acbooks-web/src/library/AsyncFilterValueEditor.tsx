import { Combobox, type ComboboxOption } from "@acbooks/ui";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { isAbortError } from "../api/client";
import { getBookFilterOptions } from "./books-api";
import type { BookFilterOptionSource } from "./filter-model";

export interface AsyncFilterValueEditorProps {
  fieldLabel: string;
  invalid?: boolean;
  onChange: (value: string) => void;
  source: BookFilterOptionSource;
  value: string;
}

interface OptionState {
  error: boolean;
  hasMore: boolean;
  loading: boolean;
  options: ComboboxOption[];
}

const INITIAL_STATE: OptionState = {
  error: false,
  hasMore: false,
  loading: true,
  options: [],
};

export function AsyncFilterValueEditor({
  fieldLabel,
  invalid = false,
  onChange,
  source,
  value,
}: AsyncFilterValueEditorProps) {
  const { t } = useTranslation("library");
  const [state, setState] = useState<OptionState>(INITIAL_STATE);

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(
      () => {
        setState((current) => ({
          ...current,
          error: false,
          loading: true,
        }));
        getBookFilterOptions(source, value.trim(), controller.signal)
          .then((page) => {
            setState({
              error: false,
              hasMore: page.hasMore,
              loading: false,
              options: page.options.map((option) => ({
                count: option.count ?? 0,
                label: option.label,
                value: option.value,
              })),
            });
          })
          .catch((cause) => {
            if (isAbortError(cause)) {
              return;
            }
            setState({
              error: true,
              hasMore: false,
              loading: false,
              options: [],
            });
          });
      },
      value.length === 0 ? 0 : 250,
    );

    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [source, value]);

  const status = state.loading
    ? t("filters.valueSearching")
    : state.error
      ? t("filters.valueSearchError")
      : state.hasMore
        ? t("filters.valueMore")
        : undefined;

  return (
    <Combobox
      ariaLabel={t("filters.valueLabel", { field: fieldLabel })}
      emptyLabel={t("filters.valueNoMatches")}
      invalid={invalid}
      loading={state.loading}
      onChange={onChange}
      options={state.options}
      placeholder={t("filters.valuePlaceholder", { field: fieldLabel })}
      status={status}
      value={value}
    />
  );
}
