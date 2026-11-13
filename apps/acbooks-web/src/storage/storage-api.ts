import type { components } from "../../../../packages/acbooks-api-client/schema";

import {
  apiRequest,
  deleteWithCsrf,
  postWithCsrf,
} from "../api/client";

export type StorageRoot = components["schemas"]["StorageRootNode"];
export type StorageRootCreate = components["schemas"]["StorageRootCreate"];
export type ScanAccepted = components["schemas"]["ScanAccepted"];
export type JobStatus = components["schemas"]["JobStatus"];

export function listStorageRoots(
  signal?: AbortSignal,
): Promise<StorageRoot[]> {
  return apiRequest<StorageRoot[]>("/storage-roots", { signal });
}

export function createStorageRoot(
  body: StorageRootCreate,
): Promise<StorageRoot> {
  return postWithCsrf<StorageRoot>("/storage-roots", body);
}

export function deleteStorageRoot(rootId: string): Promise<void> {
  return deleteWithCsrf<void>(
    `/storage-roots/${encodeURIComponent(rootId)}`,
  );
}

export function startStorageScan(
  rootId: string,
  deep: boolean,
): Promise<ScanAccepted> {
  const query = deep ? "?deep=true" : "";
  return postWithCsrf<ScanAccepted>(
    `/storage-roots/${encodeURIComponent(rootId)}/scan${query}`,
  );
}

export function getJob(
  jobId: string,
  signal?: AbortSignal,
): Promise<JobStatus> {
  return apiRequest<JobStatus>(`/jobs/${encodeURIComponent(jobId)}`, {
    signal,
  });
}