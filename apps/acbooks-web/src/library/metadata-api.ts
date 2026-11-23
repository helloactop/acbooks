import type { components } from "../../../../packages/acbooks-api-client/schema";
import { apiRequest, toApiError } from "../api/client";

export type Candidate = components["schemas"]["Candidate"];
export type MetadataSource = "douban" | "openlibrary";

export function searchMetadata(query: string, source: MetadataSource, signal?: AbortSignal): Promise<Candidate[]> {
  const params = new URLSearchParams({ q: query.trim(), source });
  return apiRequest<Candidate[]>(`/metadata/candidates?${params}`, { signal });
}

export function normalizeIsbn(value: string): string | undefined {
  const isbn = value.normalize("NFKC").replace(/[\s-]/g, "").toUpperCase();
  return /^(?:[0-9]{9}[0-9X]|[0-9]{13})$/.test(isbn) ? isbn : undefined;
}

function isbnEdition(isbn: string): string {
  if (isbn.length === 10) return isbn.slice(0, -1);
  return isbn.startsWith("978") ? isbn.slice(3, -1) : isbn;
}

export async function findMetadataByIsbn(isbn: string, signal: AbortSignal): Promise<Candidate | undefined> {
  let providerError: unknown;
  for (const source of ["douban", "openlibrary"] as const) {
    try {
      const candidates = await searchMetadata(isbn, source, signal);
      const match = candidates.find((candidate) => {
        const candidateIsbn = normalizeIsbn(candidate.isbn ?? "");
        return candidateIsbn === isbn || (
          candidateIsbn !== undefined && candidateIsbn.length !== isbn.length &&
          isbnEdition(candidateIsbn) === isbnEdition(isbn)
        );
      });
      if (match !== undefined) return match;
    } catch (cause) {
      if (toApiError(cause).status !== 502) throw cause;
      providerError = cause;
    }
  }
  if (providerError !== undefined) throw providerError;
  return undefined;
}
