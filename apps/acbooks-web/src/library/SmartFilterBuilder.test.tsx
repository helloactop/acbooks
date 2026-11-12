import { I18nProvider } from "@acbooks/ui";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { appResources } from "../i18n/resources";
import type {
  BookFilterField,
  SmartFilterRules,
} from "./filter-model";
import { SmartFilterBuilder } from "./SmartFilterBuilder";

const FIELDS: readonly BookFilterField[] = [
  {
    key: "copyCount",
    group: "files",
    type: "number",
    operators: ["between", "greater_than"],
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
  {
    key: "hasCover",
    group: "timeline",
    type: "boolean",
    operators: ["is_true", "is_false"],
    allowCustom: false,
    options: [],
  },
];

const INITIAL_RULES: SmartFilterRules = {
  combinator: "ALL",
  conditions: [
    {
      field: "copyCount",
      id: "condition-1",
      operator: "between",
      value: ["1", "5"],
    },
  ],
};

function Harness() {
  const [rules, setRules] = useState<SmartFilterRules>(INITIAL_RULES);
  return (
    <I18nProvider defaultLanguage="zh-CN" resources={appResources}>
      <SmartFilterBuilder
        fields={FIELDS}
        loading={false}
        maxConditions={30}
        onChange={setRules}
        onRetry={() => undefined}
        rules={rules}
      />
    </I18nProvider>
  );
}

describe("SmartFilterBuilder", () => {
  it("switches value editors for number, date and boolean fields", async () => {
    const user = userEvent.setup();
    render(<Harness />);

    const start = screen.getByRole("spinbutton", {
      name: "实体副本数起始值",
    });
    const end = screen.getByRole("spinbutton", {
      name: "实体副本数结束值",
    });
    expect(start).toHaveValue(1);
    expect(end).toHaveValue(5);

    await user.selectOptions(
      screen.getByRole("combobox", { name: "第 1 条筛选字段" }),
      "hasCover",
    );
    expect(screen.getByText("无需填写值")).toBeInTheDocument();

    await user.selectOptions(
      screen.getByRole("combobox", { name: "第 1 条筛选字段" }),
      "createdAt",
    );
    const dateValue = screen.getByLabelText("加入时间筛选值");
    expect(dateValue).toHaveAttribute("type", "date");

    await user.selectOptions(
      screen.getByRole("combobox", { name: "第 1 条筛选运算符" }),
      "between",
    );
    expect(
      screen.getByLabelText("加入时间起始值"),
    ).toHaveAttribute("type", "date");
    expect(screen.getByLabelText("加入时间结束值")).toHaveAttribute(
      "type",
      "date",
    );
  });
});
