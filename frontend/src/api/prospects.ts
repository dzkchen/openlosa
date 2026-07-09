import type { Tag } from "./applications";
import { apiRequest, apiRequestAllPages, appendQueryParam } from "./client";

export const prospectPriorities = ["LOW", "MEDIUM", "HIGH"] as const;
export const prospectStatuses = ["NEW", "RESEARCHING", "PROMOTED", "DROPPED"] as const;

export type ProspectPriority = (typeof prospectPriorities)[number];
export type ProspectStatus = (typeof prospectStatuses)[number];

export type ProspectApplication = {
  id: number;
  roleTitle: string;
  companyName: string;
};

export type Prospect = {
  id: number;
  name: string;
  url: string | null;
  note: string | null;
  priority: ProspectPriority;
  status: ProspectStatus;
  promotedApplication: ProspectApplication | null;
  tags: Tag[];
  createdAt: string;
  updatedAt: string;
};

export type ProspectSortField = "name" | "priority" | "status" | "createdAt" | "updatedAt";

export type ProspectListParams = {
  q?: string;
  priority?: ProspectPriority;
  status?: ProspectStatus;
  tagId?: number;
  sort?: ProspectSortField;
  dir?: "asc" | "desc";
  page?: number;
  size?: number;
};

export type ProspectCreateInput = {
  name: string;
  url?: string | null;
  note?: string | null;
  priority?: ProspectPriority;
  status?: ProspectStatus;
  tagIds?: number[];
};

export type ProspectUpdateInput = Partial<ProspectCreateInput> & {
  clearUrl?: boolean;
  clearNote?: boolean;
};

export type ProspectPromoteInput = {
  companyName?: string | null;
  roleTitle?: string | null;
};

export function prospectsQueryKey(params: ProspectListParams) {
  return ["prospects", params] as const;
}

export async function listProspects(params: ProspectListParams = {}) {
  const searchParams = new URLSearchParams();
  appendQueryParam(searchParams, "q", params.q?.trim());
  appendQueryParam(searchParams, "priority", params.priority);
  appendQueryParam(searchParams, "status", params.status);
  appendQueryParam(searchParams, "tagId", params.tagId);
  appendQueryParam(searchParams, "sort", params.sort);
  appendQueryParam(searchParams, "dir", params.dir);
  appendQueryParam(searchParams, "page", params.page);
  appendQueryParam(searchParams, "size", params.size);

  if (params.page === undefined && params.size === undefined) {
    return apiRequestAllPages<Prospect>("/api/v1/prospects", searchParams);
  }

  const query = searchParams.toString();
  return apiRequest<Prospect[]>(`/api/v1/prospects${query ? `?${query}` : ""}`);
}

export async function createProspect(input: ProspectCreateInput) {
  return apiRequest<Prospect>("/api/v1/prospects", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function updateProspect(id: number, input: ProspectUpdateInput) {
  return apiRequest<Prospect>(`/api/v1/prospects/${id}`, {
    method: "PATCH",
    body: JSON.stringify(input)
  });
}

export async function promoteProspect(id: number, input: ProspectPromoteInput = {}) {
  return apiRequest<Prospect>(`/api/v1/prospects/${id}/promote`, {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function deleteProspect(id: number) {
  return apiRequest<void>(`/api/v1/prospects/${id}`, {
    method: "DELETE"
  });
}
