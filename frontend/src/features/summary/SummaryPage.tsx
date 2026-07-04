import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";

const metrics = [
  { label: "Active applications", value: "0", tone: "text-accent" },
  { label: "Outreach due", value: "0", tone: "text-warn" },
  { label: "Favorites", value: "0", tone: "text-good" }
];

export default function SummaryPage() {
  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Summary"
        title="Pipeline overview"
        description="Application activity, follow-ups, and feed signals will roll up here as the tracker comes online."
      />

      <div className="grid gap-3 sm:grid-cols-3">
        {metrics.map((metric) => (
          <div key={metric.label} className="rounded-lg border border-line/70 bg-surface/76 px-4 py-4">
            <p className="text-sm text-muted">{metric.label}</p>
            <p className={`mt-3 text-3xl font-semibold ${metric.tone}`}>{metric.value}</p>
          </div>
        ))}
      </div>

      <div className="grid flex-1 gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(19rem,0.65fr)]">
        <WorkspacePanel title="Pipeline flow" meta="Sankey target">
          <div className="grid min-h-72 place-items-center rounded-md border border-line/70 bg-canvas/50">
            <div className="w-full max-w-lg px-6">
              <div className="h-2 rounded-full bg-gradient-to-r from-accent via-soft to-accent2" />
              <div className="mt-5 grid grid-cols-3 gap-3 text-center text-xs text-muted">
                <span>Saved</span>
                <span>Applied</span>
                <span>Interview</span>
              </div>
            </div>
          </div>
        </WorkspacePanel>

        <WorkspacePanel title="Due today" meta="0 items">
          <div className="space-y-3">
            <div className="rounded-md border border-line/70 bg-canvas/45 px-3 py-3">
              <p className="text-sm text-muted">No follow-ups are due.</p>
            </div>
            <div className="rounded-md border border-line/70 bg-canvas/45 px-3 py-3">
              <p className="text-sm text-muted">No queued outreach.</p>
            </div>
          </div>
        </WorkspacePanel>
      </div>
    </div>
  );
}
