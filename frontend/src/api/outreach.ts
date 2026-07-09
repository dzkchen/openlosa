import type { Company } from "./applications";
import type { Contact } from "./contacts";

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

type ProblemDetail = {
  title?: string;
  detail?: string;
  status?: number;
};

async function readError(response: Response) {
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    const body = (await response.json()) as ProblemDetail;
    return body.detail || body.title || `Request failed with ${response.status}`;
  }

  const text = await response.text();
  return text || `Request failed with ${response.status}`;
}

async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    }
  });

  if (!response.ok) {
    throw new Error(await readError(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

function appendParam(searchParams: URLSearchParams, key: string, value: string | undefined) {
  if (value === undefined || value === "") {
    return;
  }
  searchParams.set(key, value);
}

export function outreachQueryKey(params: OutreachListParams) {
  return ["outreach", params] as const;
}

export function dueOutreachQueryKey() {
  return ["outreach", "due"] as const;
}

export async function listOutreach(params: OutreachListParams = {}) {
  const searchParams = new URLSearchParams();
  appendParam(searchParams, "q", params.q?.trim());
  appendParam(searchParams, "status", params.status);
  appendParam(searchParams, "type", params.type);
  appendParam(searchParams, "sort", params.sort);
  appendParam(searchParams, "dir", params.dir);

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
    method: "PUT",
    body: JSON.stringify(input)
  });
}

export async function deleteOutreach(id: number) {
  return apiRequest<void>(`/api/v1/outreach/${id}`, {
    method: "DELETE"
  });
}
