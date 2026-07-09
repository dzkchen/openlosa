import { type FormEvent, type KeyboardEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState
} from "@tanstack/react-table";
import {
  contactRelationships,
  contactsQueryKey,
  createContact,
  deleteContact,
  listContacts,
  updateContact,
  type Contact,
  type ContactCreateInput,
  type ContactRelationship,
  type ContactSortField,
  type ContactUpdateInput
} from "../../api/contacts";
import EmptyState from "../../components/layout/EmptyState";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";
import EmailFinderPanel, { type EmailFinderLaunch } from "../emailFinder/components/EmailFinderPanel";

const relationshipLabels: Record<ContactRelationship, string> = {
  RECRUITER: "Recruiter",
  ALUM: "Alum",
  REFERRAL: "Referral",
  OTHER: "Other"
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

type DateCellProps = {
  disabled?: boolean;
  onCommit: (value: string | null) => void;
  value: string | null;
};

function DateCell({ disabled, onCommit, value }: DateCellProps) {
  const [draft, setDraft] = useState(value ?? "");

  useEffect(() => {
    setDraft(value ?? "");
  }, [value]);

  function commit() {
    if (!draft) {
      if (value !== null) {
        onCommit(null);
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

type RelationshipCellProps = {
  disabled?: boolean;
  onCommit: (value: ContactRelationship) => void;
  value: ContactRelationship;
};

function RelationshipCell({ disabled, onCommit, value }: RelationshipCellProps) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(event) => onCommit(event.target.value as ContactRelationship)}
      className="h-9 w-full min-w-36 rounded-md border border-transparent bg-transparent px-2 text-sm text-text outline-none transition hover:border-line/70 hover:bg-elevated/50 focus:border-accent/60 focus:bg-elevated/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
    >
      {contactRelationships.map((relationship) => (
        <option key={relationship} value={relationship} className="bg-elevated text-text">
          {relationshipLabels[relationship]}
        </option>
      ))}
    </select>
  );
}

type NewContactFormProps = {
  isSaving: boolean;
  onCancel: () => void;
  onSubmit: (input: ContactCreateInput) => void;
};

function NewContactForm({ isSaving, onCancel, onSubmit }: NewContactFormProps) {
  const [name, setName] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [title, setTitle] = useState("");
  const [email, setEmail] = useState("");
  const [linkedinUrl, setLinkedinUrl] = useState("");
  const [relationship, setRelationship] = useState<ContactRelationship>("OTHER");
  const [lastContactedAt, setLastContactedAt] = useState("");
  const [notes, setNotes] = useState("");
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
      companyName: companyName.trim() || undefined,
      name: cleanName,
      title: title.trim() || undefined,
      email: email.trim() || undefined,
      linkedinUrl: linkedinUrl.trim() || undefined,
      relationship,
      lastContactedAt: lastContactedAt || undefined,
      notes: notes.trim() || undefined
    });
  }

  return (
    <form onSubmit={handleSubmit} className="border-b border-line/70 bg-canvas/35 px-4 py-4">
      <div className="grid gap-3 lg:grid-cols-[minmax(11rem,1fr)_minmax(10rem,1fr)_minmax(10rem,1fr)_minmax(12rem,1.1fr)]">
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Name
          <input
            value={name}
            disabled={isSaving}
            onChange={(event) => setName(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
            placeholder="Person name"
          />
        </label>
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
          Relationship
          <select
            value={relationship}
            disabled={isSaving}
            onChange={(event) => setRelationship(event.target.value as ContactRelationship)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
          >
            {contactRelationships.map((option) => (
              <option key={option} value={option}>
                {relationshipLabels[option]}
              </option>
            ))}
          </select>
        </label>
        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Email
          <input
            value={email}
            disabled={isSaving}
            onChange={(event) => setEmail(event.target.value)}
            className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus"
            placeholder="name@company.com"
          />
        </label>
      </div>
      <div className="mt-3 grid gap-3 lg:grid-cols-[minmax(10rem,1fr)_minmax(14rem,1.2fr)_10rem]">
        <input
          value={title}
          disabled={isSaving}
          onChange={(event) => setTitle(event.target.value)}
          className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
          placeholder="Role or title"
        />
        <input
          value={linkedinUrl}
          disabled={isSaving}
          onChange={(event) => setLinkedinUrl(event.target.value)}
          className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
          placeholder="LinkedIn URL"
        />
        <input
          type="date"
          value={lastContactedAt}
          disabled={isSaving}
          onChange={(event) => setLastContactedAt(event.target.value)}
          className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none [color-scheme:dark] focus:border-accent/70 focus:shadow-focus"
        />
      </div>
      <input
        value={notes}
        disabled={isSaving}
        onChange={(event) => setNotes(event.target.value)}
        className="mt-3 h-10 w-full rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
        placeholder="Notes"
      />
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
            {isSaving ? "Saving" : "Add contact"}
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

export default function ContactsPage() {
  const queryClient = useQueryClient();
  const finderPanelRef = useRef<HTMLDivElement>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [relationshipFilter, setRelationshipFilter] = useState<ContactRelationship | "">("");
  const [sorting, setSorting] = useState<SortingState>([{ id: "createdAt", desc: true }]);
  const [mutationError, setMutationError] = useState<unknown | null>(null);
  const [finderLaunch, setFinderLaunch] = useState<EmailFinderLaunch | null>(null);

  const sort = sorting[0];
  const params = useMemo(
    () => ({
      q: search.trim() || undefined,
      relationship: relationshipFilter || undefined,
      sort: (sort?.id as ContactSortField | undefined) ?? "createdAt",
      dir: sort?.desc ? ("desc" as const) : ("asc" as const)
    }),
    [relationshipFilter, search, sort?.desc, sort?.id]
  );

  const query = useQuery({
    queryKey: contactsQueryKey(params),
    queryFn: () => listContacts(params)
  });
  const allContactsQuery = useQuery({
    queryKey: contactsQueryKey({ sort: "name", dir: "asc" }),
    queryFn: () => listContacts({ sort: "name", dir: "asc" })
  });

  function invalidateContacts() {
    void queryClient.invalidateQueries({ queryKey: ["contacts"] });
  }

  const createMutation = useMutation({
    mutationFn: createContact,
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      setAddOpen(false);
      invalidateContacts();
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: ContactUpdateInput }) => updateContact(id, input),
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      invalidateContacts();
    }
  });

  const deleteMutation = useMutation({
    mutationFn: deleteContact,
    onMutate: () => setMutationError(null),
    onError: (error) => setMutationError(error),
    onSuccess: () => {
      setMutationError(null);
      invalidateContacts();
    }
  });

  const isMutating = createMutation.isPending || updateMutation.isPending || deleteMutation.isPending;

  function commitUpdate(id: number, input: ContactUpdateInput) {
    updateMutation.mutate({ id, input });
  }

  const launchEmailFinder = useCallback((contact: Contact) => {
    setFinderLaunch({
      contactId: contact.id,
      personName: contact.name,
      companyUrl: contact.company?.website ?? ""
    });
    requestAnimationFrame(() => finderPanelRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
  }, []);

  const columns = useMemo<ColumnDef<Contact>[]>(
    () => [
      {
        accessorKey: "name",
        header: "Name",
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.name}
            required
            disabled={updateMutation.isPending}
            onCommit={(name) => commitUpdate(row.original.id, { name })}
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
        accessorKey: "title",
        header: "Title",
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.title}
            placeholder="Add title"
            disabled={updateMutation.isPending}
            onCommit={(title) => commitUpdate(row.original.id, { title })}
          />
        )
      },
      {
        accessorKey: "email",
        header: "Email",
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.email}
            placeholder="Add email"
            disabled={updateMutation.isPending}
            onCommit={(email) =>
              commitUpdate(row.original.id, email ? { email } : { clearEmail: true })
            }
          />
        )
      },
      {
        accessorKey: "relationship",
        header: "Relationship",
        cell: ({ row }) => (
          <RelationshipCell
            value={row.original.relationship}
            disabled={updateMutation.isPending}
            onCommit={(relationship) => {
              if (relationship !== row.original.relationship) {
                commitUpdate(row.original.id, { relationship });
              }
            }}
          />
        )
      },
      {
        accessorKey: "lastContactedAt",
        header: "Last contacted",
        cell: ({ row }) => (
          <DateCell
            value={row.original.lastContactedAt}
            disabled={updateMutation.isPending}
            onCommit={(lastContactedAt) =>
              commitUpdate(
                row.original.id,
                lastContactedAt === null ? { clearLastContactedAt: true } : { lastContactedAt }
              )
            }
          />
        )
      },
      {
        accessorKey: "linkedinUrl",
        header: "LinkedIn",
        enableSorting: false,
        cell: ({ row }) => (
          <EditableTextCell
            value={row.original.linkedinUrl}
            placeholder="Add URL"
            disabled={updateMutation.isPending}
            onCommit={(linkedinUrl) => commitUpdate(row.original.id, { linkedinUrl })}
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
              onClick={() => launchEmailFinder(row.original)}
              className="h-8 rounded-md border border-accent/60 px-2 text-xs font-semibold text-accent transition hover:bg-accent/10"
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
    [deleteMutation, launchEmailFinder, updateMutation]
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
  const hasActiveFilters = Boolean(search.trim() || relationshipFilter);

  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Contacts"
        title="Contact workspace"
        description="People, relationships, email addresses, LinkedIn URLs, and last-contacted dates."
        action={addOpen ? "Close" : "Add contact"}
        onAction={() => {
          setMutationError(null);
          setAddOpen((open) => !open);
        }}
      />
      <div ref={finderPanelRef}>
        <EmailFinderPanel
          contacts={allContactsQuery.data ?? []}
          contactsLoading={allContactsQuery.isLoading}
          launch={finderLaunch}
        />
      </div>
      <WorkspacePanel title="Contacts" meta={meta}>
        <div className="-m-4 overflow-hidden rounded-lg border border-line/70">
          <div className="flex flex-col gap-3 border-b border-line/70 bg-surface/80 p-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-1 flex-col gap-3 sm:flex-row sm:items-center">
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="h-10 min-w-0 flex-1 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
                placeholder="Filter name, company, title, email, LinkedIn, notes"
              />
              <select
                value={relationshipFilter}
                onChange={(event) => setRelationshipFilter(event.target.value as ContactRelationship | "")}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm text-text outline-none focus:border-accent/70 focus:shadow-focus"
              >
                <option value="">All relationships</option>
                {contactRelationships.map((relationship) => (
                  <option key={relationship} value={relationship}>
                    {relationshipLabels[relationship]}
                  </option>
                ))}
              </select>
            </div>
            <button
              type="button"
              onClick={() => {
                setSearch("");
                setRelationshipFilter("");
                setSorting([{ id: "createdAt", desc: true }]);
              }}
              className="h-10 rounded-md border border-line/80 px-3 text-sm font-medium text-muted transition hover:bg-elevated/70 hover:text-text"
            >
              Reset
            </button>
          </div>

          {addOpen ? (
            <NewContactForm
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
              <EmptyState title="Could not load contacts" detail={buildMutationMessage(query.error)} />
            </div>
          ) : query.isLoading ? (
            <div className="p-4">
              <EmptyState title="Loading contacts" detail="Fetching the latest contact rows from the API." />
            </div>
          ) : table.getRowModel().rows.length === 0 ? (
            <div className="p-4">
              {hasActiveFilters ? (
                <EmptyState title="No matching contacts" detail="Adjust or reset the filters to show more contact rows." />
              ) : (
                <EmptyState title="No contacts yet" detail="Add recruiters, alumni, referrals, and other people you may contact." />
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-[82rem] w-full border-collapse">
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
    </div>
  );
}
