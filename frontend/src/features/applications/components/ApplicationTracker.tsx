import { type FormEvent, type KeyboardEvent, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState
} from "@tanstack/react-table";
import {
  applicationSources,
  applicationStatuses,
  applicationsQueryKey,
  changeApplicationStatus,
  createApplication,
  listApplications,
  setApplicationFavorite,
  updateApplication,
  type ApplicationListParams,
  type ApplicationSortField,
  type ApplicationSource,
  type ApplicationStatus,
  type ApplicationUpdateInput,
  type JobApplication
} from "../../../api/applications";
import EmptyState from "../../../components/layout/EmptyState";
import WorkspacePanel from "../../../components/layout/WorkspacePanel";

const statusLabels: Record<ApplicationStatus, string> = {
  SAVED: "Saved",
  APPLIED: "Applied",
  ONLINE_ASSESSMENT: "Online assessment",
  PHONE_SCREEN: "Phone screen",
  INTERVIEW: "Interview",
  OFFER: "Offer",
  REJECTED: "Rejected",
  WITHDRAWN: "Withdrawn",
  GHOSTED: "Ghosted"
};

const sourceLabels: Record<ApplicationSource, string> = {
  MANUAL: "Manual",
  FEED: "Feed",
  PROSPECT: "Prospect"
};

type ApplicationTrackerProps = {
  addOpen?: boolean;
  allowCreate?: boolean;
  emptyDetail: string;
  emptyTitle: string;
  favoriteOnly?: boolean;
  onAddOpenChange?: (open: boolean) => void;
  title: string;
};

type EditableTextCellProps = {
  disabled?: boolean;
  onCommit: (value: string) => void;
  placeholder?: string;
  required?: boolean;
  value: string | null;
};

function EditableTextCell({ disabled, onCommit, placeholder, required, value }: EditableTextCellProps) {
  const [draft, setDraft] = useState(value ?? "");

  useEffect(() => {
    setDraft(value ?? "");
  }, [value]);

  function commit() {
    const next = draft.trim();
    const current = (value ?? "").trim();
    if (required && next.length === 0) {
      setDraft(value ?? "");
      return;
    }
    if (next !== current) {
      onCommit(next);
      setDraft(next);
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Enter") {
      event.currentTarget.blur();
    }
    if (event.key === "Escape") {
      setDraft(value ?? "");
      event.currentTarget.blur();
    }
  }

  return (
    <input
      type="text"
      value={draft}
      placeholder={placeholder}
      disabled={disabled}
      onBlur={commit}
      onChange={(event) => setDraft(event.target.value)}
      onKeyDown={handleKeyDown}
      className="h-9 w-full min-w-32 rounded-md border border-transparent bg-transparent px-2 text-sm text-text outline-none transition placeholder:text-soft hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    />
  );
}

type DateCellProps = {
  disabled?: boolean;
  onCommit: (value: string) => void;
  value: string | null;
};

function DateCell({ disabled, onCommit, value }: DateCellProps) {
  const [draft, setDraft] = useState(value ?? "");

  useEffect(() => {
    setDraft(value ?? "");
  }, [value]);

  function commit() {
    if (!draft) {
      setDraft(value ?? "");
      return;
    }
    if (draft !== value) {
      onCommit(draft);
    }
  }

  return (
    <input
      type="date"
      value={draft}
      disabled={disabled}
      onBlur={commit}
      onChange={(event) => setDraft(event.target.value)}
      className="h-9 w-full min-w-36 rounded-md border border-transparent bg-transparent px-2 text-sm text-text outline-none transition [color-scheme:dark] hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    />
  );
}

type SelectCellProps<T extends string> = {
  disabled?: boolean;
  labels: Record<T, string>;
  onCommit: (value: T) => void;
  options: readonly T[];
  value: T;
};

function SelectCell<T extends string>({ disabled, labels, onCommit, options, value }: SelectCellProps<T>) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(event) => onCommit(event.target.value as T)}
      className="h-9 w-full min-w-36 rounded-md border border-transparent bg-transparent px-2 text-sm text-text outline-none transition hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    >
      {options.map((option) => (
        <option key={option} value={option} className="bg-elevated text-text">
          {labels[option]}
        </option>
      ))}
    </select>
  );
}

type NewApplicationFormProps = {
  favoriteOnly?: boolean;
  isSaving: boolean;
  onCancel: () => void;
  onSubmit: (input: {
    appliedAt?: string;
    companyName: string;
    favorite: boolean;
    location?: string;
    postingUrl?: string;
    roleTitle: string;
    source: ApplicationSource;
    status: ApplicationStatus;
  }) => void;
};

