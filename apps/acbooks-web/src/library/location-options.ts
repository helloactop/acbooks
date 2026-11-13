import type { LocationNode } from "./locations-api";

export function locationPathLabel(
  location: LocationNode,
  byId: ReadonlyMap<string, LocationNode>,
): string {
  const parts: string[] = [];
  const seen = new Set<string>();
  let current: LocationNode | undefined = location;
  while (current !== undefined && !seen.has(current.id)) {
    seen.add(current.id);
    parts.unshift(current.name);
    current =
      current.parentId === null ? undefined : byId.get(current.parentId);
  }
  return parts.join(" / ");
}

export function locationOptions(
  locations: readonly LocationNode[],
): { label: string; value: string }[] {
  const byId = new Map(locations.map((location) => [location.id, location]));
  return locations
    .map((location) => ({
      label: locationPathLabel(location, byId),
      value: location.id,
    }))
    .sort((left, right) => left.label.localeCompare(right.label));
}