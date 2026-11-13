import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import {
  SegmentedControl,
  type SegmentedControlOption,
} from "../../src/components/segmented-control";

const OPTIONS: readonly SegmentedControlOption<"system" | "light" | "dark">[] = [
  { value: "system", label: "跟随系统" },
  { value: "light", label: "浅色" },
  { value: "dark", label: "深色" },
];

function Harness({ variant }: { variant?: "boxed" | "pill" }) {
  const [value, setValue] = useState<"system" | "light" | "dark">("system");
  return (
    <SegmentedControl
      ariaLabel="外观模式"
      onValueChange={setValue}
      options={OPTIONS}
      value={value}
      variant={variant}
    />
  );
}

describe("SegmentedControl", () => {
  it("exposes radio semantics and selects by click", async () => {
    render(<Harness />);

    expect(screen.getByRole("radiogroup", { name: "外观模式" })).toBeInTheDocument();
    const selected = screen.getByRole("radio", { name: "跟随系统" });
    expect(selected).toHaveAttribute("aria-checked", "true");
    expect(selected).toHaveClass("border-segment-selected-border");
    expect(screen.getByRole("radio", { name: "深色" })).toHaveAttribute(
      "aria-checked",
      "false",
    );
    expect(screen.getByRole("radio", { name: "深色" })).toHaveClass(
      "border-transparent",
    );

    await userEvent.click(screen.getByRole("radio", { name: "深色" }));
    expect(screen.getByRole("radio", { name: "深色" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  it("renders the rectangular boxed variant with a neutral selected state", () => {
    render(<Harness variant="boxed" />);
    const group = screen.getByRole("radiogroup", { name: "外观模式" });
    const selected = screen.getByRole("radio", { name: "跟随系统" });
    expect(group).toHaveClass("rounded-menu");
    expect(selected).toHaveClass(
      "rounded-control",
      "bg-background",
      "border-segment-selected-border",
    );
  });

  it("supports roving focus and arrow-key selection", async () => {
    render(<Harness />);

    const system = screen.getByRole("radio", { name: "跟随系统" });
    expect(system).toHaveAttribute("tabindex", "0");

    await userEvent.tab();
    expect(system).toHaveFocus();

    await userEvent.keyboard("{ArrowRight}");
    const light = screen.getByRole("radio", { name: "浅色" });
    expect(light).toHaveAttribute("aria-checked", "true");
    expect(light).toHaveFocus();

    await userEvent.keyboard("{End}");
    const dark = screen.getByRole("radio", { name: "深色" });
    expect(dark).toHaveAttribute("aria-checked", "true");
    expect(dark).toHaveFocus();

    await userEvent.keyboard("{Home}");
    expect(system).toHaveAttribute("aria-checked", "true");
    expect(system).toHaveFocus();
  });
});
