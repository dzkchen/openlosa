import { apiRequest, appendQueryParam } from "./client";

export const applicationStatuses = [
  "SAVED",
  "APPLIED",
  "ONLINE_ASSESSMENT",
  "PHONE_SCREEN",
  "INTERVIEW",
  "OFFER",
  "REJECTED",
  "WITHDRAWN",
  "GHOSTED"
] as const;

export const applicationSources = ["MANUAL", "FEED", "PROSPECT"] as const;

export type ApplicationStatus = (typeof applicationStatuses)[number];
export type ApplicationSource = (typeof applicationSources)[number];

export type Company = {
  id: number;
  name: string;
  website: string | null;
  notes: string | null;
  createdAt: string;
};

export type Tag = {
  id: number;
  name: string;
  color: string | null;
};

export type JobApplication = {
  id: number;
  company: Company;
  roleTitle: string;
  postingUrl: string | null;
  location: string | null;
  status: ApplicationStatus;
  appliedAt: string | null;
  source: ApplicationSource;
  salaryText: string | null;
  notes: string | null;
  favorite: boolean;
  tags: Tag[];
  createdAt: string;
  updatedAt: string;
};

export type ApplicationSortField =
  | "company"
  | "roleTitle"
  | "status"
  | "appliedAt"
  | "source"
  | "favorite"
  | "createdAt"
  | "updatedAt";

export type ApplicationListParams = {
  q?: string;
  status?: ApplicationStatus;
  favorite?: boolean;
  sort?: ApplicationSortField;
  dir?: "asc" | "desc";
};

export type ApplicationCreateInput = {
  companyName: string;
  roleTitle: string;
  postingUrl?: string | null;
  location?: string | null;
  status?: ApplicationStatus;
  appliedAt?: string | null;
  source?: ApplicationSource;
  salaryText?: string | null;
  notes?: string | null;
  favorite?: boolean;
};

export type ApplicationUpdateInput = Partial<ApplicationCreateInput> & {
  clearAppliedAt?: boolean;
  companyName?: string;
};

export type ApplicationImportResult = {
  importedCount: number;
  applications: JobApplication[];
};

export function applicationsQueryKey(params: ApplicationListParams) {
  return ["applications", params] as const;
}

export async function listApplications(params: ApplicationListParams = {}) {
  const searchParams = new URLSearchParams();
  appendQueryParam(searchParams, "q", params.q?.trim());
  appendQueryParam(searchParams, "status", params.status);
  appendQueryParam(searchParams, "favorite", params.favorite);
  appendQueryParam(searchParams, "sort", params.sort);
  appendQueryParam(searchParams, "dir", params.dir);

  const query = searchParams.toString();
  return apiRequest<JobApplication[]>(`/api/v1/applications${query ? `?${query}` : ""}`);
}

export async function createApplication(input: ApplicationCreateInput) {
  return apiRequest<JobApplication>("/api/v1/applications", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function importApplicationsCsv(file: File) {
  const formData = new FormData();
  formData.set("file", file);

  return apiRequest<ApplicationImportResult>("/api/v1/applications/import", {
    method: "POST",
    body: formData
  });
}

export async function updateApplication(id: number, input: ApplicationUpdateInput) {
  return apiRequest<JobApplication>(`/api/v1/applications/${id}`, {
    method: "PUT",
    body: JSON.stringify(input)
  });
}

export async function changeApplicationStatus(id: number, toStatus: ApplicationStatus) {
  return apiRequest<JobApplication>(`/api/v1/applications/${id}/status`, {
    method: "POST",
    body: JSON.stringify({ toStatus })
  });
}

export async function undoApplicationStatus(id: number) {
  return apiRequest<JobApplication>(`/api/v1/applications/${id}/status/undo`, {
    method: "POST"
  });
}

export async function setApplicationFavorite(id: number, favorite: boolean) {
  return apiRequest<JobApplication>(`/api/v1/applications/${id}/favorite`, {
    method: "PUT",
    body: JSON.stringify({ favorite })
  });
}
