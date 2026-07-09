import type { Company } from "./applications";
import { apiRequest, apiRequestAllPages, appendQueryParam } from "./client";

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
  page?: number;
  size?: number;
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
  clearEmail?: boolean;
  clearLastContactedAt?: boolean;
};

export function contactsQueryKey(params: ContactListParams) {
  return ["contacts", params] as const;
}

export async function listContacts(params: ContactListParams = {}) {
  const searchParams = new URLSearchParams();
  appendQueryParam(searchParams, "q", params.q?.trim());
  appendQueryParam(searchParams, "relationship", params.relationship);
  appendQueryParam(searchParams, "sort", params.sort);
  appendQueryParam(searchParams, "dir", params.dir);
  appendQueryParam(searchParams, "page", params.page);
  appendQueryParam(searchParams, "size", params.size);

  if (params.page === undefined && params.size === undefined) {
    return apiRequestAllPages<Contact>("/api/v1/contacts", searchParams);
  }

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
    method: "PATCH",
    body: JSON.stringify(input)
  });
}

export async function deleteContact(id: number) {
  return apiRequest<void>(`/api/v1/contacts/${id}`, {
    method: "DELETE"
  });
}
