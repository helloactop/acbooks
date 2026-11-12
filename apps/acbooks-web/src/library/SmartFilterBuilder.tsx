import {
  Button,
  IconButton,
  Input,
  Select,
  Spinner,
  cn,
} from "@acbooks/ui";
import { Database, Plus, RotateCcw, Trash2, WandSparkles } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

import { AsyncFilterValueEditor } from "./AsyncFilterValueEditor";
import {
  createSmartFilterCondition,
  needsConditionValue,
  type BookFilterField,
  type BookFilterOption,
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
  "grid gap-2 rounded-card border border-border bg-background p-3 md:grid-cols-[34px_minmax(170px,0.85fr)_minmax(132px,0.62fr)_minmax(190px,1.35fr)_42px] md:items-center";

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
  ) => {
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
          aria-label={t("filters.valueLabel", {
            field: fieldLabel(field),
          })}
          className="max-lg:h-11"
          onChange={(event) => {
            updateCondition(condition.id, { value: event.target.value });
          }}
          value={value}
        >
          <option value="">{t("filters.selectValue")}</option>
          {(field.options ?? []).map((option) => (
            <option key={option.value} value={option.value}>
              {optionLabel(field, option)}
            </option>
          ))}
        </Select>
      );
    }

    return (
      <div className="relative min-w-0">
        <Input
          aria-label={t("filters.valueLabel", { field: fieldLabel(field) })}
          className={cn("max-lg:h-11", field.unit ? "pr-12" : undefined)}
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
      className="overflow-hidden rounded-card border border-border bg-surface"
      id="library-smart-filter-panel"
    >
      <div className="flex flex-col gap-3 border-b border-border px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-start gap-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-control bg-accent-soft text-accent-text">
            <WandSparkles aria-hidden="true" className="size-[18px]" />
          </span>
          <div className="min-w-0">
            <h2 className="font-semibold text-foreground">
              {t("filters.smartTitle")}
            </h2>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-muted-foreground">
            {t("filters.match")}
          </span>
          <Select
            aria-label={t("filters.combinator")}
            className="h-10 w-[160px] max-lg:h-11"
            onChange={(event) => {
              onChange({
                ...rules,
                combinator: event.target.value === "ANY" ? "ANY" : "ALL",
              });
            }}
            value={rules.combinator}
          >
            <option value="ALL">{t("filters.allConditions")}</option>
            <option value="ANY">{t("filters.anyCondition")}</option>
          </Select>
          <Button
            className="h-10 max-lg:min-h-11"
            disabled={loading || !hasFields || atLimit}
            onClick={addCondition}
            variant="soft"
          >
            <Plus aria-hidden="true" />
            {t("filters.addCondition")}
          </Button>
        </div>
      </div>

      <div className="space-y-2.5 p-4">
        {loading ? (
          <div className="flex min-h-28 items-center justify-center gap-2 text-sm text-muted-foreground">
            <Spinner label={t("filters.loadingSchema")} />
            <span>{t("filters.loadingSchema")}</span>
          </div>
        ) : null}

        {!loading && error !== undefined ? (
          <div className="flex min-h-28 flex-col items-center justify-center gap-3 rounded-card border border-border bg-background px-6 text-center">
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
            <Database
              aria-hidden="true"
              className="size-5 text-muted-foreground"
            />
            <span className="mt-2 text-sm font-medium">
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

              return (
                <div className={ROW_GRID} key={condition.id}>
                  <span className="hidden h-7 w-7 items-center justify-center rounded-control bg-surface text-xs font-semibold text-muted-foreground md:flex">
                    {index + 1}
                  </span>
                  <Select
                    aria-label={t("filters.conditionField", {
                      index: index + 1,
                    })}
                    className="w-full min-w-0 max-lg:h-11"
                    onChange={(event) => {
                      changeField(condition, event.target.value);
                    }}
                    value={condition.field}
                  >
                    {groups.map(([group, groupFields]) => (
                      <optgroup key={group} label={groupLabel(group)}>
                        {groupFields.map((groupField) => (
                          <option
                            key={groupField.key}
                            value={groupField.key}
                          >
                            {fieldLabel(groupField)}
                          </option>
                        ))}
                      </optgroup>
                    ))}
                  </Select>
                  <Select
                    aria-label={t("filters.conditionOperator", {
                      index: index + 1,
                    })}
                    className="w-full min-w-0 max-lg:h-11"
                    onChange={(event) => {
                      changeOperator(condition, event.target.value);
                    }}
                    value={condition.operator}
                  >
                    {field.operators.map((operator) => (
                      <option key={operator} value={operator}>
                        {operatorLabel(operator)}
                      </option>
                    ))}
                  </Select>
                  <div className="min-w-0">
                    {valueEditor(condition, field)}
                  </div>
                  <IconButton
                    className="justify-self-end max-lg:size-11 md:justify-self-auto"
                    label={t("filters.removeCondition", {
                      index: index + 1,
                    })}
                    onClick={() => {
                      removeCondition(condition.id);
                    }}
                  >
                    <Trash2 aria-hidden="true" />
                  </IconButton>
                </div>
              );
            })
          : null}
      </div>

      <div className="flex flex-col gap-2 border-t border-border bg-background px-4 py-3 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
        <span>
          {rules.conditions.length > 0
            ? t("filters.rulesActive", {
                total: rules.conditions.length,
                mode: t(
                  rules.combinator === "ALL"
                    ? "filters.allHint"
                    : "filters.anyHint",
                ),
              })
            : t("filters.noRules")}
        </span>
        <Button
          className="self-start max-lg:min-h-11 sm:self-auto"
          disabled={rules.conditions.length === 0}
          onClick={() => {
            onChange({ combinator: "ALL", conditions: [] });
          }}
          size="sm"
          variant="ghost"
        >
          <RotateCcw aria-hidden="true" />
          {t("filters.clearConditions")}
        </Button>
      </div>
    </section>
  );
}
