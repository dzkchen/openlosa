type QueryParamValue = string | number | boolean | null | undefined;

type ProblemDetail = {
  title?: string;
  detail?: string;
};

async function readError(response: Response) {
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("json")) {
    const body = (await response.json()) as ProblemDetail;
    return body.detail || body.title || `Request failed with ${response.status}`;
  }

  const text = await response.text();
  return text || `Request failed with ${response.status}`;
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  if (!(init?.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    throw new Error(await readError(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export async function apiRequestAllPages<T>(
  path: string,
  searchParams: URLSearchParams,
  pageSize = 100
): Promise<T[]> {
  const items: T[] = [];
  let page = 0;

  while (true) {
    const pageParams = new URLSearchParams(searchParams);
    pageParams.set("page", String(page));
    pageParams.set("size", String(pageSize));

    const pageItems = await apiRequest<T[]>(`${path}?${pageParams.toString()}`);
    items.push(...pageItems);
    if (pageItems.length < pageSize) {
      return items;
    }
    page += 1;
  }
}

export function appendQueryParam(searchParams: URLSearchParams, key: string, value: QueryParamValue) {
  if (value === undefined || value === null || value === "") {
    return;
  }
  searchParams.set(key, String(value));
}

export function errorMessage(error: unknown, fallback = "Something went wrong.") {
  return error instanceof Error ? error.message : fallback;
}
