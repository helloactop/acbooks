import { fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { MultiValueInput } from "../../src/components/multi-value-input";
import { renderWithI18n } from "../render-with-i18n";

function Harness({ initial = [] }: { initial?: string[] }) {
  const [values, setValues] = useState<string[]>(initial);
  return (
    <MultiValueInput
      onChange={setValues}
      placeholder="添加名称"
      values={values}
    />
  );
}

describe("MultiValueInput", () => {
  it("renders chips and removes a value with its accessible name", async () => {
    const user = userEvent.setup();
    renderWithI18n(<Harness initial={["鲁迅", "沈从文"]} />);

    expect(screen.getByText("鲁迅")).toBeInTheDocument();
    const remove = screen.getByRole("button", { name: "移除 鲁迅" });
    expect(remove).toHaveClass("max-md:size-11");
    await user.click(remove);
    expect(screen.queryByText("鲁迅")).not.toBeInTheDocument();
    expect(screen.getByText("沈从文")).toBeInTheDocument();
  });

  it("adds values on Enter and comma without duplicating", async () => {
    const user = userEvent.setup();
    renderWithI18n(<Harness />);
    const input = screen.getByPlaceholderText("添加名称");

    await user.type(input, "鲁迅{Enter}");
    await user.type(input, "沈从文，");
    await user.type(input, "鲁迅{Enter}");

    expect(screen.getByText("鲁迅")).toBeInTheDocument();
    expect(screen.getByText("沈从文")).toBeInTheDocument();
    expect(screen.getAllByText("鲁迅")).toHaveLength(1);
    expect(input).toHaveValue("");
  });

  it("commits the draft on blur and deletes the last value with Backspace", async () => {
    const user = userEvent.setup();
    renderWithI18n(<Harness initial={["鲁迅"]} />);
    const input = screen.getByPlaceholderText("添加名称");

    fireEvent.keyDown(input, { key: "Backspace" });
    expect(screen.queryByText("鲁迅")).not.toBeInTheDocument();

    await user.type(input, "沈从文");
    fireEvent.blur(input);
    expect(screen.getByText("沈从文")).toBeInTheDocument();
  });

  it("disables the input and remove controls", () => {
    renderWithI18n(
      <MultiValueInput
        disabled
        onChange={() => undefined}
        placeholder="添加名称"
        values={["鲁迅"]}
      />,
    );

    expect(screen.getByPlaceholderText("添加名称")).toBeDisabled();
    expect(screen.getByRole("button", { name: "移除 鲁迅" })).toBeDisabled();
  });
});
