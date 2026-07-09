import type { Company } from "./applications";
import type { Contact } from "./contacts";
import { apiRequest, apiRequestAllPages, appendQueryParam } from "./client";

export const outreachTypes = ["COLD_EMAIL", "LINKEDIN_DM", "REFERRAL_ASK", "OTHER"] as const;
export const outreachStatuses = ["TO_SEND", "SENT", "REPLIED", "GHOSTED"] as const;

export type OutreachType = (typeof outreachTypes)[number];
export type OutreachStatus = (typeof outreachStatuses)[number];

export type OutreachApplication = {
  id: number;
  roleTitle: string;
  companyName: string;
};

export type Outreach = {
  id: number;
  contact: Contact | null;
  company: Company | null;
  application: OutreachApplication | null;
  type: OutreachType;
  status: OutreachStatus;
  sentAt: string | null;
  followUpBy: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
};

export type OutreachSortField =
  | "contact"
  | "company"
  | "application"
  | "type"
  | "status"
  | "sentAt"
  | "followUpBy"
  | "createdAt"
  | "updatedAt";

export type OutreachListParams = {
  q?: string;
  status?: OutreachStatus;
  type?: OutreachType;
  sort?: OutreachSortField;
  dir?: "asc" | "desc";
  page?: number;
  size?: number;
};

export type OutreachCreateInput = {
  contactId?: number | null;
  companyName?: string | null;
  companyWebsite?: string | null;
  companyNotes?: string | null;
  applicationId?: number | null;
  type?: OutreachType;
  status?: OutreachStatus;
  sentAt?: string | null;
  followUpBy?: string | null;
  notes?: string | null;
};

export type OutreachUpdateInput = Partial<OutreachCreateInput> & {
  clearContact?: boolean;
  clearCompany?: boolean;
  clearApplication?: boolean;
  clearSentAt?: boolean;
  clearFollowUpBy?: boolean;
};

export function outreachQueryKey(params: OutreachListParams) {
  return ["outreach", params] as const;
}

export function dueOutreachQueryKey() {
  return ["outreach", "due"] as const;
}

export async function listOutreach(params: OutreachListParams = {}) {
  const searchParams = new URLSearchParams();
  appendQueryParam(searchParams, "q", params.q?.trim());
  appendQueryParam(searchParams, "status", params.status);
  appendQueryParam(searchParams, "type", params.type);
  appendQueryParam(searchParams, "sort", params.sort);
  appendQueryParam(searchParams, "dir", params.dir);
  appendQueryParam(searchParams, "page", params.page);
  appendQueryParam(searchParams, "size", params.size);

  if (params.page === undefined && params.size === undefined) {
    return apiRequestAllPages<Outreach>("/api/v1/outreach", searchParams);
  }

  const query = searchParams.toString();
  return apiRequest<Outreach[]>(`/api/v1/outreach${query ? `?${query}` : ""}`);
}

export async function listDueOutreach() {
  return apiRequest<Outreach[]>("/api/v1/outreach/due");
}

export async function createOutreach(input: OutreachCreateInput) {
  return apiRequest<Outreach>("/api/v1/outreach", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function updateOutreach(id: number, input: OutreachUpdateInput) {
  return apiRequest<Outreach>(`/api/v1/outreach/${id}`, {
    method: "PATCH",
    body: JSON.stringify(input)
  });
}

export async function deleteOutreach(id: number) {
  return apiRequest<void>(`/api/v1/outreach/${id}`, {
    method: "DELETE"
  });
}
