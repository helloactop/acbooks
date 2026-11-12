import { X } from "lucide-react";
import { useTranslation } from "react-i18next";

import {
  needsConditionValue,
  type BookFilterField,
  type SmartFilterCondition,
  type SmartFilterSummary,
} from "./filter-model";

export interface ActiveFilterSummaryProps {
  fields: readonly BookFilterField[];
  onOpenFilters: () => void;
  onRemove: (id: string) => void;
  summary: SmartFilterSummary;
}

const CHIP_CLASS =
  "inline-flex h-9 max-w-full items-center gap-1.5 rounded-full border border-border bg-surface px-3 text-xs text-foreground transition-colors duration-150 ease-out hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none max-lg:min-h-11";

export function ActiveFilterSummary({
  fields,
  onOpenFilters,
  onRemove,
  summary,
}: ActiveFilterSummaryProps) {
  const { t } = useTranslation("library");
  const fieldByKey = new Map(fields.map((field) => [field.key, field]));

  const fieldLabel = (field: BookFilterField | undefined, key: string) =>
    field === undefined
      ? key
      : t(`filters.fields.${field.key}`, { defaultValue: field.key });
  const operatorLabel = (operator: string) =>
    t(`filters.operators.${operator}`, { defaultValue: operator });
  const optionLabel = (field: BookFilterField, value: string) => {
    if (field.key === "format") {
      return value.toLocaleUpperCase();
    }
    const option = field.options?.find((item) => item.value === value);
    if (option === undefined) {
      return value;
    }
    return field.key === "readingStatus"
      ? t(`status.${option.value}`, { defaultValue: option.label })
      : option.label;
  };
  const conditionLabel = (condition: SmartFilterCondition) => {
    const field = fieldByKey.get(condition.field);
    const label = fieldLabel(field, condition.field);
    const operator = operatorLabel(condition.operator);
    if (!needsConditionValue(condition.operator)) {
      return t("filters.summaryConditionNoValue", {
        field: label,
        operator,
      });
    }
    if (Array.isArray(condition.value)) {
      return t("filters.summaryCondition", {
        field: label,
        operator,
        value: condition.value.join(" – "),
      });
    }
    const value =
      field !== undefined && field.type === "select"
        ? optionLabel(field, condition.value ?? "")
        : (condition.value ?? "");
    return t("filters.summaryCondition", {
      field: label,
      operator,
      value,
    });
  };

  if (summary.total === 0) {
    return null;
  }

  return (
    <div
      aria-label={t("filters.summaryLabel")}
      className="flex flex-wrap items-center gap-2"
      role="group"
    >
      {summary.validRules.conditions.map((condition) => {
        const label = conditionLabel(condition);
        return (
          <button
            aria-label={t("filters.summaryRemove", { condition: label })}
            className={CHIP_CLASS}
            key={condition.id}
            onClick={() => {
              onRemove(condition.id);
            }}
            type="button"
          >
            <span className="truncate">{label}</span>
            <X
              aria-hidden="true"
              className="size-3.5 shrink-0 text-muted-foreground"
            />
          </button>
        );
      })}

      {summary.incompleteCount > 0 ? (
        <button
          aria-label={t("filters.summaryOpen")}
          className={`${CHIP_CLASS} text-warning`}
          onClick={onOpenFilters}
          type="button"
        >
          {t("filters.summaryIncomplete", {
            total: summary.incompleteCount,
          })}
        </button>
      ) : null}

      {summary.invalidCount > 0 ? (
        <button
          aria-label={t("filters.summaryOpen")}
          className={`${CHIP_CLASS} text-destructive-text`}
          onClick={onOpenFilters}
          type="button"
        >
          {t("filters.summaryInvalid", { total: summary.invalidCount })}
        </button>
      ) : null}
    </div>
  );
}
