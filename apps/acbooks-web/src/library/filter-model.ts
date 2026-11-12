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
): string {
  const applicable = applicableSmartFilterRules(rules);
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
