export const DAY = 86_400_000;
export const dateLabel = (value: number) => new Date(value).toISOString().slice(0, 10);
export const dateValue = (label: string) => Date.parse(`${label}T00:00:00Z`);

export function localInput(value: number) {
  const date = new Date(value);
  return new Date(value - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 23);
}
