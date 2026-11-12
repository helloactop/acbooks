import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { renderWithI18n } from "../render-with-i18n";
import { Field } from "../../src/components/field";
import { Input } from "../../src/components/input";
import { PasswordInput } from "../../src/components/password-input";

describe("form components", () => {
  it("associates Field labels, descriptions and errors with the control", () => {
    render(
      <Field
        description="用于登录 acbooks"
        error="请输入用户名"
        label="用户名"
        required
      >
        <Input />
      </Field>,
    );

    const input = screen.getByLabelText("用户名");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute("aria-required", "true");
    expect(input).toHaveAttribute(
      "aria-errormessage",
      `${input.id}-error`,
    );
    expect(input).toHaveAccessibleDescription("用于登录 acbooks 请输入用户名");
  });

  it("marks fields invalid without rendering an inline error", () => {
    render(
      <Field invalid label="用户名">
        <Input />
      </Field>,
    );

    expect(screen.getByLabelText("用户名")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("toggles password visibility without changing the input value", async () => {
    renderWithI18n(
      <PasswordInput aria-label="密码" defaultValue="secret-value" />,
    );

    const input = screen.getByLabelText("密码");
    expect(input).toHaveAttribute("type", "password");

    await userEvent.click(screen.getByRole("button", { name: "显示密码" }));
    expect(input).toHaveAttribute("type", "text");
    expect(input).toHaveValue("secret-value");

    const hideButton = screen.getByRole("button", { name: "隐藏密码" });
    expect(hideButton).toHaveAttribute("aria-pressed", "true");
    expect(hideButton).toHaveAttribute("aria-controls", input.id);
  });

  it("disables the visibility control together with the password input", () => {
    renderWithI18n(
      <PasswordInput aria-label="密码" defaultValue="secret" disabled />,
    );

    expect(screen.getByLabelText("密码")).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "显示密码" }),
    ).toBeDisabled();
  });

  it("localizes password visibility controls", async () => {
    renderWithI18n(
      <PasswordInput aria-label="Password" defaultValue="secret" />,
      "en",
    );

    await userEvent.click(screen.getByRole("button", { name: "Show password" }));
    expect(
      screen.getByRole("button", { name: "Hide password" }),
    ).toHaveAttribute("aria-pressed", "true");
  });

  it("preserves the auth control height in the disabled state", () => {
    render(
      <Input
        aria-label="禁用输入"
        controlSize="auth"
        defaultValue="不可编辑"
        disabled
      />,
    );

    const input = screen.getByLabelText("禁用输入");
    expect(input).toBeDisabled();
    expect(input).toHaveClass("h-11");
    expect(input).toHaveClass("border-control-border");
    expect(input).toHaveClass("disabled:bg-disabled-surface");
    expect(input).toHaveClass("disabled:border-disabled-border");
  });
});
