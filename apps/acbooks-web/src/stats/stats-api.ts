import type { components, operations } from "../../../../packages/acbooks-api-client/schema";
import { apiRequest, deleteWithCsrf, patchWithCsrf, postWithCsrf } from "../api/client";

export type ReadingStats = components["schemas"]["ReadingStats"];
export type TimelinePage = components["schemas"]["ReadingPage_TimelineSession_"];
export type TimelineSession = components["schemas"]["TimelineSession"];
export type DailyReadingStats = components["schemas"]["DailyReadingStats"];
export interface StatsRange { startDate: string; endDate: string; timeZone: string }
export type StatsSelection = StatsRange | { timeZone: string };
type SessionTimes = Pick<components["schemas"]["SessionRead"], "startedAt" | "endedAt" | "activeSeconds">;
type CreateResult = operations["createReadingSession"]["responses"][200]["content"]["application/json"];

export function getStats(range: StatsSelection, signal: AbortSignal, bookId?: string) {
  const path = bookId === undefined ? "/stats/overview" : `/books/${encodeURIComponent(bookId)}/stats`;
  return apiRequest<ReadingStats>(`${path}?${new URLSearchParams({ ...range })}`, { signal });
}

export function getTimeline(range: StatsSelection, offset: number, signal: AbortSignal, bookId?: string) {
  const query = new URLSearchParams({ ...range, offset: String(offset), includeInactive: "true" });
  if (bookId !== undefined) query.set("bookId", bookId);
  return apiRequest<TimelinePage>(`/stats/timeline?${query}`, { signal });
}

export function getDailyStats(day: string, timeZone: string, offset: number, signal: AbortSignal, bookId?: string) {
  const query = new URLSearchParams({ day, timeZone, offset: String(offset) });
  if (bookId !== undefined) query.set("bookId", bookId);
  return apiRequest<DailyReadingStats>(`/stats/daily?${query}`, { signal });
}

function sessionPath(bookId: string, id?: string) {
  const path = `/books/${encodeURIComponent(bookId)}/reading-sessions`;
  return id === undefined ? path : `${path}/${encodeURIComponent(id)}`;
}

export function createSession(bookId: string, times: SessionTimes, deviceId: string, mutationId: string) {
  return postWithCsrf<CreateResult>(sessionPath(bookId), { ...times, deviceId, mutationId, source: "manual" });
}

export function updateSession(session: TimelineSession, times: SessionTimes) {
  return patchWithCsrf(sessionPath(session.bookId, session.id), times);
}

export function deleteSession(session: TimelineSession) {
  return deleteWithCsrf(sessionPath(session.bookId, session.id));
}
