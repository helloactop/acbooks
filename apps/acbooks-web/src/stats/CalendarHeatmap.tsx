/*
 * Adapted from shadcn-calendar-heatmap by Gurbaaz Singh Nandra (2024), MIT.
 * Source and full copyright/permission notice are recorded in LICENSE.md.
 */

import { DayPicker, type DayPickerProps } from "react-day-picker";

type CalendarProps = DayPickerProps & {
  variantClassnames: string[];
  datesPerVariant: Date[][];
};

export function CalendarHeatmap({ variantClassnames, datesPerVariant, ...props }: CalendarProps) {
  const variantLabels = variantClassnames.map((_, index) => `__variant${index}`);
  const modifiers = Object.fromEntries(variantLabels.map((key, index) => [key, datesPerVariant[index] ?? []]));
  const modifiersClassNames = Object.fromEntries(variantClassnames.map((className, index) => [`__variant${index}`, className]));
  return <DayPicker {...props} modifiers={modifiers} modifiersClassNames={modifiersClassNames} />;
}
