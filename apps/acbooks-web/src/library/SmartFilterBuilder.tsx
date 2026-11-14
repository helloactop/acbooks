import {
  Button,
  IconButton,
  Input,
  Select,
  Spinner,
  cn,
} from "@acbooks/ui";
import { Trash2, WandSparkles } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

import { AsyncFilterValueEditor } from "./AsyncFilterValueEditor";
import {
  createSmartFilterCondition,
  needsConditionValue,
  summarizeSmartFilterRules,
  validateSmartFilterCondition,
  type BookFilterField,
  type BookFilterOption,
  type FilterConditionValidation,
  type SmartFilterCondition,
  type SmartFilterRules,
} from "./filter-model";

export interface SmartFilterBuilderProps {
  error?: ReactNode;
  fields: readonly BookFilterField[];
  loading: boolean;
  maxConditions: number;
  onChange: (rules: SmartFilterRules) => void;
  onRetry: () => void;
  rules: SmartFilterRules;
}

const ROW_GRID =
  "grid gap-2 rounded-card border border-border bg-background p-3 @min-[680px]:grid-cols-[34px_minmax(150px,0.85fr)_minmax(120px,0.62fr)_minmax(170px,1.35fr)_42px] @min-[680px]:items-center";

function initialValue(operator: string): string | string[] | undefined {
  if (operator === "between") {
    return ["", ""];
  }
  return needsConditionValue(operator) ? "" : undefined;
}

