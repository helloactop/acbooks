import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { Combobox, type ComboboxOption } from "./combobox";

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
});
