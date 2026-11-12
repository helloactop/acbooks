import { describe, expect, it } from "vitest";

import {
  serializeSmartFilterRules,
  summarizeSmartFilterRules,
  validateSmartFilterCondition,
  type BookFilterField,
  type SmartFilterCondition,
} from "../../src/library/filter-model";

const FIELDS: readonly BookFilterField[] = [
  {
    key: "title",
    group: "metadata",
    type: "text",
    operators: ["contains", "equals", "between"],
    allowCustom: false,
    options: [],
  },
  {
    key: "copyCount",
    group: "files",
    type: "number",
    operators: ["equals", "between"],
    allowCustom: false,
    options: [],
  },
  {
    key: "createdAt",
    group: "timeline",
    type: "date",
    operators: ["equals", "between"],
    allowCustom: false,
    options: [],
  },
];

function condition(
  overrides: Partial<SmartFilterCondition>,
): SmartFilterCondition {
  return {
    field: "title",
    id: "condition-1",
    operator: "equals",
    value: "呐喊",
    ...overrides,
  };
}

describe("smart filter validation", () => {
  it("rejects text over 500 characters", () => {
    expect(
      validateSmartFilterCondition(
        condition({ value: "长".repeat(501) }),
        FIELDS,
      ),
    ).toEqual({ reason: "length", status: "invalid" });
  });

  it("rejects reversed number and date ranges", () => {
    expect(
      validateSmartFilterCondition(
        condition({
          field: "copyCount",
          operator: "between",
          value: ["5", "1"],
        }),
        FIELDS,
      ),
    ).toEqual({ reason: "range", status: "invalid" });
    expect(
      validateSmartFilterCondition(
        condition({
          field: "createdAt",
          operator: "between",
          value: ["2026-12-31", "2026-01-01"],
        }),
        FIELDS,
      ),
    ).toEqual({ reason: "range", status: "invalid" });
  });

  it("separates incomplete, invalid and valid conditions", () => {
    const rules = {
      combinator: "ALL" as const,
      conditions: [
        condition({ id: "valid" }),
        condition({ id: "empty", value: "" }),
        condition({
          id: "range",
          field: "copyCount",
          operator: "between",
          value: ["5", "1"],
        }),
      ],
    };

    const summary = summarizeSmartFilterRules(rules, FIELDS);
    expect(summary.validCount).toBe(1);
    expect(summary.incompleteCount).toBe(1);
    expect(summary.invalidCount).toBe(1);
    expect(summary.validRules.conditions.map((item) => item.id)).toEqual([
      "valid",
    ]);
    expect(
      JSON.parse(serializeSmartFilterRules(rules, FIELDS)).conditions,
    ).toEqual([{ field: "title", operator: "equals", value: "呐喊" }]);
  });
});
