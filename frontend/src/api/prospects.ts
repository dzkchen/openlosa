import type { Tag } from "./applications";

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

function appendParam(searchParams: URLSearchParams, key: string, value: string | number | undefined) {
  if (value === undefined || value === "") {
    return;
  }
  searchParams.set(key, String(value));
}

export function prospectsQueryKey(params: ProspectListParams) {
  return ["prospects", params] as const;
}

export async function listProspects(params: ProspectListParams = {}) {
  const searchParams = new URLSearchParams();
  appendParam(searchParams, "q", params.q?.trim());
  appendParam(searchParams, "priority", params.priority);
  appendParam(searchParams, "status", params.status);
  appendParam(searchParams, "tagId", params.tagId);
  appendParam(searchParams, "sort", params.sort);
  appendParam(searchParams, "dir", params.dir);

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
    method: "PUT",
    body: JSON.stringify(input)
  });
}

export async function deleteProspect(id: number) {
  return apiRequest<void>(`/api/v1/prospects/${id}`, {
    method: "DELETE"
  });
}
