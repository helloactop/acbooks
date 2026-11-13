import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { Select } from "../../src/components/select";

const OPTIONS = [
  { label: "最近添加", value: "recent" },
  { label: "书名", value: "title" },
];

function Harness() {
  const [value, setValue] = useState("recent");
  return (
    <>
      <label htmlFor="sort">排序</label>
      <Select
        ariaLabel="排序"
        id="sort"
        onValueChange={setValue}
        options={OPTIONS}
        value={value}
      />
    </>
  );
}

describe("Select", () => {
  it("opens the listbox and selects an option", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const trigger = screen.getByRole("combobox", { name: "排序" });

    expect(trigger).toHaveTextContent("最近添加");
    expect(trigger).toHaveAttribute("aria-expanded", "false");

    await user.click(trigger);
    const listbox = await screen.findByRole("listbox");
    expect(within(listbox).getAllByRole("option")).toHaveLength(2);
    expect(
      within(listbox).getByRole("option", { name: "最近添加" }),
    ).toHaveAttribute("aria-selected", "true");

    await user.click(
      within(listbox).getByRole("option", { name: "书名" }),
    );
    expect(trigger).toHaveTextContent("书名");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("supports keyboard navigation, selection and Escape", async () => {
    render(<Harness />);
    const trigger = screen.getByRole("combobox", { name: "排序" });

    fireEvent.keyDown(trigger, { key: "Enter" });
    expect(screen.getByRole("listbox")).toBeInTheDocument();

    fireEvent.keyDown(trigger, { key: "ArrowDown" });
    fireEvent.keyDown(trigger, { key: "Enter" });
    expect(trigger).toHaveTextContent("书名");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();

    fireEvent.keyDown(trigger, { key: "ArrowDown" });
    expect(screen.getByRole("listbox")).toBeInTheDocument();
    fireEvent.keyDown(trigger, { key: "Escape" });
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("renders grouped options", async () => {
    const user = userEvent.setup();
    render(
      <Select
        ariaLabel="字段"
        groups={[
          {
            label: "图书元数据",
            options: [
              { label: "书名", value: "title" },
              { label: "作者", value: "author" },
            ],
          },
        ]}
        onValueChange={() => undefined}
        value="title"
      />,
    );

    await user.click(screen.getByRole("combobox", { name: "字段" }));
    const group = await screen.findByRole("group", {
      name: "图书元数据",
    });
    expect(
      within(group).getAllByRole("option").map((option) => option.textContent),
    ).toEqual(["书名", "作者"]);
  });

  it("keeps disabled styling on the trigger", () => {
    render(
      <Select
        ariaLabel="排序"
        disabled
        onValueChange={() => undefined}
        options={OPTIONS}
        value="recent"
      />,
    );
    const trigger = screen.getByRole("combobox", { name: "排序" });
    expect(trigger).toBeDisabled();
    expect(trigger).toHaveClass("disabled:bg-disabled-surface");
  });

  it("portals the listbox inside an open dialog", async () => {
    const user = userEvent.setup();
    render(
      <dialog open>
        <Select
          ariaLabel="排序"
          onValueChange={() => undefined}
          options={OPTIONS}
          value="recent"
        />
      </dialog>,
    );
    const dialog = screen.getByRole("dialog");

    await user.click(screen.getByRole("combobox", { name: "排序" }));
    const listbox = await screen.findByRole("listbox");

    expect(dialog.contains(listbox)).toBe(true);
    expect(document.body.contains(listbox)).toBe(true);
  });
});
