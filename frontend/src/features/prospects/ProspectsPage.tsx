import { type FormEvent, type KeyboardEvent, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState
} from "@tanstack/react-table";
import type { Tag } from "../../api/applications";
import {
  createProspect,
  deleteProspect,
  listProspects,
  prospectPriorities,
  prospectsQueryKey,
  prospectStatuses,
  updateProspect,
  type Prospect,
  type ProspectCreateInput,
  type ProspectPriority,
  type ProspectSortField,
  type ProspectStatus,
  type ProspectUpdateInput
} from "../../api/prospects";
import { listTags, tagsQueryKey } from "../../api/tags";
import EmptyState from "../../components/layout/EmptyState";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";

const priorityLabels: Record<ProspectPriority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High"
};

const statusLabels: Record<ProspectStatus, string> = {
  NEW: "New",
  RESEARCHING: "Researching",
  PROMOTED: "Promoted",
  DROPPED: "Dropped"
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
      className="h-9 w-full min-w-36 rounded-md border border-transparent bg-transparent px-2 text-sm text-text outline-none transition placeholder:text-soft hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    />
  );
}

type PriorityCellProps = {
  disabled?: boolean;
  onCommit: (value: ProspectPriority) => void;
  value: ProspectPriority;
};

function PriorityCell({ disabled, onCommit, value }: PriorityCellProps) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(event) => onCommit(event.target.value as ProspectPriority)}
      className="h-9 w-full min-w-28 rounded-md border border-transparent bg-transparent px-2 text-sm text-text outline-none transition hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    >
      {prospectPriorities.map((priority) => (
        <option key={priority} value={priority} className="bg-elevated text-text">
          {priorityLabels[priority]}
        </option>
      ))}
    </select>
  );
}

type StatusCellProps = {
  disabled?: boolean;
  onCommit: (value: ProspectStatus) => void;
  value: ProspectStatus;
};

function StatusCell({ disabled, onCommit, value }: StatusCellProps) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(event) => onCommit(event.target.value as ProspectStatus)}
      className="h-9 w-full min-w-36 rounded-md border border-transparent bg-transparent px-2 text-sm text-text outline-none transition hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    >
      {prospectStatuses.map((status) => (
        <option key={status} value={status} className="bg-elevated text-text">
          {statusLabels[status]}
        </option>
      ))}
    </select>
  );
}

type TagsCellProps = {
  disabled?: boolean;
  onCommit: (tagIds: number[]) => void;
  tags: Tag[];
  value: Tag[];
};

function TagsCell({ disabled, onCommit, tags, value }: TagsCellProps) {
  const selectedIds = value.map((tag) => String(tag.id));

  return (
    <select
      multiple
      value={selectedIds}
      disabled={disabled}
      onChange={(event) =>
        onCommit(Array.from(event.currentTarget.selectedOptions).map((option) => Number(option.value)))
      }
      className="h-20 w-full min-w-44 rounded-md border border-transparent bg-transparent px-2 py-1 text-sm text-text outline-none transition hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    >
      {tags.length === 0 ? (
        <option disabled className="bg-elevated text-muted">
          No tags
        </option>
      ) : null}
      {tags.map((tag) => (
        <option key={tag.id} value={tag.id} className="bg-elevated text-text">
          {tag.name}
        </option>
      ))}
    </select>
  );
}

type NewProspectFormProps = {
  isSaving: boolean;
  onCancel: () => void;
  onSubmit: (input: ProspectCreateInput) => void;
  tags: Tag[];
};

