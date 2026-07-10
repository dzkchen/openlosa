import { apiRequest, appendQueryParam } from "./client";

export type FeedJob = {
  id: number;
  companyName: string;
  title: string;
  url: string;
  location: string | null;
  sourceAts: string;
  sponsorship: string | null;
  postedAt: string | null;
  firstSeenAt: string;
  lastSeenAt: string;
  open: boolean;
  hidden: boolean;
  savedProspectId: number | null;
  createdApplicationId: number | null;
};

export type PagedResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type FeedIngestStatus = "SUCCESS" | "SKIPPED" | "FAILED";

export type FeedIngestRunSummary = {
  ranAt: string;
  status: FeedIngestStatus;
  message: string | null;
  jobsSeen: number;
  jobsNew: number;
  jobsClosed: number;
};

export type FeedHealth = {
  lastRun: FeedIngestRunSummary | null;
  lastSuccessAt: string | null;
  openJobs: number;
  stale: boolean;
  staleAfterHours: number;
};

export type FeedSortField = "company" | "title" | "sponsorship" | "postedAt" | "lastSeenAt";

export type FeedListParams = {
  q?: string;
  sponsorship?: string;
  open?: boolean;
  hidden?: boolean;
  postedFrom?: string;
  postedTo?: string;
  sort?: FeedSortField;
  dir?: "asc" | "desc";
  page?: number;
  size?: number;
};

// Prefix for all job-list queries; mutations invalidate this without touching
// ["feed", "health"], which only changes when an ingest runs.
export function feedJobsQueryKey() {
  return ["feed", "jobs"] as const;
}

export function feedQueryKey(params: FeedListParams) {
  return [...feedJobsQueryKey(), params] as const;
}

export async function listFeedJobs(params: FeedListParams = {}) {
  const searchParams = new URLSearchParams();
  appendQueryParam(searchParams, "q", params.q?.trim());
  appendQueryParam(searchParams, "sponsorship", params.sponsorship);
  appendQueryParam(searchParams, "open", params.open);
  appendQueryParam(searchParams, "hidden", params.hidden);
  appendQueryParam(searchParams, "postedFrom", params.postedFrom);
  appendQueryParam(searchParams, "postedTo", params.postedTo);
  appendQueryParam(searchParams, "sort", params.sort);
  appendQueryParam(searchParams, "dir", params.dir);
  appendQueryParam(searchParams, "page", params.page);
  appendQueryParam(searchParams, "size", params.size);

  const query = searchParams.toString();
  return apiRequest<PagedResponse<FeedJob>>(`/api/v1/feed/jobs${query ? `?${query}` : ""}`);
}

export function feedHealthQueryKey() {
  return ["feed", "health"] as const;
}

export async function getFeedHealth() {
  return apiRequest<FeedHealth>("/api/v1/feed/health");
}

export async function setFeedJobHidden(id: number, hidden: boolean) {
  return apiRequest<FeedJob>(`/api/v1/feed/jobs/${id}/hide`, {
    method: "POST",
    body: JSON.stringify({ hidden })
  });
}

export async function saveFeedJobProspect(id: number) {
  return apiRequest<FeedJob>(`/api/v1/feed/jobs/${id}/save-prospect`, {
    method: "POST"
  });
}

export async function createFeedJobApplication(id: number) {
  return apiRequest<FeedJob>(`/api/v1/feed/jobs/${id}/create-application`, {
    method: "POST"
  });
}
