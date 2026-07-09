import type { Tag } from "./applications";

export type TagSortField = "name" | "color";

export type TagListParams = {
  q?: string;
  sort?: TagSortField;
  dir?: "asc" | "desc";
};

export type TagCreateInput = {
  name: string;
  color?: string | null;
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

export function tagsQueryKey(params: TagListParams) {
  return ["tags", params] as const;
}

export async function listTags(params: TagListParams = {}) {
  const searchParams = new URLSearchParams();
  appendParam(searchParams, "q", params.q?.trim());
  appendParam(searchParams, "sort", params.sort);
  appendParam(searchParams, "dir", params.dir);

  const query = searchParams.toString();
  return apiRequest<Tag[]>(`/api/v1/tags${query ? `?${query}` : ""}`);
}

export async function createTag(input: TagCreateInput) {
  return apiRequest<Tag>("/api/v1/tags", {
    method: "POST",
    body: JSON.stringify(input)
  });
}
