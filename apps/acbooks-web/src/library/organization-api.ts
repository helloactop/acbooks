import type { components, operations } from "../../../../packages/acbooks-api-client/schema";
import { apiRequest, deleteWithCsrf, patchWithCsrf, postWithCsrf } from "../api/client";

export type OrganizationKind = operations["listOrganizationEntries"]["parameters"]["path"]["kind"];
export type OrganizationEntry = components["schemas"]["OrganizationEntry"];
export type DuplicatePage = components["schemas"]["DuplicatePage"];
export type MergePair = components["schemas"]["BookMergePair"];
export type MergePreview = components["schemas"]["BookMergePreview"];

export const ORGANIZATION_PAGE_SIZE = 20;
const root = "/organization";

export function listEntries(kind: OrganizationKind, offset: number, signal?: AbortSignal) {
  return apiRequest<components["schemas"]["OrganizationPage"]>(
    `${root}/${kind}?offset=${offset}&limit=${ORGANIZATION_PAGE_SIZE}`, { signal },
  );
}

export function listDuplicates(offset: number, signal?: AbortSignal) {
  return apiRequest<DuplicatePage>(
    `${root}/duplicates?offset=${offset}&limit=${ORGANIZATION_PAGE_SIZE}`, { signal },
  );
}

export function renameEntry(kind: OrganizationKind, id: string, name: string) {
  return patchWithCsrf<void>(`${root}/${kind}/${id}`, { name });
}

export function deleteEntry(kind: OrganizationKind, id: string) {
  return deleteWithCsrf<void>(`${root}/${kind}/${id}`);
}

export function mergeEntries(kind: OrganizationKind, id: string, targetId: string) {
  return postWithCsrf<void>(`${root}/${kind}/${id}/merge`, { targetId });
}

export function previewMerge(pair: MergePair) {
  return postWithCsrf<MergePreview>(`${root}/books/merge-preview`, pair);
}

export function mergeBooks(payload: components["schemas"]["BookMergeRequest"]) {
  return postWithCsrf<void>(`${root}/books/merge`, payload);
}

export function reassociateFile(fileId: string, pair: MergePair) {
  return patchWithCsrf<void>(`${root}/files/${fileId}/book`, pair);
}
