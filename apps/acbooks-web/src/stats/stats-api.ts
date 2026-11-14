import type { components } from "../../../../packages/acbooks-api-client/schema";
import { apiRequest } from "../api/client";

export type ReadingStats = components["schemas"]["ReadingStats"];
export type TimelinePage = components["schemas"]["ReadingPage_TimelineSession_"];
export interface StatsRange { startDate: string; endDate: string; timeZone: string }

export function getStats(range: StatsRange, signal: AbortSignal, bookId?: string) {
  const path = bookId === undefined ? "/stats/overview" : `/books/${encodeURIComponent(bookId)}/stats`;
  return apiRequest<ReadingStats>(`${path}?${new URLSearchParams({ ...range })}`, { signal });
}

export function getTimeline(range: StatsRange, offset: number, signal: AbortSignal, bookId?: string) {
  const query = new URLSearchParams({ ...range, offset: String(offset) });
  if (bookId !== undefined) query.set("bookId", bookId);
  return apiRequest<TimelinePage>(`/stats/timeline?${query}`, { signal });
}
