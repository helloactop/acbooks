import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { Select } from "./select";

function Harness() {
  const [value, setValue] = useState("recent");
  return (
    <>
      <label htmlFor="sort">排序</label>
      <Select
        id="sort"
        onChange={(event) => {
          setValue(event.target.value);
        }}
        value={value}
      >
        <option value="recent">最近添加</option>
        <option value="title">书名</option>
      </Select>
    </>
  );
}

describe("Select", () => {
  it("uses native select semantics and updates the value", async () => {
    render(<Harness />);
    const select = screen.getByRole("combobox", { name: "排序" });

    expect(select).toHaveValue("recent");
    await userEvent.selectOptions(select, "title");
    expect(select).toHaveValue("title");
  });

  it("keeps disabled styling on the native select", () => {
    render(
      <Select aria-label="排序" disabled value="recent">
        <option value="recent">最近添加</option>
      </Select>,
    );
    const select = screen.getByRole("combobox", { name: "排序" });
    expect(select).toBeDisabled();
    expect(select).toHaveClass("disabled:bg-disabled-surface");
  });
});
