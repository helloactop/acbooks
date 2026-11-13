import type { BookCopyCreate } from "./copies-api";

export interface CopyDraft {
  acquiredAt: string;
  locationId: string | null;
  note: string;
}

export function emptyCopyDraft(): CopyDraft {
  return { acquiredAt: "", locationId: null, note: "" };
}

function dateToMillis(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    return null;
  }
  const parsed = Date.parse(`${trimmed}T00:00:00Z`);
  return Number.isNaN(parsed) ? null : parsed;
}

export function copyDraftToBody(draft: CopyDraft): BookCopyCreate {
  return {
    acquiredAt: dateToMillis(draft.acquiredAt),
    locationId: draft.locationId,
    note: draft.note.trim().length > 0 ? draft.note.trim() : null,
  };
}