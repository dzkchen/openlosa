import type { Company } from "./applications";

export const contactRelationships = ["RECRUITER", "ALUM", "REFERRAL", "OTHER"] as const;

export type ContactRelationship = (typeof contactRelationships)[number];

export type Contact = {
  id: number;
  company: Company | null;
  name: string;
  title: string | null;
  email: string | null;
  linkedinUrl: string | null;
  relationship: ContactRelationship;
  notes: string | null;
  lastContactedAt: string | null;
  createdAt: string;
};

export type ContactSortField = "name" | "company" | "title" | "email" | "relationship" | "lastContactedAt" | "createdAt";

export type ContactListParams = {
  q?: string;
  relationship?: ContactRelationship;
  sort?: ContactSortField;
  dir?: "asc" | "desc";
};

export type ContactCreateInput = {
  companyName?: string | null;
  companyWebsite?: string | null;
  companyNotes?: string | null;
  name: string;
  title?: string | null;
  email?: string | null;
  linkedinUrl?: string | null;
  relationship?: ContactRelationship;
  notes?: string | null;
  lastContactedAt?: string | null;
};

export type ContactUpdateInput = Partial<ContactCreateInput> & {
  clearCompany?: boolean;
  clearLastContactedAt?: boolean;
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

export function contactsQueryKey(params: ContactListParams) {
  return ["contacts", params] as const;
}

export async function listContacts(params: ContactListParams = {}) {
  const searchParams = new URLSearchParams();
  appendParam(searchParams, "q", params.q?.trim());
  appendParam(searchParams, "relationship", params.relationship);
  appendParam(searchParams, "sort", params.sort);
  appendParam(searchParams, "dir", params.dir);

  const query = searchParams.toString();
  return apiRequest<Contact[]>(`/api/v1/contacts${query ? `?${query}` : ""}`);
}

export async function createContact(input: ContactCreateInput) {
  return apiRequest<Contact>("/api/v1/contacts", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function updateContact(id: number, input: ContactUpdateInput) {
  return apiRequest<Contact>(`/api/v1/contacts/${id}`, {
    method: "PUT",
    body: JSON.stringify(input)
  });
}

export async function deleteContact(id: number) {
  return apiRequest<void>(`/api/v1/contacts/${id}`, {
    method: "DELETE"
  });
}
