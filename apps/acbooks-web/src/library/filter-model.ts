import type { components } from "../../../../packages/acbooks-api-client/schema";

export type BookFilterSchema = components["schemas"]["BookFilterSchema"];
export type BookFilterField = components["schemas"]["BookFilterField"];
export type BookFilterOption = components["schemas"]["BookFilterOption"];
export type BookFilterOptionPage =
  components["schemas"]["BookFilterOptionPage"];
export type BookFilterFieldType = BookFilterField["type"];
export type BookFilterOptionSource = NonNullable<
  BookFilterField["optionSource"]
>;
export type FilterCombinator = "ALL" | "ANY";

export interface SmartFilterCondition {
  field: string;
  id: string;
  operator: string;
  value?: string | string[];
}

export interface SmartFilterRules {
  combinator: FilterCombinator;
  conditions: SmartFilterCondition[];
}

export type FilterConditionStatus = "incomplete" | "invalid" | "valid";

export type FilterConditionReason =
  | "date"
  | "field"
  | "length"
  | "number"
  | "operator"
  | "range"
  | "value";

export interface FilterConditionValidation {
  reason?: FilterConditionReason;
  status: FilterConditionStatus;
}

export interface SmartFilterSummary {
  incompleteCount: number;
  invalidCount: number;
  total: number;
  validCount: number;
  validRules: SmartFilterRules;
}

export const EMPTY_FILTER_RULES: SmartFilterRules = {
  combinator: "ALL",
  conditions: [],
};

const VALUELESS_OPERATORS = new Set([
  "is_empty",
  "is_not_empty",
  "is_true",
  "is_false",
]);

let conditionSequence = 0;

function nextConditionId(): string {
  conditionSequence += 1;
  return `filter-${Date.now()}-${conditionSequence}`;
}

export function needsConditionValue(operator: string): boolean {
  return !VALUELESS_OPERATORS.has(operator);
}

