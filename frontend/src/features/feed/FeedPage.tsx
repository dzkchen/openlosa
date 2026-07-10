import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState
} from "@tanstack/react-table";
import {
  createFeedJobApplication,
  feedHealthQueryKey,
  feedJobsQueryKey,
  feedQueryKey,
  getFeedHealth,
  listFeedJobs,
  saveFeedJobProspect,
  setFeedJobHidden,
  type FeedHealth,
  type FeedJob,
  type FeedListParams,
  type FeedSortField
} from "../../api/feed";
import { errorMessage } from "../../api/client";
import { formatDate } from "../../utils/format";
import EmptyState from "../../components/layout/EmptyState";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";

const PAGE_SIZE = 25;

type OpenFilter = "" | "open" | "closed";

const RELATIVE_TIME = new Intl.RelativeTimeFormat(undefined, { numeric: "auto" });

function parseUtc(value: string) {
  return new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}Z`);
}

function formatRelative(value: string) {
  const then = parseUtc(value).getTime();
  const diffSeconds = Math.round((then - Date.now()) / 1000);
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ["day", 86400],
    ["hour", 3600],
    ["minute", 60]
  ];
  for (const [unit, seconds] of units) {
    if (Math.abs(diffSeconds) >= seconds) {
      return RELATIVE_TIME.format(Math.round(diffSeconds / seconds), unit);
    }
  }
  return RELATIVE_TIME.format(diffSeconds, "second");
}

function FeedStalenessBanner({ health }: { health: FeedHealth }) {
  const { lastRun, lastSuccessAt, stale, staleAfterHours } = health;
  const lastFailed = lastRun?.status === "FAILED";

  let heading: string;
  let detail: string;
  if (lastRun === null) {
    heading = "Feed has not ingested yet";
    detail =
      "No ingest run has completed. Postings appear once the feed engine writes jobs.json and the hourly ingest picks it up.";
  } else if (stale) {
    heading = "Feed data is stale";
    detail = lastSuccessAt
      ? `The last successful ingest was ${formatRelative(lastSuccessAt)}, past the ${staleAfterHours}-hour freshness window. The engine may have stopped — existing postings below are still usable.`
      : `No ingest has applied engine data yet, past the ${staleAfterHours}-hour freshness window. The engine may not be producing jobs.json — existing postings below are still usable.`;
  } else if (lastFailed) {
    heading = "Last feed ingest failed";
    detail = `The most recent ingest run failed but the feed is still within its ${staleAfterHours}-hour freshness window. Existing postings remain usable.`;
  } else {
    return null;
  }

  return (
    <div className="rounded-lg border border-warn/30 bg-warn/10 px-4 py-3 text-sm text-warn">
      <p className="font-semibold">{heading}</p>
      <p className="mt-1 text-warn/90">{detail}</p>
      {lastFailed && lastRun?.message ? (
        <p className="mt-2 break-words font-mono text-xs text-warn/80">{lastRun.message}</p>
      ) : null}
    </div>
  );
}

function FeedRowActions({
  job,
  onActionStart,
  onActionError
}: {
  job: FeedJob;
  onActionStart: () => void;
  onActionError: (error: unknown) => void;
}) {
  const queryClient = useQueryClient();

  const invalidate = (keys: readonly (readonly string[])[]) => {
    for (const queryKey of keys) {
      void queryClient.invalidateQueries({ queryKey });
    }
  };

  const saveProspectMutation = useMutation({
    mutationFn: () => saveFeedJobProspect(job.id),
    onMutate: onActionStart,
    onError: onActionError,
    onSuccess: () => invalidate([feedJobsQueryKey(), ["prospects"]])
  });

  const createApplicationMutation = useMutation({
    mutationFn: () => createFeedJobApplication(job.id),
    onMutate: onActionStart,
    onError: onActionError,
    onSuccess: () => invalidate([feedJobsQueryKey(), ["applications"], ["dashboard"]])
  });

  const hideMutation = useMutation({
    mutationFn: () => setFeedJobHidden(job.id, !job.hidden),
    onMutate: onActionStart,
    onError: onActionError,
    onSuccess: () => invalidate([feedJobsQueryKey()])
  });

  const savedProspect = job.savedProspectId != null;
  const createdApplication = job.createdApplicationId != null;
  const linkClass =
    "inline-flex h-8 items-center whitespace-nowrap rounded-md border border-accent/50 bg-accent/10 px-2 text-xs font-semibold text-text";
  const actionClass =
    "h-8 rounded-md border border-line/70 px-2 text-xs font-semibold text-muted transition hover:border-accent/60 hover:bg-accent/10 hover:text-text disabled:cursor-wait disabled:opacity-60";

  return (
    <div className="flex items-center gap-1 px-2">
      {savedProspect ? (
        <span className={linkClass}>Saved ✓</span>
      ) : (
        <button
          type="button"
          disabled={saveProspectMutation.isPending}
          onClick={() => saveProspectMutation.mutate()}
          className={actionClass}
        >
          {saveProspectMutation.isPending ? "Saving…" : "Save prospect"}
        </button>
      )}
      {createdApplication ? (
        <span className={linkClass}>Application ✓</span>
      ) : (
        <button
          type="button"
          disabled={createApplicationMutation.isPending}
          onClick={() => createApplicationMutation.mutate()}
          className={actionClass}
        >
          {createApplicationMutation.isPending ? "Creating…" : "Create application"}
        </button>
      )}
      <button
        type="button"
        disabled={hideMutation.isPending}
        onClick={() => hideMutation.mutate()}
        className={actionClass}
      >
        {job.hidden ? "Unhide" : "Hide"}
      </button>
    </div>
  );
}

export default function FeedPage() {
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [sponsorship, setSponsorship] = useState("");
  const [openFilter, setOpenFilter] = useState<OpenFilter>("");
  const [showHidden, setShowHidden] = useState(false);
  const [sorting, setSorting] = useState<SortingState>([{ id: "postedAt", desc: true }]);
  const [page, setPage] = useState(0);
  const [mutationError, setMutationError] = useState<unknown | null>(null);

  useEffect(() => {
    const handle = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(handle);
  }, [search]);

  const sort = sorting[0];
  const sortField = (sort?.id as FeedSortField | undefined) ?? "postedAt";
  const sortDir = sort?.desc === false ? "asc" : "desc";

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, sponsorship, openFilter, showHidden, sortField, sortDir]);

  const params: FeedListParams = useMemo(
    () => ({
      q: debouncedSearch || undefined,
      sponsorship: sponsorship.trim() || undefined,
      open: openFilter === "open" ? true : openFilter === "closed" ? false : undefined,
      hidden: showHidden ? true : undefined,
      sort: sortField,
      dir: sortDir,
      page,
      size: PAGE_SIZE
    }),
    [debouncedSearch, sponsorship, openFilter, showHidden, sortField, sortDir, page]
  );

  const query = useQuery({
    queryKey: feedQueryKey(params),
    queryFn: () => listFeedJobs(params)
  });

  const healthQuery = useQuery({
    queryKey: feedHealthQueryKey(),
    queryFn: getFeedHealth,
    staleTime: 60_000,
    refetchInterval: 90_000
  });
  const health = healthQuery.data;

  const columns = useMemo<ColumnDef<FeedJob>[]>(
    () => [
      {
        id: "company",
        accessorFn: (row) => row.companyName,
        header: "Company",
        cell: ({ row }) => <span className="whitespace-nowrap px-2 text-sm font-medium text-text">{row.original.companyName}</span>
      },
      {
        id: "title",
        accessorFn: (row) => row.title,
        header: "Title",
        cell: ({ row }) => (
          <a
            href={row.original.url}
            target="_blank"
            rel="noreferrer"
            className="px-2 text-sm text-accent2 underline-offset-2 hover:underline"
          >
            {row.original.title}
          </a>
        )
      },
      {
        accessorKey: "location",
        header: "Location",
        enableSorting: false,
        cell: ({ row }) => <span className="px-2 text-sm text-muted">{row.original.location ?? "—"}</span>
      },
      {
        id: "sponsorship",
        accessorFn: (row) => row.sponsorship,
        header: "Sponsorship",
        cell: ({ row }) => <span className="whitespace-nowrap px-2 text-sm text-muted">{row.original.sponsorship ?? "—"}</span>
      },
      {
        id: "source",
        accessorFn: (row) => row.sourceAts,
        header: "Source",
        enableSorting: false,
        cell: ({ row }) => <span className="whitespace-nowrap px-2 text-sm text-muted">{row.original.sourceAts}</span>
      },
      {
        id: "open",
        accessorFn: (row) => row.open,
        header: "Status",
        enableSorting: false,
        cell: ({ row }) => (
          <span
            className={`inline-flex whitespace-nowrap rounded-md border px-2 py-1 text-xs font-semibold ${
              row.original.open
                ? "border-accent/50 bg-accent/10 text-text"
                : "border-line/70 text-muted"
            }`}
          >
            {row.original.open ? "Open" : "Closed"}
          </span>
        )
      },
      {
        id: "postedAt",
        accessorFn: (row) => row.postedAt,
        header: "Posted",
        cell: ({ row }) => <span className="whitespace-nowrap px-2 text-sm text-muted">{formatDate(row.original.postedAt, "—")}</span>
      },
      {
        id: "actions",
        header: "Actions",
        enableSorting: false,
        cell: ({ row }) => (
          <FeedRowActions
            job={row.original}
            onActionStart={() => setMutationError(null)}
            onActionError={setMutationError}
          />
        )
      }
    ],
    []
  );

  const table = useReactTable({
    data: query.data?.content ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualSorting: true,
    onSortingChange: (updater) =>
      setSorting((previous) => {
        const next = typeof updater === "function" ? updater(previous) : updater;
        return next.slice(0, 1);
      }),
    state: { sorting }
  });

  const totalElements = query.data?.totalElements ?? 0;
  const totalPages = query.data?.totalPages ?? 0;
  const currentPage = query.data?.page ?? page;
  const jobCountMeta = `${totalElements} ${totalElements === 1 ? "job" : "jobs"}`;
  const healthyFreshness =
    health && !health.stale && health.lastRun?.status !== "FAILED" && health.lastSuccessAt
      ? `last change ${formatRelative(health.lastSuccessAt)}`
      : null;
  const meta = query.isLoading
    ? "Loading"
    : healthyFreshness
      ? `${jobCountMeta} · ${healthyFreshness}`
      : jobCountMeta;
  const hasActiveFilters = Boolean(debouncedSearch || sponsorship.trim() || openFilter || showHidden);
  const rows = table.getRowModel().rows;

  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Feed"
        title="Internship feed"
        description="Tracked postings from the feed engine, ready to save as prospects or applications."
      />
      {health ? <FeedStalenessBanner health={health} /> : null}
      <WorkspacePanel title="Feed jobs" meta={meta}>
        <div className="-m-4 overflow-hidden rounded-lg border border-line/70">
          <div className="flex flex-col gap-3 border-b border-line/70 bg-surface/80 p-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-1 flex-col gap-3 sm:flex-row sm:items-center">
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="h-10 min-w-0 flex-1 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
                placeholder="Filter company or title"
              />
              <input
                value={sponsorship}
                onChange={(event) => setSponsorship(event.target.value)}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
                placeholder="Sponsorship (exact)"
              />
              <select
                value={openFilter}
                onChange={(event) => setOpenFilter(event.target.value as OpenFilter)}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none focus:border-accent/70 focus:shadow-focus"
              >
                <option value="">All statuses</option>
                <option value="open">Open</option>
                <option value="closed">Closed</option>
              </select>
              <label className="flex h-10 items-center gap-2 whitespace-nowrap rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-muted">
                <input
                  type="checkbox"
                  checked={showHidden}
                  onChange={(event) => setShowHidden(event.target.checked)}
                  className="h-4 w-4 accent-accent"
                />
                Show hidden
              </label>
            </div>
            <button
              type="button"
              onClick={() => {
                setSearch("");
                setSponsorship("");
                setOpenFilter("");
                setShowHidden(false);
                setSorting([{ id: "postedAt", desc: true }]);
              }}
              className="h-10 rounded-md border border-line/80 px-3 text-sm font-medium text-muted transition hover:bg-elevated/70 hover:text-text"
            >
              Reset
            </button>
          </div>

          {mutationError ? (
            <div className="border-b border-warn/30 bg-warn/10 px-4 py-3 text-sm text-warn">{errorMessage(mutationError)}</div>
          ) : null}

          {query.error ? (
            <div className="p-4">
              <EmptyState title="Could not load feed jobs" detail={errorMessage(query.error)} />
            </div>
          ) : query.isLoading ? (
            <div className="p-4">
              <EmptyState title="Loading feed jobs" detail="Fetching the latest postings from the API." />
            </div>
          ) : rows.length === 0 ? (
            <div className="p-4">
              {hasActiveFilters ? (
                <EmptyState title="No matching feed jobs" detail="Adjust or reset the filters to show more postings." />
              ) : (
                <EmptyState title="No feed jobs yet" detail="Postings appear here after the engine ingest runs." />
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-[64rem] w-full border-collapse">
                <thead className="bg-canvas/50">
                  {table.getHeaderGroups().map((headerGroup) => (
                    <tr key={headerGroup.id}>
                      {headerGroup.headers.map((header) => (
                        <th
                          key={header.id}
                          className="border-b border-line/70 px-2 py-2 text-left text-xs font-semibold uppercase tracking-[0.12em] text-muted"
                        >
                          {header.isPlaceholder ? null : header.column.getCanSort() ? (
                            <button
                              type="button"
                              onClick={header.column.getToggleSortingHandler()}
                              className="flex items-center gap-1 text-left transition hover:text-text"
                            >
                              {flexRender(header.column.columnDef.header, header.getContext())}
                              <span className="inline-block w-4 text-soft">
                                {header.column.getIsSorted() === "asc" ? "Asc" : header.column.getIsSorted() === "desc" ? "Desc" : ""}
                              </span>
                            </button>
                          ) : (
                            flexRender(header.column.columnDef.header, header.getContext())
                          )}
                        </th>
                      ))}
                    </tr>
                  ))}
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr
                      key={row.id}
                      className={`border-b border-line/50 transition hover:bg-elevated/25 ${row.original.hidden ? "opacity-60" : ""}`}
                    >
                      {row.getVisibleCells().map((cell) => (
                        <td key={cell.id} className="h-12 max-w-72 px-1 py-1 align-middle">
                          {flexRender(cell.column.columnDef.cell, cell.getContext())}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {!query.isLoading && !query.error && totalElements > 0 ? (
            <div className="flex flex-col gap-3 border-t border-line/70 bg-surface/80 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
              <span className="text-xs text-muted">
                Page {currentPage + 1} of {Math.max(totalPages, 1)} · {totalElements} total
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={currentPage <= 0 || query.isFetching}
                  onClick={() => setPage((previous) => Math.max(previous - 1, 0))}
                  className="h-9 rounded-md border border-line/80 px-3 text-sm font-medium text-muted transition hover:bg-elevated/70 hover:text-text disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Previous
                </button>
                <button
                  type="button"
                  disabled={currentPage + 1 >= totalPages || query.isFetching}
                  onClick={() => setPage((previous) => previous + 1)}
                  className="h-9 rounded-md border border-line/80 px-3 text-sm font-medium text-muted transition hover:bg-elevated/70 hover:text-text disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Next
                </button>
              </div>
            </div>
          ) : null}
        </div>
      </WorkspacePanel>
    </div>
  );
}
