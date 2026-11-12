import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import { THEME_BROWSER_COLORS } from "./theme-types";

const css = readFileSync(
  resolve(process.cwd(), "src/styles/theme.css"),
  "utf8",
);

type Tokens = Record<string, string>;

function readTokens(selector: string): Tokens {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = new RegExp(`${escaped}\\s*\\{`).exec(css);
  if (match === null) {
    throw new Error(`Missing CSS selector: ${selector}`);
  }
  const blockStart = match.index + match[0].length - 1;
  const blockEnd = css.indexOf("}", blockStart);
  const block = css.slice(blockStart + 1, blockEnd);
  const tokens: Tokens = {};

  for (const match of block.matchAll(
    /--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6});/g,
  )) {
    const [, name, value] = match;
    if (name !== undefined && value !== undefined) {
      tokens[name] = value;
    }
  }
  return tokens;
}

function channel(value: number): number {
  const normalized = value / 255;
  return normalized <= 0.04045
    ? normalized / 12.92
    : ((normalized + 0.055) / 1.055) ** 2.4;
}

function luminance(hex: string): number {
  const value = hex.slice(1);
  const [red = 0, green = 0, blue = 0] = [0, 2, 4].map((offset) =>
    channel(Number.parseInt(value.slice(offset, offset + 2), 16)),
  );
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function contrast(foreground: string, background: string): number {
  const [lighter = 0, darker = 0] = [
    luminance(foreground),
    luminance(background),
  ].sort((left, right) => right - left);
  return (lighter + 0.05) / (darker + 0.05);
}

const light = readTokens(":root");
const dark = { ...light, ...readTokens('[data-theme="dark"]') };

const textPairs: ReadonlyArray<readonly [string, string]> = [
  ["foreground", "background"],
  ["foreground", "surface"],
  ["foreground", "surface-hover"],
  ["foreground", "surface-active"],
  ["muted-foreground", "background"],
  ["muted-foreground", "surface"],
  ["muted-foreground", "surface-hover"],
  ["accent-text", "background"],
  ["accent-text", "surface"],
  ["accent-text", "accent-soft"],
  ["accent-text", "accent-hover"],
  ["accent-text", "accent-active"],
  ["destructive-text", "background"],
  ["destructive-text", "surface"],
  ["destructive-foreground", "destructive"],
  ["success", "background"],
  ["success", "surface"],
  ["warning", "background"],
  ["warning", "surface"],
  ["disabled-foreground", "disabled-surface"],
];

const nonTextPairs: ReadonlyArray<readonly [string, string]> = [
  ["control-border", "background"],
  ["control-border", "surface"],
  ["accent-border", "background"],
  ["accent-border", "surface"],
  ["accent-border", "accent-soft"],
  ["accent-border", "accent-hover"],
  ["accent-border", "accent-active"],
  ["segment-selected-border", "background"],
  ["segment-selected-border", "surface"],
  ["ring", "background"],
  ["ring", "surface"],
];

describe("theme contrast", () => {
  it("keeps browser theme colors aligned with the frame tokens", () => {
    expect(THEME_BROWSER_COLORS.light.toLowerCase()).toBe(
      light.frame?.toLowerCase(),
    );
    expect(THEME_BROWSER_COLORS.dark.toLowerCase()).toBe(
      dark.frame?.toLowerCase(),
    );
  });

  it("keeps text and control tokens above the documented contrast floors", () => {
    const failures: string[] = [];

    for (const [themeName, tokens] of [
      ["light", light],
      ["dark", dark],
    ] as const) {
      const checkPair = (
        foreground: string,
        background: string,
        minimum: number,
      ) => {
        const foregroundValue = tokens[foreground];
        const backgroundValue = tokens[background];
        if (foregroundValue === undefined || backgroundValue === undefined) {
          failures.push(
            `${themeName}: missing token --${foregroundValue === undefined ? foreground : background}`,
          );
          return;
        }
        const value = contrast(foregroundValue, backgroundValue);
        if (value < minimum) {
          failures.push(
            `${themeName}: ${foreground} on ${background} is ${value.toFixed(2)}:1, expected at least ${minimum}:1`,
          );
        }
      };

      for (const [foreground, background] of textPairs) {
        checkPair(foreground, background, 4.5);
      }

      for (const [foreground, background] of nonTextPairs) {
        checkPair(foreground, background, 3);
      }
    }

    expect(failures).toEqual([]);
  });
});
