import type { components } from "../../../../packages/acbooks-api-client/schema";

import {
  apiRequest,
  deleteWithCsrf,
  patchWithCsrf,
  postWithCsrf,
} from "../api/client";

export type LocationNode = components["schemas"]["LocationNode"];
export type LocationCreate = components["schemas"]["LocationCreate"];
export type LocationUpdate = components["schemas"]["LocationUpdate"];

export function listLocations(signal?: AbortSignal): Promise<LocationNode[]> {
  return apiRequest<LocationNode[]>("/locations", { signal });
}

export function createLocation(body: LocationCreate): Promise<LocationNode> {
  return postWithCsrf<LocationNode>("/locations", body);
}

export function updateLocation(
  locationId: string,
  body: LocationUpdate,
): Promise<LocationNode> {
  return patchWithCsrf<LocationNode>(
    `/locations/${encodeURIComponent(locationId)}`,
    body,
  );
}

export function deleteLocation(locationId: string): Promise<void> {
  return deleteWithCsrf<void>(`/locations/${encodeURIComponent(locationId)}`);
}