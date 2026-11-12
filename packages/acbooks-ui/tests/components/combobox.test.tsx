import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { Combobox, type ComboboxOption } from "../../src/components/combobox";

const OPTIONS: readonly ComboboxOption[] = [
  { value: "Alpha", label: "Alpha", count: 2 },
  { value: "Beta", label: "Beta", count: 1 },
  { value: "Gamma", label: "Gamma" },
];

function Harness({ initial = "" }: { initial?: string }) {
  const [value, setValue] = useState(initial);
  return (
    <Combobox
      ariaLabel="Author"
      emptyLabel="No matches"
      onChange={setValue}
      options={OPTIONS}
      placeholder="Choose or type"
      value={value}
    />
  );
}

describe("Combobox", () => {
  it("filters options and selects with the mouse", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByRole("combobox", { name: "Author" });

    await user.click(input);
    expect(screen.getByRole("listbox")).toBeInTheDocument();
    expect(screen.getAllByRole("option")).toHaveLength(3);

    await user.type(input, "be");
    expect(screen.getAllByRole("option")).toHaveLength(1);
    await user.click(screen.getByRole("option", { name: /Beta/ }));

    expect(input).toHaveValue("Beta");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("supports keyboard selection and custom values", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByRole("combobox", { name: "Author" });

    await user.click(input);
    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("option", { name: /Alpha/ })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    await user.keyboard("{Enter}");
    expect(input).toHaveValue("Alpha");

    await user.clear(input);
    await user.type(input, "Custom author");
    expect(input).toHaveValue("Custom author");
    expect(screen.getByText("No matches")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("portals the menu outside clipping ancestors and flips near the viewport edge", async () => {
    const user = userEvent.setup();
    const { container } = render(
      <div style={{ height: 40, overflow: "hidden" }}>
        <Harness />
      </div>,
    );
    const input = screen.getByRole("combobox", { name: "Author" });
    vi.spyOn(input, "getBoundingClientRect").mockReturnValue({
      bottom: 380,
      height: 40,
      left: 20,
      right: 220,
      top: 340,
      width: 200,
      x: 20,
      y: 340,
      toJSON: () => ({}),
    });
    vi.spyOn(window, "innerHeight", "get").mockReturnValue(400);

    await user.click(input);
    const menu = screen.getByRole("listbox");
    expect(container.contains(menu)).toBe(false);
    expect(menu).toHaveClass("fixed");
    expect(menu.style.bottom).not.toBe("");
    expect(menu.style.top).toBe("");
    vi.restoreAllMocks();
  });
});
