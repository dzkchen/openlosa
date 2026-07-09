import { apiRequest } from "./client";

export const emailLookupStatuses = ["VERIFIED", "CATCH_ALL", "UNKNOWN", "DOES_NOT_EXIST"] as const;

export type EmailLookupStatus = (typeof emailLookupStatuses)[number];

export type EmailCandidate = {
  email: string;
  founder: string | null;
  status: EmailLookupStatus;
  confidence: number | null;
  rank: number | null;
  permutationRank: number | null;
  mxHost: string | null;
  smtpCode: number | null;
  latencyMs: number | null;
  catchAllDomain: boolean | null;
};

export type EmailLookup = {
  id: number;
  contactId: number | null;
  personName: string;
  companyUrl: string;
  domain: string | null;
  company: string | null;
  requestedCount: number | null;
  permutationsProbed: number | null;
  topStatus: EmailLookupStatus | null;
  topConfidence: number | null;
  chosenEmail: string | null;
  candidates: EmailCandidate[];
  createdAt: string;
};

export type EmailLookupInput = {
  contactId: number;
  personName: string;
  companyUrl: string;
  count?: number;
  includeCatchAll?: boolean;
  includeUnknown?: boolean;
  noSmtp?: boolean;
  delaySeconds?: number;
};

export type EmailChooseInput = {
  lookupId: number;
  contactId?: number;
  email: string;
  createOutreach?: boolean;
};

export type EmailChooseResult = {
  lookupId: number;
  chosenEmail: string;
  contactId: number;
  outreachId: number | null;
};

export async function lookupEmail(input: EmailLookupInput) {
  return apiRequest<EmailLookup>("/api/v1/email-finder/lookup", {
    method: "POST",
    body: JSON.stringify(input)
  });
}

export async function getEmailLookup(lookupId: number) {
  return apiRequest<EmailLookup>(`/api/v1/email-finder/${lookupId}`);
}

export async function listEmailLookups(contactId: number) {
  return apiRequest<EmailLookup[]>(`/api/v1/email-finder/lookups?contactId=${contactId}`);
}

export async function chooseEmail(input: EmailChooseInput) {
  return apiRequest<EmailChooseResult>(`/api/v1/email-finder/${input.lookupId}/choose`, {
    method: "POST",
    body: JSON.stringify({
      contactId: input.contactId,
      email: input.email,
      createOutreach: input.createOutreach
    })
  });
}
