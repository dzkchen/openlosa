import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  chooseEmail,
  listEmailLookups,
  lookupEmail,
  type EmailCandidate,
  type EmailLookup,
  type EmailLookupStatus
} from "../../../api/emailFinder";
import { errorMessage } from "../../../api/client";
import type { Contact } from "../../../api/contacts";
import EmptyState from "../../../components/layout/EmptyState";
import WorkspacePanel from "../../../components/layout/WorkspacePanel";

export type EmailFinderLaunch = {
  contactId: number;
  personName: string;
  companyUrl: string;
};

type EmailFinderPanelProps = {
  contacts: Contact[];
  contactsLoading?: boolean;
  launch?: EmailFinderLaunch | null;
};

const statusLabels: Record<EmailLookupStatus, string> = {
  VERIFIED: "Verified",
  CATCH_ALL: "Catch-all",
  UNKNOWN: "Unknown",
  DOES_NOT_EXIST: "Does not exist"
};

const statusClasses: Record<EmailLookupStatus, string> = {
  VERIFIED: "border-good/35 bg-good/10 text-good",
  CATCH_ALL: "border-warn/35 bg-warn/10 text-warn",
  UNKNOWN: "border-accent/35 bg-accent/10 text-accent",
  DOES_NOT_EXIST: "border-line/80 bg-elevated/50 text-muted"
};

function sidecarFriendlyMessage(error: unknown) {
  const message = errorMessage(error, "Email Finder request failed.");
  const normalized = message.toLowerCase();
  if (normalized.includes("sidecar")) {
    if (normalized.includes("unavailable") || normalized.includes("timed out")) {
      return "Email Finder sidecar is unavailable or timed out. Start the email profile, then try again.";
    }
    if (normalized.includes("failed") || normalized.includes("invalid response") || normalized.includes("empty response")) {
      return "Email Finder sidecar failed. Check the sidecar logs, then try again.";
    }
  }
  return message;
}

function StatusBadge({ status }: { status: EmailLookupStatus }) {
  return (
    <span className={`inline-flex h-7 items-center rounded-md border px-2 text-xs font-semibold ${statusClasses[status]}`}>
      {statusLabels[status]}
    </span>
  );
}

function ConfidenceBadge({ confidence }: { confidence: number | null }) {
  return (
    <span className="inline-flex h-7 items-center rounded-md border border-line/70 bg-elevated/50 px-2 text-xs font-semibold text-muted">
      {confidence === null ? "No confidence" : `${confidence}% confidence`}
    </span>
  );
}

function CandidateMeta({ candidate }: { candidate: EmailCandidate }) {
  const items = [
    candidate.founder ? `For ${candidate.founder}` : null,
    candidate.mxHost ? `MX ${candidate.mxHost}` : null,
    candidate.smtpCode !== null ? `SMTP ${candidate.smtpCode}` : null,
    candidate.latencyMs !== null ? `${candidate.latencyMs} ms` : null,
    candidate.catchAllDomain ? "Catch-all domain" : null
  ].filter(Boolean);

  if (items.length === 0) {
    return null;
  }

  return <p className="mt-1 truncate text-xs text-soft">{items.join(" / ")}</p>;
}

function candidateSortRank(candidate: EmailCandidate, index: number) {
  return candidate.rank ?? index + 1;
}

function rankedCandidates(lookup: EmailLookup | null) {
  return (lookup?.candidates ?? [])
    .map((candidate, index) => ({
      candidate,
      displayRank: candidateSortRank(candidate, index),
      originalIndex: index
    }))
    .sort((left, right) => left.displayRank - right.displayRank || left.originalIndex - right.originalIndex);
}