function NewProspectForm({ isSaving, onCancel, onSubmit, tags }: NewProspectFormProps) {
  const [name, setName] = useState("");
  const [url, setUrl] = useState("");
  const [note, setNote] = useState("");
  const [priority, setPriority] = useState<ProspectPriority>("MEDIUM");
  const [status, setStatus] = useState<ProspectStatus>("NEW");
  const [tagIds, setTagIds] = useState<number[]>([]);
  const [validation, setValidation] = useState<string | null>(null);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const cleanName = name.trim();
    if (!cleanName) {
      setValidation("Name is required.");
      return;
    }

    setValidation(null);
    onSubmit({
      name: cleanName,
      url: url.trim() || undefined,
      note: note.trim() || undefined,
      priority,
      status,
      tagIds
    });
  }

  return (
    <form onSubmit={handleSubmit} className="border-b border-line/70 bg-canvas/35 px-4 py-4">
      <div className="grid gap-3 lg:grid-cols-[minmax(13rem,1fr)_minmax(14rem,1.2fr)_9rem_11rem]">
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Name
          <input
            value={name}
            disabled={isSaving}
            onChange={(event) => setName(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
            placeholder="Company or role lead"
          />
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          URL
          <input
            value={url}
            disabled={isSaving}
            onChange={(event) => setUrl(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
            placeholder="https://..."
          />
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Priority
          <select
            value={priority}
            disabled={isSaving}
            onChange={(event) => setPriority(event.target.value as ProspectPriority)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
          >
            {prospectPriorities.map((option) => (
              <option key={option} value={option}>
                {priorityLabels[option]}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Status
          <select
            value={status}
            disabled={isSaving}
            onChange={(event) => setStatus(event.target.value as ProspectStatus)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
          >
            {prospectStatuses.map((option) => (
              <option key={option} value={option}>
                {statusLabels[option]}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="mt-3 grid gap-3 lg:grid-cols-[minmax(14rem,1fr)_minmax(16rem,1.2fr)]">
        <input
          value={note}
          disabled={isSaving}
          onChange={(event) => setNote(event.target.value)}
          className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
          placeholder="Note"
        />
        <select
          multiple
          value={tagIds.map(String)}
          disabled={isSaving}
          onChange={(event) =>
            setTagIds(Array.from(event.currentTarget.selectedOptions).map((option) => Number(option.value)))
          }
          className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none focus:border-accent/70 focus:shadow-focus"
        >
          {tags.map((tag) => (
            <option key={tag.id} value={tag.id}>
              {tag.name}
            </option>
          ))}
        </select>
      </div>
      {validation ? <p className="mt-3 text-sm text-warn">{validation}</p> : null}
      <div className="mt-3 flex justify-end gap-2">
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
          {isSaving ? "Saving" : "Add prospect"}
        </button>
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

export default function ProspectsPage() {
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [priorityFilter, setPriorityFilter] = useState<ProspectPriority | "">("");
  const [statusFilter, setStatusFilter] = useState<ProspectStatus | "">("");
  const [tagFilter, setTagFilter] = useState("");
  const [sorting, setSorting] = useState<SortingState>([{ id: "createdAt", desc: true }]);
  const [mutationError, setMutationError] = useState<unknown | null>(null);

  const sort = sorting[0];
  const params = useMemo(
    () => ({
      q: search.trim() || undefined,
      priority: priorityFilter || undefined,
      status: statusFilter || undefined,
      tagId: tagFilter ? Number(tagFilter) : undefined,
      sort: (sort?.id as ProspectSortField | undefined) ?? "createdAt",
      dir: sort?.desc ? ("desc" as const) : ("asc" as const)
    }),
    [priorityFilter, search, sort?.desc, sort?.id, statusFilter, tagFilter]
  );

  const query = useQuery({
    queryKey: prospectsQueryKey(params),
    queryFn: () => listProspects(params)
  });
  const tagsQuery = useQuery({
    queryKey: tagsQueryKey({ sort: "name", dir: "asc" }),
    queryFn: () => listTags({ sort: "name", dir: "asc" })
  });
  const tags = tagsQuery.data ?? [];

  function invalidateProspects() {
    void queryClient.invalidateQueries({ queryKey: ["prospects"] });
  }

  const createMutation = useMutation({
    mutationFn: createProspect,
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      setAddOpen(false);
      invalidateProspects();
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: ProspectUpdateInput }) => updateProspect(id, input),
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      invalidateProspects();
    }
  });

  const deleteMutation = useMutation({
    mutationFn: deleteProspect,
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      invalidateProspects();
    }
  });

  function commitUpdate(id: number, input: ProspectUpdateInput) {
    updateMutation.mutate({ id, input });
  }

  const columns = useMemo<ColumnDef<Prospect>[]>(
    () => [
      {
        accessorKey: "name",
        header: "Name",
        cell: ({ row }) => (
          <EditableTextCell
            required
            value={row.original.name}
            disabled={updateMutation.isPending}
            onCommit={(name) => commitUpdate(row.original.id, { name })}
          />
        )
      },
      {
        accessorKey: "url",
        header: "URL",
        enableSorting: false,
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.url}
            placeholder="Add URL"
            disabled={updateMutation.isPending}
            onCommit={(url) => commitUpdate(row.original.id, url ? { url } : { clearUrl: true })}
          />
        )
      },
      {
        accessorKey: "priority",
        header: "Priority",
        cell: ({ row }) => (
          <PriorityCell
            value={row.original.priority}
            disabled={updateMutation.isPending}
            onCommit={(priority) => {
              if (priority !== row.original.priority) {
                commitUpdate(row.original.id, { priority });
              }
            }}
          />
        )
      },
      {
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) => (
          <StatusCell
            value={row.original.status}
            disabled={updateMutation.isPending}
            onCommit={(status) => {
              if (status !== row.original.status) {
                commitUpdate(row.original.id, { status });
              }
            }}
          />
        )
      },
      {
        id: "tags",
        header: "Tags",
        enableSorting: false,
        cell: ({ row }) => (
          <TagsCell
            tags={tags}
            value={row.original.tags}
            disabled={updateMutation.isPending || tagsQuery.isLoading}
            onCommit={(tagIds) => commitUpdate(row.original.id, { tagIds })}
          />
        )
      },
      {
        accessorKey: "note",
        header: "Note",
        enableSorting: false,
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.note}
            placeholder="Add note"
            disabled={updateMutation.isPending}
            onCommit={(note) => commitUpdate(row.original.id, note ? { note } : { clearNote: true })}
          />
        )
      },
      {
        id: "promotedApplication",
        header: "Promoted",
        enableSorting: false,
        cell: ({ row }) => (
          <span className="block min-w-44 px-2 text-sm text-muted">
            {row.original.promotedApplication
              ? `${row.original.promotedApplication.roleTitle} · ${row.original.promotedApplication.companyName}`
              : "Not promoted"}
          </span>
        )
      },
      {
        accessorKey: "createdAt",
        header: "Added",
        cell: ({ row }) => (
          <span className="whitespace-nowrap px-2 text-sm text-muted">{formatDate(row.original.createdAt.slice(0, 10))}</span>
        )
      },
      {
        id: "actions",
        header: "",
        enableSorting: false,
        cell: ({ row }) => (
          <button
            type="button"
            disabled={deleteMutation.isPending}
            onClick={() => deleteMutation.mutate(row.original.id)}
            className="h-8 rounded-md border border-line/70 px-2 text-xs font-semibold text-muted transition hover:border-warn/60 hover:bg-warn/10 hover:text-warn disabled:cursor-wait disabled:opacity-60"
          >
            Delete
          </button>
        )
      }
    ],
    [deleteMutation, tags, tagsQuery.isLoading, updateMutation]
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
  const hasActiveFilters = Boolean(search.trim() || priorityFilter || statusFilter || tagFilter);

  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Prospects"
        title="Prospect list"
        description="Capture companies and roles before they become tracked applications."
        action={addOpen ? "Close" : "Add prospect"}
        onAction={() => {
          setMutationError(null);
          setAddOpen((open) => !open);
        }}
      />
      <WorkspacePanel title="Prospects" meta={meta}>
        <div className="-m-4 overflow-hidden rounded-lg border border-line/70">
          <div className="flex flex-col gap-3 border-b border-line/70 bg-surface/80 p-4 xl:flex-row xl:items-center xl:justify-between">
            <div className="flex flex-1 flex-col gap-3 md:flex-row md:items-center">
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="h-10 min-w-0 flex-1 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
                placeholder="Filter name, URL, note, tags, promoted app"
              />
              <select
                value={priorityFilter}
                onChange={(event) => setPriorityFilter(event.target.value as ProspectPriority | "")}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none focus:border-accent/70 focus:shadow-focus"
              >
                <option value="">All priorities</option>
                {prospectPriorities.map((priority) => (
                  <option key={priority} value={priority}>
                    {priorityLabels[priority]}
                  </option>
                ))}
              </select>
              <select
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value as ProspectStatus | "")}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none focus:border-accent/70 focus:shadow-focus"
              >
                <option value="">All statuses</option>
                {prospectStatuses.map((status) => (
                  <option key={status} value={status}>
                    {statusLabels[status]}
                  </option>
                ))}
              </select>
              <select
                value={tagFilter}
                onChange={(event) => setTagFilter(event.target.value)}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none focus:border-accent/70 focus:shadow-focus"
              >
                <option value="">All tags</option>
                {tags.map((tag) => (
                  <option key={tag.id} value={tag.id}>
                    {tag.name}
                  </option>
                ))}
              </select>
            </div>
            {hasActiveFilters ? (
              <button
                type="button"
                onClick={() => {
                  setSearch("");
                  setPriorityFilter("");
                  setStatusFilter("");
                  setTagFilter("");
                }}
                className="h-10 rounded-md border border-line/80 px-3 text-sm font-medium text-muted transition hover:bg-elevated/70 hover:text-text"
              >
                Clear filters
              </button>
            ) : null}
          </div>
          {addOpen ? (
            <NewProspectForm
              tags={tags}
              isSaving={createMutation.isPending}
              onCancel={() => setAddOpen(false)}
              onSubmit={(input) => createMutation.mutate(input)}
            />
          ) : null}
          {mutationError ? (
            <div className="border-b border-warn/30 bg-warn/10 px-4 py-3 text-sm text-warn">
              {buildMutationMessage(mutationError)}
            </div>
          ) : null}
          {query.isError ? (
            <div className="border-b border-warn/30 bg-warn/10 px-4 py-3 text-sm text-warn">
              {buildMutationMessage(query.error)}
            </div>
          ) : null}
          <div className="overflow-x-auto">
            <table className="min-w-full border-separate border-spacing-0">
              <thead>
                {table.getHeaderGroups().map((headerGroup) => (
                  <tr key={headerGroup.id}>
                    {headerGroup.headers.map((header) => (
                      <th
                        key={header.id}
                        className="border-b border-line/70 bg-canvas/70 px-3 py-2 text-left text-xs font-semibold uppercase tracking-[0.12em] text-muted"
                      >
                        {header.isPlaceholder ? null : (
                          <button
                            type="button"
                            disabled={!header.column.getCanSort()}
                            onClick={header.column.getToggleSortingHandler()}
                            className="inline-flex items-center gap-1 disabled:cursor-default"
                          >
                            {flexRender(header.column.columnDef.header, header.getContext())}
                            {header.column.getCanSort() ? (
                              <span className="text-soft">
                                {header.column.getIsSorted() === "asc"
                                  ? "↑"
                                  : header.column.getIsSorted() === "desc"
                                    ? "↓"
                                    : "↕"}
                              </span>
                            ) : null}
                          </button>
                        )}
                      </th>
                    ))}
                  </tr>
                ))}
              </thead>
              <tbody>
                {table.getRowModel().rows.map((row) => (
                  <tr key={row.id} className="group">
                    {row.getVisibleCells().map((cell) => (
                      <td
                        key={cell.id}
                        className="border-b border-line/50 px-3 py-2 align-middle transition group-hover:bg-elevated/25"
                      >
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!query.isLoading && table.getRowModel().rows.length === 0 ? (
            <div className="p-6">
              <EmptyState
                title={hasActiveFilters ? "No prospects match the filters" : "No prospects yet"}
                detail={hasActiveFilters ? "Clear filters or adjust the search." : "Add a prospect to start tracking early leads."}
              />
            </div>
          ) : null}
        </div>
      </WorkspacePanel>
    </div>
  );
}
