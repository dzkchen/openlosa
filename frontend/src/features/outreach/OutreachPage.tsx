import { type FormEvent, type KeyboardEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState
} from "@tanstack/react-table";
import { contactsQueryKey, listContacts, type Contact } from "../../api/contacts";
import {
  createOutreach,
  deleteOutreach,
  dueOutreachQueryKey,
  listDueOutreach,
  listOutreach,
  outreachQueryKey,
  outreachStatuses,
  outreachTypes,
  updateOutreach,
  type Outreach,
  type OutreachCreateInput,
  type OutreachSortField,
  type OutreachStatus,
  type OutreachType,
  type OutreachUpdateInput
} from "../../api/outreach";
import EmptyState from "../../components/layout/EmptyState";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";
import EmailFinderPanel, { type EmailFinderLaunch } from "../emailFinder/components/EmailFinderPanel";
import DueTodayList from "./components/DueTodayList";

const typeLabels: Record<OutreachType, string> = {
  COLD_EMAIL: "Cold email",
  LINKEDIN_DM: "LinkedIn DM",
  REFERRAL_ASK: "Referral ask",
  OTHER: "Other"
};

const statusLabels: Record<OutreachStatus, string> = {
  TO_SEND: "To send",
  SENT: "Sent",
  REPLIED: "Replied",
  GHOSTED: "Ghosted"
};

const nextStatuses: Record<OutreachStatus, OutreachStatus[]> = {
  TO_SEND: ["TO_SEND", "SENT"],
  SENT: ["SENT", "REPLIED", "GHOSTED"],
  REPLIED: ["REPLIED"],
  GHOSTED: ["GHOSTED"]
};

type EditableTextCellProps = {
  disabled?: boolean;
  onCommit: (value: string) => void;
  placeholder?: string;
  value: string | null;
};

function EditableTextCell({ disabled, onCommit, placeholder, value }: EditableTextCellProps) {
  const [draft, setDraft] = useState(value ?? "");

  useEffect(() => {
    setDraft(value ?? "");
  }, [value]);

  function commit() {
    const next = draft.trim();
    const current = (value ?? "").trim();
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

type DateCellProps = {
  clearable?: boolean;
  disabled?: boolean;
  onCommit: (value: string | null) => void;
  value: string | null;
};

function DateCell({ clearable = true, disabled, onCommit, value }: DateCellProps) {
  const [draft, setDraft] = useState(value ?? "");

  useEffect(() => {
    setDraft(value ?? "");
  }, [value]);

  function commit() {
    if (!draft) {
      if (value !== null) {
        if (clearable) {
          onCommit(null);
        } else {
          setDraft(value);
        }
      }
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

type ContactCellProps = {
  contacts: Contact[];
  disabled?: boolean;
  onCommit: (contactId: number | null) => void;
  value: Contact | null;
};

function ContactCell({ contacts, disabled, onCommit, value }: ContactCellProps) {
  return (
    <select
      value={value?.id ?? ""}
      disabled={disabled}
      onChange={(event) => onCommit(event.target.value ? Number(event.target.value) : null)}
      className="h-9 w-full min-w-44 rounded-md border border-transparent bg-transparent px-2 text-sm text-text outline-none transition hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    >
      <option value="" className="bg-elevated text-text">
        No contact
      </option>
      {contacts.map((contact) => (
        <option key={contact.id} value={contact.id} className="bg-elevated text-text">
          {contact.name}
          {contact.company ? ` · ${contact.company.name}` : ""}
        </option>
      ))}
    </select>
  );
}

type TypeCellProps = {
  disabled?: boolean;
  onCommit: (type: OutreachType) => void;
  value: OutreachType;
};

function TypeCell({ disabled, onCommit, value }: TypeCellProps) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(event) => onCommit(event.target.value as OutreachType)}
      className="h-9 w-full min-w-36 rounded-md border border-transparent bg-transparent px-2 text-sm text-text outline-none transition hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    >
      {outreachTypes.map((type) => (
        <option key={type} value={type} className="bg-elevated text-text">
          {typeLabels[type]}
        </option>
      ))}
    </select>
  );
}

type StatusCellProps = {
  disabled?: boolean;
  onCommit: (status: OutreachStatus) => void;
  value: OutreachStatus;
};

function StatusCell({ disabled, onCommit, value }: StatusCellProps) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(event) => onCommit(event.target.value as OutreachStatus)}
      className="h-9 w-full min-w-32 rounded-md border border-transparent bg-transparent px-2 text-sm text-text outline-none transition hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    >
      {nextStatuses[value].map((status) => (
        <option key={status} value={status} className="bg-elevated text-text">
          {statusLabels[status]}
        </option>
      ))}
    </select>
  );
}