export function SmartFilterBuilder({
  error,
  fields,
  loading,
  maxConditions,
  onChange,
  onRetry,
  rules,
}: SmartFilterBuilderProps) {
  const { t } = useTranslation("library");
  const fieldByKey = new Map(fields.map((field) => [field.key, field]));
  const groups = Array.from(
    fields.reduce((result, field) => {
      const group = result.get(field.group) ?? [];
      group.push(field);
      result.set(field.group, group);
      return result;
    }, new Map<string, BookFilterField[]>()),
  );
  const summary = summarizeSmartFilterRules(rules, fields);

  const fieldLabel = (field: BookFilterField) =>
    t(`filters.fields.${field.key}`, { defaultValue: field.key });
  const groupLabel = (group: string) =>
    t(`filters.groups.${group}`, { defaultValue: group });
  const operatorLabel = (operator: string) =>
    t(`filters.operators.${operator}`, { defaultValue: operator });
  const optionLabel = (field: BookFilterField, option: BookFilterOption) =>
    field.key === "readingStatus"
      ? t(`status.${option.value}`, { defaultValue: option.label })
      : option.label;

  const validationMessage = (validation: FilterConditionValidation) => {
    if (validation.status === "incomplete") {
      return t("filters.ruleIncomplete");
    }
    switch (validation.reason) {
      case "date":
        return t("filters.ruleInvalidDate");
      case "field":
        return t("filters.ruleInvalidField");
      case "length":
        return t("filters.ruleInvalidLength");
      case "number":
        return t("filters.ruleInvalidNumber");
      case "operator":
        return t("filters.ruleInvalidOperator");
      case "range":
        return t("filters.ruleInvalidRange");
      case "value":
        return t("filters.ruleInvalidValue");
      default:
        return t("filters.ruleInvalidValue");
    }
  };

  const addCondition = () => {
    onChange({
      ...rules,
      conditions: [
        ...rules.conditions,
        createSmartFilterCondition(fields),
      ],
    });
  };

  const updateCondition = (
    id: string,
    patch: Partial<SmartFilterCondition>,
  ) => {
    onChange({
      ...rules,
      conditions: rules.conditions.map((condition) =>
        condition.id === id ? { ...condition, ...patch } : condition,
      ),
    });
  };

  const changeField = (condition: SmartFilterCondition, fieldKey: string) => {
    const field = fieldByKey.get(fieldKey);
    if (field === undefined) {
      return;
    }
    const operator = field.operators[0] ?? "equals";
    updateCondition(condition.id, {
      field: fieldKey,
      operator,
      value: initialValue(operator),
    });
  };

  const changeOperator = (
    condition: SmartFilterCondition,
    operator: string,
  ) => {
    updateCondition(condition.id, {
      operator,
      value: initialValue(operator),
    });
  };

  const removeCondition = (id: string) => {
    onChange({
      ...rules,
      conditions: rules.conditions.filter((condition) => condition.id !== id),
    });
  };

  const valueEditor = (
    condition: SmartFilterCondition,
    field: BookFilterField,
    validation: FilterConditionValidation,
  ) => {
    const invalid = validation.status === "invalid";
    if (!needsConditionValue(condition.operator)) {
      return (
        <div className="flex h-10 items-center rounded-control bg-surface px-3 text-sm text-muted-foreground max-lg:h-11">
          {t("filters.noValue")}
        </div>
      );
    }

    if (condition.operator === "between") {
      const values = Array.isArray(condition.value)
        ? condition.value
        : ["", ""];
      const inputType = field.type === "date" ? "date" : "number";
      return (
        <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-2">
          <Input
            aria-invalid={invalid || undefined}
            aria-label={t("filters.rangeStart", {
              field: fieldLabel(field),
            })}
            className="max-lg:h-11"
            onChange={(event) => {
              updateCondition(condition.id, {
                value: [event.target.value, values[1] ?? ""],
              });
            }}
            step={inputType === "number" ? "any" : undefined}
            type={inputType}
            value={values[0] ?? ""}
          />
          <span className="text-xs text-muted-foreground">
            {t("filters.rangeTo")}
          </span>
          <Input
            aria-invalid={invalid || undefined}
            aria-label={t("filters.rangeEnd", {
              field: fieldLabel(field),
            })}
            className="max-lg:h-11"
            onChange={(event) => {
              updateCondition(condition.id, {
                value: [values[0] ?? "", event.target.value],
              });
            }}
            step={inputType === "number" ? "any" : undefined}
            type={inputType}
            value={values[1] ?? ""}
          />
        </div>
      );
    }

    const value = Array.isArray(condition.value)
      ? (condition.value[0] ?? "")
      : (condition.value ?? "");

    if (
      field.type === "select" &&
      field.optionSource !== undefined &&
      field.optionSource !== null
    ) {
      return (
        <AsyncFilterValueEditor
          fieldLabel={fieldLabel(field)}
          invalid={invalid}
          key={`${condition.id}:${field.key}`}
          onChange={(nextValue) => {
            updateCondition(condition.id, { value: nextValue });
          }}
          source={field.optionSource}
          value={value}
        />
      );
    }

    if (field.type === "select") {
      return (
        <Select
          ariaLabel={t("filters.valueLabel", {
            field: fieldLabel(field),
          })}
          className="max-lg:h-11"
          invalid={invalid}
          onValueChange={(nextValue) => {
            updateCondition(condition.id, { value: nextValue });
          }}
          options={[
            { label: t("filters.selectValue"), value: "" },
            ...(field.options ?? []).map((option) => ({
              label: optionLabel(field, option),
              value: option.value,
            })),
          ]}
          value={typeof value === "string" ? value : ""}
        />
      );
    }

    return (
      <div className="relative min-w-0">
        <Input
          aria-invalid={invalid || undefined}
          aria-label={t("filters.valueLabel", { field: fieldLabel(field) })}
          className={cn("max-lg:h-11", field.unit ? "pr-12" : undefined)}
          maxLength={field.type === "text" ? 500 : undefined}
          onChange={(event) => {
            updateCondition(condition.id, { value: event.target.value });
          }}
          placeholder={t("filters.valuePlaceholder", {
            field: fieldLabel(field),
          })}
          step={field.type === "number" ? "any" : undefined}
          type={
            field.type === "date"
              ? "date"
              : field.type === "number"
                ? "number"
                : "text"
          }
          value={value}
        />
        {field.unit ? (
          <span className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-xs text-muted-foreground">
            {field.unit}
          </span>
        ) : null}
      </div>
    );
  };

  const hasFields = fields.length > 0;
  const atLimit = rules.conditions.length >= maxConditions;

  return (
    <section
      aria-label={t("filters.smartTitle")}
      className="@container rounded-card border border-border bg-surface"
      id="library-smart-filter-panel"
    >
      <div className="flex flex-col gap-2 border-b border-border px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-center gap-2">
          <span className="flex size-8 shrink-0 items-center justify-center rounded-control bg-accent-soft text-accent-text">
            <WandSparkles aria-hidden="true" className="size-[18px]" />
          </span>
          <div className="min-w-0">
            <h2 className="text-[15px] font-semibold text-foreground">
              {t("filters.smartTitle")}
            </h2>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-muted-foreground">
            {t("filters.match")}
          </span>
          <Select
            ariaLabel={t("filters.combinator")}
            containerClassName="w-[140px]"
            onValueChange={(nextValue) => {
              onChange({
                ...rules,
                combinator: nextValue === "ANY" ? "ANY" : "ALL",
              });
            }}
            options={[
              { label: t("filters.allConditions"), value: "ALL" },
              { label: t("filters.anyCondition"), value: "ANY" },
            ]}
            value={rules.combinator}
          />
          <Button
            disabled={loading || !hasFields || atLimit}
            onClick={addCondition}
            variant="soft"
          >
            {t("filters.addCondition")}
          </Button>
        </div>
      </div>

      <div className="space-y-2 p-3">
        {loading ? (
          <div className="flex min-h-20 items-center justify-center gap-2 text-[13px] text-muted-foreground">
            <Spinner label={t("filters.loadingSchema")} />
            <span>{t("filters.loadingSchema")}</span>
          </div>
        ) : null}

        {!loading && error !== undefined ? (
          <div className="flex min-h-20 flex-col items-center justify-center gap-2 rounded-card border border-border bg-background px-4 text-center">
            <p className="text-sm text-destructive-text">{error}</p>
            <Button onClick={onRetry} size="sm" variant="outline">
              {t("errors.retry")}
            </Button>
          </div>
        ) : null}

        {!loading && error === undefined && rules.conditions.length === 0 ? (
          <button
            className="flex min-h-28 w-full flex-col items-center justify-center rounded-card border border-dashed border-control-border bg-background px-6 text-center transition-colors duration-150 ease-out hover:bg-surface-hover focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none disabled:cursor-not-allowed disabled:text-disabled-foreground"
            disabled={!hasFields || atLimit}
            onClick={addCondition}
            type="button"
          >
            <span className="text-sm font-medium">
              {t("filters.addFirst")}
            </span>
            <span className="mt-1 text-xs text-muted-foreground">
              {t("filters.addFirstHint")}
            </span>
          </button>
        ) : null}

        {!loading && error === undefined
          ? rules.conditions.map((condition, index) => {
              const field = fieldByKey.get(condition.field);
              if (field === undefined) {
                return (
                  <div
                    className="flex items-center justify-between gap-3 rounded-card border border-border bg-background p-3 text-sm text-foreground"
                    key={condition.id}
                  >
                    <span>
                      {t("filters.unsupported")}
                      {": "}
                      <span className="font-mono text-xs">
                        {condition.field}
                      </span>
                    </span>
                    <IconButton
                      label={t("filters.removeUnsupported")}
                      onClick={() => {
                        removeCondition(condition.id);
                      }}
                    >
                      <Trash2 aria-hidden="true" />
                    </IconButton>
                  </div>
                );
              }

              const validation = validateSmartFilterCondition(
                condition,
                fields,
              );
              return (
                <div className={ROW_GRID} key={condition.id}>
                  <span className="hidden h-7 w-7 items-center justify-center rounded-control bg-surface text-xs font-semibold text-muted-foreground @min-[680px]:flex">
                    {index + 1}
                  </span>
                  <Select
                    ariaLabel={t("filters.conditionField", {
                      index: index + 1,
                    })}
                    className="max-lg:h-11"
                    groups={groups.map(([group, groupFields]) => ({
                      label: groupLabel(group),
                      options: groupFields.map((groupField) => ({
                        label: fieldLabel(groupField),
                        value: groupField.key,
                      })),
                    }))}
                    onValueChange={(nextValue) => {
                      changeField(condition, nextValue);
                    }}
                    value={condition.field}
                  />
                  <Select
                    ariaLabel={t("filters.conditionOperator", {
                      index: index + 1,
                    })}
                    className="max-lg:h-11"
                    onValueChange={(nextValue) => {
                      changeOperator(condition, nextValue);
                    }}
                    options={field.operators.map((operator) => ({
                      label: operatorLabel(operator),
                      value: operator,
                    }))}
                    value={condition.operator}
                  />
                  <div className="min-w-0">
                    {valueEditor(condition, field, validation)}
                  </div>
                  <IconButton
                    className="justify-self-end max-lg:size-11 @min-[680px]:justify-self-auto"
                    label={t("filters.removeCondition", {
                      index: index + 1,
                    })}
                    onClick={() => {
                      removeCondition(condition.id);
                    }}
                  >
                    <Trash2 aria-hidden="true" />
                  </IconButton>
                  {validation.status !== "valid" ? (
                    <p
                      className={cn(
                        "@min-[680px]:col-span-4 @min-[680px]:col-start-2",
                        validation.status === "invalid"
                          ? "text-destructive-text"
                          : "text-warning",
                      )}
                      role={
                        validation.status === "invalid"
                          ? "alert"
                          : undefined
                      }
                    >
                      {validationMessage(validation)}
                    </p>
                  ) : null}
                </div>
              );
            })
          : null}
      </div>

      <div className="flex flex-col gap-2 rounded-b-card border-t border-border bg-background px-3 py-2 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
        <span>
          {summary.total === 0
            ? t("filters.noRules")
            : [
                summary.validCount > 0
                  ? t("filters.rulesActive", {
                      total: summary.validCount,
                      mode: t(
                        rules.combinator === "ALL"
                          ? "filters.allHint"
                          : "filters.anyHint",
                      ),
                    })
                  : t("filters.noValidRules"),
                summary.incompleteCount > 0
                  ? t("filters.summaryIncomplete", {
                      total: summary.incompleteCount,
                    })
                  : null,
                summary.invalidCount > 0
                  ? t("filters.summaryInvalid", {
                      total: summary.invalidCount,
                    })
                  : null,
              ]
                .filter(Boolean)
                .join(" · ")}
        </span>
        <div className="flex flex-wrap items-center gap-1">
          <Button
            className="self-start max-md:min-h-11 sm:self-auto"
            disabled={summary.total === 0}
            onClick={() => {
              onChange({ combinator: "ALL", conditions: [] });
            }}
            size="sm"
            variant="ghost"
          >
            {t("filters.clearConditions")}
          </Button>
        </div>
      </div>
    </section>
  );
}
