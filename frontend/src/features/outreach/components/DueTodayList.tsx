import type { Outreach, OutreachStatus, OutreachUpdateInput } from "../../../api/outreach";
import { formatDate } from "../../../utils/format";

const statusLabels: Record<OutreachStatus, string> = {
  TO_SEND: "To send",
  SENT: "Follow-up due",
  REPLIED: "Replied",
  GHOSTED: "Ghosted"
};

type DueTodayListProps = {
  disabled?: boolean;
  errorMessage?: string | null;
  isLoading: boolean;
  items: Outreach[];
  onUpdate: (id: number, input: OutreachUpdateInput) => void;
};

function itemTitle(item: Outreach) {
  if (item.contact) {
    return item.contact.name;
  }
  if (item.company) {
    return item.company.name;
  }
  return "Standalone outreach";
}

function itemDetail(item: Outreach) {
  const pieces = [
    item.company?.name,
    item.contact?.email,
    item.followUpBy ? `Follow-up ${formatDate(item.followUpBy, "No date")}` : null,
    item.notes
  ].filter(Boolean);
  return pieces.join(" · ");
}

export default function DueTodayList({ disabled, errorMessage, isLoading, items, onUpdate }: DueTodayListProps) {
  return (
    <section className="rounded-lg border border-line/70 bg-surface/75">
      <div className="flex flex-col gap-1 border-b border-line/70 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-sm font-semibold text-text">Due today</h2>
          <p className="text-xs text-muted">Unsent outreach and sent messages with follow-ups due today or earlier.</p>
        </div>
        <span className="text-xs font-medium uppercase tracking-[0.12em] text-muted">
          {isLoading ? "Loading" : `${items.length} due`}
        </span>
      </div>
      {errorMessage ? (
        <div className="border-b border-warn/30 bg-warn/10 px-4 py-3 text-sm text-warn">{errorMessage}</div>
      ) : null}
      {items.length === 0 && !errorMessage ? (
        <div className="px-4 py-5 text-sm text-muted">
          {isLoading ? "Loading due outreach..." : "No outreach is due right now."}
        </div>
      ) : items.length > 0 ? (
        <div className="divide-y divide-line/60">
          {items.map((item) => (
            <div key={item.id} className="flex flex-col gap-3 px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="truncate text-sm font-medium text-text">{itemTitle(item)}</p>
                  <span className="rounded-md border border-line/70 bg-elevated/70 px-2 py-0.5 text-xs text-muted">
                    {statusLabels[item.status]}
                  </span>
                </div>
                <p className="mt-1 truncate text-xs text-muted">{itemDetail(item) || "No details"}</p>
              </div>
              <div className="flex shrink-0 flex-wrap gap-2">
                {item.status === "TO_SEND" ? (
                  <button
                    type="button"
                    disabled={disabled}
                    onClick={() => onUpdate(item.id, { status: "SENT" })}
                    className="h-8 rounded-md bg-text px-2.5 text-xs font-semibold text-canvas transition hover:bg-text/90 disabled:cursor-wait disabled:bg-soft disabled:text-text/60"
                  >
                    Mark sent
                  </button>
                ) : (
                  <>
                    <button
                      type="button"
                      disabled={disabled}
                      onClick={() => onUpdate(item.id, { status: "REPLIED" })}
                      className="h-8 rounded-md border border-line/70 px-2.5 text-xs font-semibold text-muted transition hover:border-accent/60 hover:bg-accent/10 hover:text-text disabled:cursor-wait disabled:opacity-60"
                    >
                      Replied
                    </button>
                    <button
                      type="button"
                      disabled={disabled}
                      onClick={() => onUpdate(item.id, { status: "GHOSTED" })}
                      className="h-8 rounded-md border border-line/70 px-2.5 text-xs font-semibold text-muted transition hover:border-warn/60 hover:bg-warn/10 hover:text-warn disabled:cursor-wait disabled:opacity-60"
                    >
                      Ghosted
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}