type NewOutreachFormProps = {
  contacts: Contact[];
  isSaving: boolean;
  onCancel: () => void;
  onSubmit: (input: OutreachCreateInput) => void;
};

function NewOutreachForm({ contacts, isSaving, onCancel, onSubmit }: NewOutreachFormProps) {
  const [contactId, setContactId] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [type, setType] = useState<OutreachType>("COLD_EMAIL");
  const [status, setStatus] = useState<OutreachStatus>("TO_SEND");
  const [sentAt, setSentAt] = useState("");
  const [followUpBy, setFollowUpBy] = useState("");
  const [notes, setNotes] = useState("");

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    onSubmit({
      contactId: contactId ? Number(contactId) : undefined,
      companyName: companyName.trim() || undefined,
      type,
      status,
      sentAt: status === "SENT" && sentAt ? sentAt : undefined,
      followUpBy: followUpBy || undefined,
      notes: notes.trim() || undefined
    });
  }

  return (
    <form onSubmit={handleSubmit} className="border-b border-line/70 bg-canvas/35 px-4 py-4">
      <div className="grid gap-3 lg:grid-cols-[minmax(12rem,1fr)_minmax(10rem,1fr)_10rem_9rem_9rem_9rem]">
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Contact
          <select
            value={contactId}
            disabled={isSaving}
            onChange={(event) => setContactId(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
          >
            <option value="">No contact</option>
            {contacts.map((contact) => (
              <option key={contact.id} value={contact.id}>
                {contact.name}
                {contact.company ? ` · ${contact.company.name}` : ""}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Company
          <input
            value={companyName}
            disabled={isSaving}
            onChange={(event) => setCompanyName(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
            placeholder="Optional"
          />
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Type
          <select
            value={type}
            disabled={isSaving}
            onChange={(event) => setType(event.target.value as OutreachType)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
          >
            {outreachTypes.map((option) => (
              <option key={option} value={option}>
                {typeLabels[option]}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Status
          <select
            value={status}
            disabled={isSaving}
            onChange={(event) => setStatus(event.target.value as OutreachStatus)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
          >
            <option value="TO_SEND">To send</option>
            <option value="SENT">Sent</option>
          </select>
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Sent
          <input
            type="date"
            value={sentAt}
            disabled={isSaving || status !== "SENT"}
            onChange={(event) => setSentAt(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none [color-scheme:dark] focus:border-accent/70 focus:shadow-focus disabled:text-muted"
          />
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Follow-up
          <input
            type="date"
            value={followUpBy}
            disabled={isSaving}
            onChange={(event) => setFollowUpBy(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none [color-scheme:dark] focus:border-accent/70 focus:shadow-focus"
          />
        </label>
      </div>
      <input
        value={notes}
        disabled={isSaving}
        onChange={(event) => setNotes(event.target.value)}
        className="mt-3 h-10 w-full rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
        placeholder="Notes"
      />
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
          {isSaving ? "Saving" : "Queue outreach"}
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

export default function OutreachPage() {
  const queryClient = useQueryClient();
  const finderPanelRef = useRef<HTMLDivElement>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<OutreachStatus | "">("");
  const [typeFilter, setTypeFilter] = useState<OutreachType | "">("");
  const [sorting, setSorting] = useState<SortingState>([{ id: "createdAt", desc: true }]);
  const [mutationError, setMutationError] = useState<unknown | null>(null);
  const [finderLaunch, setFinderLaunch] = useState<EmailFinderLaunch | null>(null);

  const sort = sorting[0];
  const params = useMemo(
    () => ({
      q: search.trim() || undefined,
      status: statusFilter || undefined,
      type: typeFilter || undefined,
      sort: (sort?.id as OutreachSortField | undefined) ?? "createdAt",
      dir: sort?.desc ? ("desc" as const) : ("asc" as const)
    }),
    [search, sort?.desc, sort?.id, statusFilter, typeFilter]
  );

  const query = useQuery({
    queryKey: outreachQueryKey(params),
    queryFn: () => listOutreach(params)
  });
  const dueQuery = useQuery({
    queryKey: dueOutreachQueryKey(),
    queryFn: listDueOutreach
  });
  const contactsQuery = useQuery({
    queryKey: contactsQueryKey({ sort: "name", dir: "asc" }),
    queryFn: () => listContacts({ sort: "name", dir: "asc" })
  });
  const contacts = contactsQuery.data ?? [];

  function invalidateOutreach() {
    void queryClient.invalidateQueries({ queryKey: ["outreach"] });
    void queryClient.invalidateQueries({ queryKey: ["contacts"] });
  }

  const createMutation = useMutation({
    mutationFn: createOutreach,
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      setAddOpen(false);
      invalidateOutreach();
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: OutreachUpdateInput }) => updateOutreach(id, input),
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      invalidateOutreach();
    }
  });

  const deleteMutation = useMutation({
    mutationFn: deleteOutreach,
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      invalidateOutreach();
    }
  });

  function commitUpdate(id: number, input: OutreachUpdateInput) {
    updateMutation.mutate({ id, input });
  }

  const launchEmailFinder = useCallback((outreach: Outreach) => {
    if (!outreach.contact) {
      return;
    }
    setFinderLaunch({
      contactId: outreach.contact.id,
      personName: outreach.contact.name,
      companyUrl: outreach.contact.company?.website ?? outreach.company?.website ?? ""
    });
    requestAnimationFrame(() => finderPanelRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
  }, []);

  const columns = useMemo<ColumnDef<Outreach>[]>(
    () => [
      {
        id: "contact",
        accessorFn: (row) => row.contact?.name ?? "",
        header: "Contact",
        cell: ({ row }) => (
          <ContactCell
            contacts={contacts}
            value={row.original.contact}
            disabled={updateMutation.isPending}
            onCommit={(contactId) =>
              commitUpdate(row.original.id, contactId === null ? { clearContact: true } : { contactId })
            }
          />
        )
      },
      {
        id: "company",
        accessorFn: (row) => row.company?.name ?? "",
        header: "Company",
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.company?.name ?? null}
            placeholder="Add company"
            disabled={updateMutation.isPending}
            onCommit={(companyName) =>
              commitUpdate(row.original.id, companyName ? { companyName } : { clearCompany: true })
            }
          />
        )
      },
      {
        id: "application",
        accessorFn: (row) => row.application?.roleTitle ?? "",
        header: "Application",
        cell: ({ row }) => (
          <span className="block min-w-44 px-2 text-sm text-muted">
            {row.original.application
              ? `${row.original.application.roleTitle} · ${row.original.application.companyName}`
              : "Not linked"}
          </span>
        )
      },
      {
        accessorKey: "type",
        header: "Type",
        cell: ({ row }) => (
          <TypeCell
            value={row.original.type}
            disabled={updateMutation.isPending}
            onCommit={(type) => {
              if (type !== row.original.type) {
                commitUpdate(row.original.id, { type });
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
        accessorKey: "sentAt",
        header: "Sent",
        cell: ({ row }) => (
          <DateCell
            clearable={false}
            value={row.original.sentAt}
            disabled={updateMutation.isPending || row.original.status === "TO_SEND"}
            onCommit={(sentAt) =>
              commitUpdate(row.original.id, sentAt === null ? { clearSentAt: true } : { sentAt })
            }
          />
        )
      },
      {
        accessorKey: "followUpBy",
        header: "Follow-up by",
        cell: ({ row }) => (
          <DateCell
            value={row.original.followUpBy}
            disabled={updateMutation.isPending}
            onCommit={(followUpBy) =>
              commitUpdate(row.original.id, followUpBy === null ? { clearFollowUpBy: true } : { followUpBy })
            }
          />
        )
      },
      {
        accessorKey: "notes",
        header: "Notes",
        enableSorting: false,
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.notes}
            placeholder="Add notes"
            disabled={updateMutation.isPending}
            onCommit={(notes) => commitUpdate(row.original.id, { notes })}
          />
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
          <div className="flex min-w-44 items-center gap-2">
            <button
              type="button"
              disabled={!row.original.contact}
              onClick={() => launchEmailFinder(row.original)}
              className="h-8 rounded-md border border-accent/60 px-2 text-xs font-semibold text-accent transition hover:bg-accent/10 disabled:cursor-not-allowed disabled:border-line/70 disabled:text-soft"
            >
              Find email
            </button>
            <button
              type="button"
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(row.original.id)}
              className="h-8 rounded-md border border-line/70 px-2 text-xs font-semibold text-muted transition hover:border-warn/60 hover:bg-warn/10 hover:text-warn disabled:cursor-wait disabled:opacity-60"
            >
              Delete
            </button>
          </div>
        )
      }
    ],
    [contacts, deleteMutation, launchEmailFinder, updateMutation]
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
  const hasActiveFilters = Boolean(search.trim() || statusFilter || typeFilter);

  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Outreach"
        title="Outreach queue"
        description="Queue messages, mark sends, track replies, and keep follow-up dates visible."
        action={addOpen ? "Close" : "Queue outreach"}
        onAction={() => {
          setMutationError(null);
          setAddOpen((open) => !open);
        }}
      />
      <DueTodayList
        items={dueQuery.data ?? []}
        isLoading={dueQuery.isLoading}
        errorMessage={dueQuery.isError ? buildMutationMessage(dueQuery.error) : null}
        disabled={updateMutation.isPending}
        onUpdate={commitUpdate}
      />
      {finderLaunch ? (
        <div ref={finderPanelRef}>
          <EmailFinderPanel contacts={contacts} contactsLoading={contactsQuery.isLoading} launch={finderLaunch} />
        </div>
      ) : null}
      <WorkspacePanel title="Outreach" meta={meta}>
        <div className="-m-4 overflow-hidden rounded-lg border border-line/70">
          <div className="flex flex-col gap-3 border-b border-line/70 bg-surface/80 p-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-1 flex-col gap-3 sm:flex-row sm:items-center">
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="h-10 min-w-0 flex-1 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
                placeholder="Filter contact, company, application, email, notes"
              />
              <select
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value as OutreachStatus | "")}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none focus:border-accent/70 focus:shadow-focus"
              >
                <option value="">All statuses</option>
                {outreachStatuses.map((status) => (
                  <option key={status} value={status}>
                    {statusLabels[status]}
                  </option>
                ))}
              </select>
              <select
                value={typeFilter}
                onChange={(event) => setTypeFilter(event.target.value as OutreachType | "")}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none focus:border-accent/70 focus:shadow-focus"
              >
                <option value="">All types</option>
                {outreachTypes.map((type) => (
                  <option key={type} value={type}>
                    {typeLabels[type]}
                  </option>
                ))}
              </select>
            </div>
            <button
              type="button"
              onClick={() => {
                setSearch("");
                setStatusFilter("");
                setTypeFilter("");
                setSorting([{ id: "createdAt", desc: true }]);
              }}
              className="h-10 rounded-md border border-line/80 px-3 text-sm font-medium text-muted transition hover:bg-elevated/70 hover:text-text"
            >
              Reset
            </button>
          </div>

          {addOpen ? (
            <NewOutreachForm
              contacts={contacts}
              isSaving={createMutation.isPending}
              onCancel={() => {
                setMutationError(null);
                setAddOpen(false);
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
              <EmptyState title="Could not load outreach" detail={buildMutationMessage(query.error)} />
            </div>
          ) : query.isLoading ? (
            <div className="p-4">
              <EmptyState title="Loading outreach" detail="Fetching the latest outreach rows from the API." />
            </div>
          ) : table.getRowModel().rows.length === 0 ? (
            <div className="p-4">
              {hasActiveFilters ? (
                <EmptyState title="No matching outreach" detail="Adjust or reset the filters to show more outreach rows." />
              ) : (
                <EmptyState title="No outreach yet" detail="Queue a message, then mark it sent when it leaves your inbox." />
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-[86rem] w-full border-collapse">
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
                    <tr key={row.id} className="border-b border-line/50 last:border-0 hover:bg-elevated/25">
                      {row.getVisibleCells().map((cell) => (
                        <td key={cell.id} className="h-12 px-1 align-middle">
                          {flexRender(cell.column.columnDef.cell, cell.getContext())}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </WorkspacePanel>
    </div>
  );
}