export default function EmailFinderPanel({ contacts, contactsLoading, launch }: EmailFinderPanelProps) {
  const queryClient = useQueryClient();
  const requestGeneration = useRef(0);
  const [selectedContactId, setSelectedContactId] = useState("");
  const [personName, setPersonName] = useState("");
  const [companyUrl, setCompanyUrl] = useState("");
  const [count, setCount] = useState(3);
  const [lookup, setLookup] = useState<EmailLookup | null>(null);
  const [chosenEmail, setChosenEmail] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [validation, setValidation] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const selectedContact = useMemo(
    () => contacts.find((contact) => String(contact.id) === selectedContactId) ?? null,
    [contacts, selectedContactId]
  );
  const selectedContactMissing = Boolean(selectedContactId && !selectedContact);
  const selectedContactIdNumber = Number(selectedContactId);
  const previousLookupsQuery = useQuery({
    queryKey: ["email-lookups", selectedContactIdNumber],
    queryFn: () => listEmailLookups(selectedContactIdNumber),
    enabled: Number.isSafeInteger(selectedContactIdNumber) && selectedContactIdNumber > 0
  });

  useEffect(() => {
    if (!launch) {
      return;
    }

    requestGeneration.current += 1;
    setSelectedContactId(String(launch.contactId));
    setPersonName(launch.personName);
    setCompanyUrl(launch.companyUrl);
    setLookup(null);
    setChosenEmail(null);
    setSuccessMessage(null);
    setValidation(null);
    setToastMessage(null);
  }, [launch]);

  function clearLookupState() {
    requestGeneration.current += 1;
    setLookup(null);
    setChosenEmail(null);
    setSuccessMessage(null);
    setValidation(null);
  }

  function handleContactChange(value: string) {
    clearLookupState();
    setSelectedContactId(value);
    const contact = contacts.find((item) => String(item.id) === value);
    if (contact) {
      setPersonName(contact.name);
      setCompanyUrl(contact.company?.website ?? "");
    }
  }

  const lookupMutation = useMutation({
    mutationFn: ({ input }: { input: Parameters<typeof lookupEmail>[0]; generation: number }) => lookupEmail(input),
    onMutate: () => {
      setLookup(null);
      setChosenEmail(null);
      setSuccessMessage(null);
      setValidation(null);
      setToastMessage(null);
    },
    onError: (error, { generation }) => {
      if (generation !== requestGeneration.current) {
        return;
      }
      setToastMessage(sidecarFriendlyMessage(error));
    },
    onSuccess: (result, { generation }) => {
      void queryClient.invalidateQueries({ queryKey: ["email-lookups", result.contactId] });
      if (generation !== requestGeneration.current) {
        return;
      }
      setLookup(result);
      setChosenEmail(result.chosenEmail);
      setToastMessage(null);
    }
  });

  const chooseMutation = useMutation({
    mutationFn: ({ input }: { input: Parameters<typeof chooseEmail>[0]; generation: number }) => chooseEmail(input),
    onMutate: () => {
      setSuccessMessage(null);
      setToastMessage(null);
    },
    onError: (error, { generation }) => {
      if (generation !== requestGeneration.current) {
        return;
      }
      setToastMessage(errorMessage(error, "Email Finder request failed."));
    },
    onSuccess: (result, { generation }) => {
      void queryClient.invalidateQueries({ queryKey: ["contacts"] });
      void queryClient.invalidateQueries({ queryKey: ["outreach"] });
      void queryClient.invalidateQueries({ queryKey: ["email-lookups", result.contactId] });
      if (generation !== requestGeneration.current) {
        return;
      }
      setChosenEmail(result.chosenEmail);
      setSuccessMessage(
        result.outreachId
          ? `Filled ${result.chosenEmail} and queued a to-send outreach.`
          : `Filled ${result.chosenEmail}.`
      );
    }
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const contactId = Number(selectedContactId);
    const cleanName = personName.trim();
    const cleanUrl = companyUrl.trim();

    if (!contactId) {
      setValidation("Choose a contact before finding email.");
      return;
    }
    if (!cleanName) {
      setValidation("Person name is required.");
      return;
    }
    if (!cleanUrl) {
      setValidation("Company URL is required.");
      return;
    }

    const generation = requestGeneration.current + 1;
    requestGeneration.current = generation;
    lookupMutation.mutate({
      generation,
      input: {
        contactId,
        personName: cleanName,
        companyUrl: cleanUrl,
        count,
        includeCatchAll: true,
        includeUnknown: true
      }
    });
  }

  function chooseCandidate(candidate: EmailCandidate) {
    if (!lookup || !selectedContactId) {
      return;
    }
    chooseMutation.mutate({
      generation: requestGeneration.current,
      input: {
        lookupId: lookup.id,
        contactId: Number(selectedContactId),
        email: candidate.email,
        createOutreach: true
      }
    });
  }

  const formDisabled = lookupMutation.isPending || chooseMutation.isPending;
  const candidates = rankedCandidates(lookup);

  function reopenLookup(previousLookup: EmailLookup) {
    setLookup(previousLookup);
    setChosenEmail(previousLookup.chosenEmail);
    setPersonName(previousLookup.personName);
    setCompanyUrl(previousLookup.companyUrl);
    setSuccessMessage(null);
    setValidation(null);
    setToastMessage(null);
  }

  return (
    <WorkspacePanel title="Find email" meta={lookup ? `${candidates.length} candidates` : "Lookup"}>
      <div className="grid gap-4">
        <form onSubmit={handleSubmit} className="grid gap-4">
          <div className="grid gap-3 lg:grid-cols-[minmax(13rem,1fr)_minmax(12rem,1fr)_minmax(14rem,1.1fr)_8rem]">
            <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
              Contact
              <select
                value={selectedContactId}
                disabled={formDisabled || contactsLoading}
                onChange={(event) => handleContactChange(event.target.value)}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
              >
                <option value="">{contactsLoading ? "Loading contacts" : "Choose contact"}</option>
                {selectedContactMissing ? <option value={selectedContactId}>{personName || "Selected contact"}</option> : null}
                {contacts.map((contact) => (
                  <option key={contact.id} value={contact.id}>
                    {contact.name}
                    {contact.company ? ` · ${contact.company.name}` : ""}
                  </option>
                ))}
              </select>
            </label>
            <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
              Person
              <input
                value={personName}
                disabled={formDisabled}
                onChange={(event) => {
                  clearLookupState();
                  setPersonName(event.target.value);
                }}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
                placeholder="Full name"
              />
            </label>
            <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
              Company URL
              <input
                value={companyUrl}
                disabled={formDisabled}
                onChange={(event) => {
                  clearLookupState();
                  setCompanyUrl(event.target.value);
                }}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
                placeholder="https://company.com"
              />
            </label>
            <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
              Count
              <select
                value={count}
                disabled={formDisabled}
                onChange={(event) => {
                  clearLookupState();
                  setCount(Number(event.target.value));
                }}
                className="h-10 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none focus:border-accent/70 focus:shadow-focus disabled:cursor-wait disabled:text-muted"
              >
                <option value={3}>3</option>
                <option value={5}>5</option>
                <option value={10}>10</option>
              </select>
            </label>
          </div>

          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <p className="text-sm leading-6 text-muted">
              Generated addresses are ranked guesses, not guarantees. SMTP probes may be blocked on port 25.
              {selectedContact?.email ? ` Current email: ${selectedContact.email}.` : ""}
            </p>
            <button
              type="submit"
              disabled={formDisabled || !selectedContactId}
              className={`inline-flex h-10 items-center justify-center rounded-md bg-text px-4 text-sm font-medium text-canvas transition hover:bg-text/90 focus-visible:outline-none focus-visible:shadow-focus disabled:bg-soft disabled:text-text/60 ${
                formDisabled ? "disabled:cursor-wait" : "disabled:cursor-not-allowed"
              }`}
            >
              {lookupMutation.isPending ? "Finding" : "Find email"}
            </button>
          </div>

          {validation ? (
            <div role="alert" className="rounded-md border border-warn/30 bg-warn/10 px-3 py-2 text-sm text-warn">
              {validation}
            </div>
          ) : null}
        </form>

        {successMessage ? (
          <div className="rounded-md border border-good/30 bg-good/10 px-3 py-2 text-sm text-good">
            {successMessage}
          </div>
        ) : null}

        {previousLookupsQuery.isError ? (
          <div
            role="alert"
            className="flex flex-col gap-2 rounded-md border border-warn/30 bg-warn/10 px-3 py-2 text-sm text-warn sm:flex-row sm:items-center sm:justify-between"
          >
            <span>Could not load previous results: {errorMessage(previousLookupsQuery.error, "Email Finder request failed.")}</span>
            <button
              type="button"
              onClick={() => void previousLookupsQuery.refetch()}
              className="h-8 rounded-md border border-warn/40 px-2 text-xs font-semibold transition hover:bg-warn/10"
            >
              Retry
            </button>
          </div>
        ) : previousLookupsQuery.data && previousLookupsQuery.data.length > 0 ? (
          <div className="flex flex-wrap items-center gap-2 rounded-md border border-line/70 bg-canvas/35 px-3 py-2">
            <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted">Previous results</span>
            {previousLookupsQuery.data.slice(0, 5).map((previousLookup) => (
              <button
                key={previousLookup.id}
                type="button"
                disabled={formDisabled}
                onClick={() => reopenLookup(previousLookup)}
                className="rounded-md border border-line/70 px-2 py-1 text-xs text-muted transition hover:bg-elevated/80 hover:text-text disabled:cursor-wait disabled:text-soft"
              >
                {new Date(previousLookup.createdAt).toLocaleDateString()} · {previousLookup.candidates.length} candidates
              </button>
            ))}
          </div>
        ) : null}

        {lookup ? (
          <div className="rounded-md border border-line/70 bg-canvas/35">
            <div className="flex flex-col gap-1 border-b border-line/70 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-medium text-text">
                  {lookup.personName}
                  {lookup.company ? ` / ${lookup.company}` : ""}
                </p>
                <p className="text-xs text-muted">
                  {lookup.domain ?? lookup.companyUrl}
                  {lookup.permutationsProbed !== null ? ` / ${lookup.permutationsProbed} probed` : ""}
                </p>
              </div>
              {lookup.topStatus ? (
                <div className="flex flex-wrap gap-2">
                  <StatusBadge status={lookup.topStatus} />
                  <ConfidenceBadge confidence={lookup.topConfidence} />
                </div>
              ) : null}
            </div>

            {candidates.length === 0 ? (
              <div className="p-4">
                <EmptyState
                  title="No candidates returned"
                  detail="Try a company homepage URL, include a fuller name, or run the sidecar with SMTP access."
                />
              </div>
            ) : (
              <div className="divide-y divide-line/60">
                {candidates.map(({ candidate, displayRank, originalIndex }) => {
                  const isChosen = chosenEmail?.toLowerCase() === candidate.email.toLowerCase();
                  return (
                    <div
                      key={`${candidate.email}-${candidate.rank ?? originalIndex}`}
                      className="grid gap-3 px-4 py-3 md:grid-cols-[3rem_minmax(0,1fr)_auto]"
                    >
                      <div className="flex h-9 w-9 items-center justify-center rounded-md border border-line/70 bg-elevated/45 text-sm font-semibold text-muted">
                        {displayRank}
                      </div>
                      <div className="min-w-0">
                        <div className="flex min-w-0 flex-wrap items-center gap-2">
                          <p className="min-w-0 truncate text-sm font-medium text-text">{candidate.email}</p>
                          <StatusBadge status={candidate.status} />
                          <ConfidenceBadge confidence={candidate.confidence} />
                        </div>
                        <CandidateMeta candidate={candidate} />
                      </div>
                      <button
                        type="button"
                        disabled={chooseMutation.isPending || isChosen}
                        onClick={() => chooseCandidate(candidate)}
                        className={`h-9 rounded-md border border-accent/60 px-3 text-sm font-semibold text-accent transition hover:bg-accent/10 disabled:border-line/70 disabled:text-soft ${
                          chooseMutation.isPending ? "disabled:cursor-wait" : "disabled:cursor-not-allowed"
                        }`}
                      >
                        {isChosen ? "Chosen" : "Choose"}
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        ) : null}

        {toastMessage ? (
          <div
            role="alert"
            className="fixed bottom-5 left-4 right-4 z-50 flex items-start gap-3 rounded-lg border border-warn/30 bg-surface px-4 py-3 text-sm text-warn shadow-2xl sm:left-auto sm:max-w-sm"
          >
            <span className="leading-6">{toastMessage}</span>
            <button
              type="button"
              onClick={() => setToastMessage(null)}
              className="rounded-md border border-line/70 px-2 py-1 text-xs font-semibold text-muted transition hover:bg-elevated/80 hover:text-text"
            >
              Dismiss
            </button>
          </div>
        ) : null}
      </div>
    </WorkspacePanel>
  );
}