export function createSmartFilterCondition(
  fields: readonly BookFilterField[],
): SmartFilterCondition {
  const field = fields[0];
  return {
    field: field?.key ?? "title",
    id: nextConditionId(),
    operator: field?.operators[0] ?? "contains",
    value: "",
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readConditionValue(value: unknown): string | string[] | undefined {
  if (typeof value === "string") {
    return value;
  }
  if (
    Array.isArray(value) &&
    value.every((item) => typeof item === "string")
  ) {
    return value;
  }
  return undefined;
}

export function parseSmartFilterRules(value: unknown): SmartFilterRules {
  let parsed: unknown = value;
  if (typeof value === "string") {
    if (value.trim().length === 0) {
      return EMPTY_FILTER_RULES;
    }
    try {
      parsed = JSON.parse(value) as unknown;
    } catch {
      return EMPTY_FILTER_RULES;
    }
  }
  if (!isRecord(parsed)) {
    return EMPTY_FILTER_RULES;
  }

  const rawConditions = Array.isArray(parsed.conditions)
    ? parsed.conditions
    : [];
  const conditions: SmartFilterCondition[] = [];
  for (const [index, raw] of rawConditions.slice(0, 30).entries()) {
    if (!isRecord(raw)) {
      continue;
    }
    const field = raw.field;
    const operator = raw.operator;
    if (typeof field !== "string" || typeof operator !== "string") {
      continue;
    }
    const condition: SmartFilterCondition = {
      field,
      id: `stored-filter-${index}`,
      operator,
    };
    const conditionValue = readConditionValue(raw.value);
    if (conditionValue !== undefined) {
      condition.value = conditionValue;
    }
    conditions.push(condition);
  }

  return {
    combinator: parsed.combinator === "ANY" ? "ANY" : "ALL",
    conditions,
  };
}

export function smartFilterConditionComplete(
  condition: SmartFilterCondition,
): boolean {
  if (!needsConditionValue(condition.operator)) {
    return true;
  }
  if (condition.operator === "between") {
    return (
      Array.isArray(condition.value) &&
      condition.value.length === 2 &&
      condition.value.every((item) => item.trim().length > 0)
    );
  }
  return (
    !Array.isArray(condition.value) &&
    (condition.value ?? "").trim().length > 0
  );
}

function parseNumber(value: string): number | null {
  if (value.trim().length === 0) {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseDate(value: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  const parsed = Date.parse(`${value}T00:00:00Z`);
  if (!Number.isFinite(parsed)) {
    return null;
  }
  return new Date(parsed).toISOString().slice(0, 10) === value
    ? parsed
    : null;
}

function validateScalar(
  field: BookFilterField,
  value: string,
): FilterConditionValidation {
  if (value.trim().length === 0) {
    return { status: "incomplete" };
  }
  if (field.type === "text" && value.length > 500) {
    return { reason: "length", status: "invalid" };
  }
  if (field.type === "number" && parseNumber(value) === null) {
    return { reason: "number", status: "invalid" };
  }
  if (field.type === "date" && parseDate(value) === null) {
    return { reason: "date", status: "invalid" };
  }
  if (
    field.type === "select" &&
    field.allowCustom === false &&
    (field.options?.length ?? 0) > 0 &&
    !(field.options ?? []).some((option) => option.value === value)
  ) {
    return { reason: "value", status: "invalid" };
  }
  return { status: "valid" };
}

export function validateSmartFilterCondition(
  condition: SmartFilterCondition,
  fields: readonly BookFilterField[],
): FilterConditionValidation {
  const field =
    fields.length === 0
      ? undefined
      : fields.find((candidate) => candidate.key === condition.field);
  if (fields.length > 0 && field === undefined) {
    return { reason: "field", status: "invalid" };
  }
  if (
    field !== undefined &&
    !field.operators.includes(condition.operator)
  ) {
    return { reason: "operator", status: "invalid" };
  }
  if (!needsConditionValue(condition.operator)) {
    return { status: "valid" };
  }
  if (condition.operator === "between") {
    if (
      !Array.isArray(condition.value) ||
      condition.value.length !== 2 ||
      condition.value.some((item) => item.trim().length === 0)
    ) {
      return smartFilterConditionComplete(condition)
        ? { reason: "range", status: "invalid" }
        : { status: "incomplete" };
    }
    if (field === undefined) {
      return { status: "valid" };
    }
    const start = condition.value[0] ?? "";
    const end = condition.value[1] ?? "";
    const first = validateScalar(field, start);
    if (first.status !== "valid") {
      return first;
    }
    const second = validateScalar(field, end);
    if (second.status !== "valid") {
      return second;
    }
    if (field.type === "number") {
      const startNumber = parseNumber(start);
      const endNumber = parseNumber(end);
      if (
        startNumber !== null &&
        endNumber !== null &&
        startNumber > endNumber
      ) {
        return { reason: "range", status: "invalid" };
      }
    } else if (field.type === "date") {
      const startDate = parseDate(start);
      const endDate = parseDate(end);
      if (
        startDate !== null &&
        endDate !== null &&
        startDate > endDate
      ) {
        return { reason: "range", status: "invalid" };
      }
    }
    return { status: "valid" };
  }

  if (Array.isArray(condition.value)) {
    return { status: "invalid", reason: "value" };
  }
  const value = condition.value ?? "";
  if (value.trim().length === 0) {
    return { status: "incomplete" };
  }
  return field === undefined
    ? { status: "valid" }
    : validateScalar(field, value);
}

export function summarizeSmartFilterRules(
  rules: SmartFilterRules,
  fields: readonly BookFilterField[],
): SmartFilterSummary {
  let incompleteCount = 0;
  let invalidCount = 0;
  const validConditions: SmartFilterCondition[] = [];
  for (const condition of rules.conditions) {
    const validation = validateSmartFilterCondition(condition, fields);
    if (validation.status === "valid") {
      validConditions.push(condition);
    } else if (validation.status === "incomplete") {
      incompleteCount += 1;
    } else {
      invalidCount += 1;
    }
  }
  return {
    incompleteCount,
    invalidCount,
    total: rules.conditions.length,
    validCount: validConditions.length,
    validRules: {
      combinator: rules.combinator,
      conditions: validConditions,
    },
  };
}

export function applicableSmartFilterRules(
  rules: SmartFilterRules,
): SmartFilterRules {
  return {
    combinator: rules.combinator,
    conditions: rules.conditions.filter(smartFilterConditionComplete),
  };
}

export function serializeSmartFilterRules(
  rules: SmartFilterRules,
  fields?: readonly BookFilterField[],
): string {
  const applicable =
    fields !== undefined && fields.length > 0
      ? summarizeSmartFilterRules(rules, fields).validRules
      : applicableSmartFilterRules(rules);
  if (applicable.conditions.length === 0) {
    return "";
  }
  return JSON.stringify({
    combinator: applicable.combinator,
    conditions: applicable.conditions.map(({ field, operator, value }) => ({
      field,
      operator,
      ...(value === undefined ? {} : { value }),
    })),
  });
}

export function filterRulesFromTagAndFormat(
  tag: string,
  format: string,
): SmartFilterRules {
  const conditions: SmartFilterCondition[] = [];
  if (tag.trim().length > 0) {
    conditions.push({
      field: "tag",
      id: nextConditionId(),
      operator: "equals",
      value: tag.trim(),
    });
  }
  if (format.trim().length > 0) {
    conditions.push({
      field: "format",
      id: nextConditionId(),
      operator: "equals",
      value: format.trim().toLocaleLowerCase(),
    });
  }
  return { combinator: "ALL", conditions };
}