function NewApplicationForm({ favoriteOnly, isSaving, onCancel, onSubmit }: NewApplicationFormProps) {
  const [companyName, setCompanyName] = useState("");
  const [roleTitle, setRoleTitle] = useState("");
  const [status, setStatus] = useState<ApplicationStatus>("SAVED");
  const [source, setSource] = useState<ApplicationSource>("MANUAL");
  const [appliedAt, setAppliedAt] = useState("");
  const [location, setLocation] = useState("");
  const [postingUrl, setPostingUrl] = useState("");
  const [favorite, setFavorite] = useState(Boolean(favoriteOnly));
  const [validation, setValidation] = useState<string | null>(null);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const cleanCompany = companyName.trim();
    const cleanRole = roleTitle.trim();

    if (!cleanCompany || !cleanRole) {
      setValidation("Company and role are required.");
      return;
    }

    setValidation(null);
    onSubmit({
      appliedAt: appliedAt || undefined,
      companyName: cleanCompany,
      favorite,
      location: location.trim() || undefined,
      postingUrl: postingUrl.trim() || undefined,
      roleTitle: cleanRole,
      source,
      status
    });
  }

  return (
    <form onSubmit={handleSubmit} className="border-b border-line/70 bg-canvas/35 px-4 py-4">
      <div className="grid gap-3 lg:grid-cols-[minmax(10rem,1fr)_minmax(12rem,1.2fr)_10rem_10rem_10rem]">
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Company
          <input
            value={companyName}
            disabled={isSaving}
            onChange={(event) => setCompanyName(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
            placeholder="Company name"
          />
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Role
          <input
            value={roleTitle}
            disabled={isSaving}
            onChange={(event) => setRoleTitle(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
            placeholder="Role title"
          />
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Status
          <select
            value={status}
            disabled={isSaving}
            onChange={(event) => setStatus(event.target.value as ApplicationStatus)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
          >
            {applicationStatuses.map((option) => (
              <option key={option} value={option}>
                {statusLabels[option]}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Source
          <select
            value={source}
            disabled={isSaving}
            onChange={(event) => setSource(event.target.value as ApplicationSource)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
          >
            {applicationSources.map((option) => (
              <option key={option} value={option}>
                {sourceLabels[option]}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Applied
          <input
            type="date"
            value={appliedAt}
            disabled={isSaving}
            onChange={(event) => setAppliedAt(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none [color-scheme:dark] focus:border-accent/70 focus:shadow-focus"
          />
        </label>
      </div>
      <div className="mt-3 grid gap-3 lg:grid-cols-[minmax(10rem,1fr)_minmax(14rem,1.3fr)_auto]">
        <input
          value={location}
          disabled={isSaving}
          onChange={(event) => setLocation(event.target.value)}
          className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
          placeholder="Location"
        />
        <input
          value={postingUrl}
          disabled={isSaving}
          onChange={(event) => setPostingUrl(event.target.value)}
          className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
          placeholder="Posting URL"
        />
        <label className="flex h-10 items-center gap-2 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-muted">
          <input
            type="checkbox"
            checked={favorite}
            disabled={isSaving || favoriteOnly}
            onChange={(event) => setFavorite(event.target.checked)}
            className="h-4 w-4 accent-accent"
          />
          Favorite
        </label>
      </div>
      <div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="min-h-5 text-sm text-warn">{validation}</p>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={isSaving}
            className="h-9 rounded-md border border-line/80 px-3 text-sm font-medium text-muted transition hover:bg-elevated/70 hover:text-text disabled:cursor-wait disabled:text-soft"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSaving}
            className="h-9 rounded-md bg-text px-3 text-sm font-medium text-canvas transition hover:bg-text/90 disabled:cursor-wait disabled:bg-soft disabled:text-text/60"
          >
            {isSaving ? "Saving" : "Add application"}
          </button>
        </div>
      </div>
    </form>
  );
}

function formatDate(value: string | null) {
  if (!value) {
    return "Not set";
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(new Date(`${value}T00:00:00`));
}

function buildMutationMessage(error: unknown) {
  return error instanceof Error ? error.message : "Something went wrong.";
}

export default function ApplicationTracker({
  addOpen,
  allowCreate,
  emptyDetail,
  emptyTitle,
  favoriteOnly,
  onAddOpenChange,
  title
}: ApplicationTrackerProps) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<ApplicationStatus | "">("");
  const [sorting, setSorting] = useState<SortingState>([{ id: "updatedAt", desc: true }]);
  const [mutationError, setMutationError] = useState<unknown | null>(null);

  const sort = sorting[0];
  const params: ApplicationListParams = useMemo(
    () => ({
      favorite: favoriteOnly ? true : undefined,
      q: search.trim() || undefined,
      status: statusFilter || undefined,
      sort: (sort?.id as ApplicationSortField | undefined) ?? "updatedAt",
      dir: sort?.desc ? "desc" : "asc"
    }),
    [favoriteOnly, search, sort?.desc, sort?.id, statusFilter]
  );

  const query = useQuery({
    queryKey: applicationsQueryKey(params),
    queryFn: () => listApplications(params)
  });

  function invalidateApplications() {
    void queryClient.invalidateQueries({ queryKey: ["applications"] });
  }

  const createMutation = useMutation({
    mutationFn: createApplication,
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      onAddOpenChange?.(false);
      invalidateApplications();
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: ApplicationUpdateInput }) => updateApplication(id, input),
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      invalidateApplications();
    }
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: ApplicationStatus }) => changeApplicationStatus(id, status),
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      invalidateApplications();
    }
  });

  const favoriteMutation = useMutation({
    mutationFn: ({ id, favorite }: { id: number; favorite: boolean }) => setApplicationFavorite(id, favorite),
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      invalidateApplications();
    }
  });

  const isMutating =
    createMutation.isPending || updateMutation.isPending || statusMutation.isPending || favoriteMutation.isPending;

  function commitUpdate(id: number, input: ApplicationUpdateInput) {
    updateMutation.mutate({ id, input });
  }

  const columns = useMemo<ColumnDef<JobApplication>[]>(
    () => [
      {
        accessorKey: "favorite",
        header: "Fav",
        cell: ({ row }) => (
          <button
            type="button"
            disabled={favoriteMutation.isPending}
            onClick={() => favoriteMutation.mutate({ id: row.original.id, favorite: !row.original.favorite })}
            className={`h-8 min-w-16 rounded-md border px-2 text-xs font-semibold transition ${
              row.original.favorite
                ? "border-accent/60 bg-accent/15 text-text"
                : "border-line/70 text-muted hover:bg-elevated/70 hover:text-text"
            } disabled:cursor-wait disabled:opacity-60`}
          >
            {row.original.favorite ? "On" : "Off"}
          </button>
        )
      },
      {
        id: "company",
        accessorFn: (row) => row.company.name,
        header: "Company",
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.company.name}
            required
            disabled={updateMutation.isPending}
            onCommit={(companyName) => commitUpdate(row.original.id, { companyName })}
          />
        )
      },
      {
        accessorKey: "roleTitle",
        header: "Role",
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.roleTitle}
            required
            disabled={updateMutation.isPending}
            onCommit={(roleTitle) => commitUpdate(row.original.id, { roleTitle })}
          />
        )
      },
      {
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) => (
          <SelectCell
            value={row.original.status}
            labels={statusLabels}
            options={applicationStatuses}
            disabled={statusMutation.isPending}
            onCommit={(status) => {
              if (status !== row.original.status) {
                statusMutation.mutate({ id: row.original.id, status });
              }
            }}
          />
        )
      },
      {
        accessorKey: "appliedAt",
        header: "Applied",
        cell: ({ row }) => (
          <DateCell
            value={row.original.appliedAt}
            disabled={updateMutation.isPending}
            onCommit={(appliedAt) => commitUpdate(row.original.id, { appliedAt })}
          />
        )
      },
      {
        accessorKey: "source",
        header: "Source",
        cell: ({ row }) => (
          <SelectCell
            value={row.original.source}
            labels={sourceLabels}
            options={applicationSources}
            disabled={updateMutation.isPending}
            onCommit={(source) => {
              if (source !== row.original.source) {
                commitUpdate(row.original.id, { source });
              }
            }}
          />
        )
      },
      {
        accessorKey: "location",
        header: "Location",
        enableSorting: false,
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.location}
            placeholder="Add location"
            disabled={updateMutation.isPending}
            onCommit={(location) => commitUpdate(row.original.id, { location })}
          />
        )
      },
      {
        accessorKey: "postingUrl",
        header: "Posting",
        enableSorting: false,
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.postingUrl}
            placeholder="Add URL"
            disabled={updateMutation.isPending}
            onCommit={(postingUrl) => commitUpdate(row.original.id, { postingUrl })}
          />
        )
      },
      {
        accessorKey: "tags",
        header: "Tags",
        enableSorting: false,
        cell: ({ row }) =>
          row.original.tags.length ? (
            <div className="flex min-w-40 flex-wrap gap-1.5">
              {row.original.tags.map((tag) => (
                <span key={tag.id} className="rounded-md border border-line/70 bg-elevated/60 px-2 py-1 text-xs text-muted">
                  {tag.name}
                </span>
              ))}
            </div>
          ) : (
            <span className="px-2 text-sm text-soft">No tags</span>
          )
      },
      {
        accessorKey: "updatedAt",
        header: "Updated",
        cell: ({ row }) => <span className="whitespace-nowrap px-2 text-sm text-muted">{formatDate(row.original.updatedAt.slice(0, 10))}</span>
      }
    ],
    [favoriteMutation, statusMutation, updateMutation]
  );

  const table = useReactTable({
    data: query.data ?? [],
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

  const meta = query.isLoading ? "Loading" : `${query.data?.length ?? 0} rows`;
  const hasActiveFilters = Boolean(search.trim() || statusFilter);

  return (
    <WorkspacePanel title={title} meta={meta}>
      <div className="-m-4 overflow-hidden rounded-lg border border-line/70">
        <div className="flex flex-col gap-3 border-b border-line/70 bg-surface/80 p-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-1 flex-col gap-3 sm:flex-row sm:items-center">
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="h-10 min-w-0 flex-1 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
              placeholder="Filter company, role, URL, location, notes"
            />
            <select
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as ApplicationStatus | "")}
              className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none focus:border-accent/70 focus:shadow-focus"
            >
              <option value="">All statuses</option>
              {applicationStatuses.map((status) => (
                <option key={status} value={status}>
                  {statusLabels[status]}
                </option>
              ))}
            </select>
          </div>
          <button
            type="button"
            onClick={() => {
              setSearch("");
              setStatusFilter("");
              setSorting([{ id: "updatedAt", desc: true }]);
            }}
            className="h-10 rounded-md border border-line/80 px-3 text-sm font-medium text-muted transition hover:bg-elevated/70 hover:text-text"
          >
            Reset
          </button>
        </div>

        {allowCreate && addOpen ? (
          <NewApplicationForm
            favoriteOnly={favoriteOnly}
            isSaving={createMutation.isPending}
            onCancel={() => {
              setMutationError(null);
              onAddOpenChange?.(false);
            }}
            onSubmit={(input) => createMutation.mutate(input)}
          />
        ) : null}

        {mutationError ? (
          <div className="border-b border-warn/30 bg-warn/10 px-4 py-3 text-sm text-warn">
            {buildMutationMessage(mutationError)}
          </div>
        ) : null}

        {query.error ? (
          <div className="p-4">
            <EmptyState title="Could not load applications" detail={buildMutationMessage(query.error)} />
          </div>
        ) : query.isLoading ? (
          <div className="p-4">
            <EmptyState title="Loading applications" detail="Fetching the latest tracker rows from the API." />
          </div>
        ) : table.getRowModel().rows.length === 0 ? (
          <div className="p-4">
            {hasActiveFilters ? (
              <EmptyState title="No matching applications" detail="Adjust or reset the filters to show more tracker rows." />
            ) : (
              <EmptyState title={emptyTitle} detail={emptyDetail} />
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-[76rem] w-full border-collapse">
              <thead className="bg-canvas/50">
                {table.getHeaderGroups().map((headerGroup) => (
                  <tr key={headerGroup.id}>
                    {headerGroup.headers.map((header) => (
                      <th key={header.id} className="border-b border-line/70 px-2 py-2 text-left text-xs font-semibold uppercase tracking-[0.12em] text-muted">
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
                {table.getRowModel().rows.map((row) => (
                  <tr key={row.id} className="border-b border-line/50 transition hover:bg-elevated/25">
                    {row.getVisibleCells().map((cell) => (
                      <td key={cell.id} className="h-12 max-w-72 px-1 py-1 align-middle">
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            {isMutating ? <div className="border-t border-line/70 px-4 py-2 text-xs text-muted">Saving latest change...</div> : null}
          </div>
        )}
      </div>
    </WorkspacePanel>
  );
}
