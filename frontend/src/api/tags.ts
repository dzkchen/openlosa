import type { Tag } from "./applications";
import { apiRequest, appendQueryParam } from "./client";

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

export function tagsQueryKey(params: TagListParams) {
  return ["tags", params] as const;
}

export async function listTags(params: TagListParams = {}) {
  const searchParams = new URLSearchParams();
  appendQueryParam(searchParams, "q", params.q?.trim());
  appendQueryParam(searchParams, "sort", params.sort);
  appendQueryParam(searchParams, "dir", params.dir);

  const query = searchParams.toString();
  return apiRequest<Tag[]>(`/api/v1/tags${query ? `?${query}` : ""}`);
}

export async function createTag(input: TagCreateInput) {
  return apiRequest<Tag>("/api/v1/tags", {
    method: "POST",
    body: JSON.stringify(input)
  });
}
