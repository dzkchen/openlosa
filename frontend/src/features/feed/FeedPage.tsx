import EmptyState from "../../components/layout/EmptyState";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";

export default function FeedPage() {
  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Feed"
        title="Internship feed"
        description="Tracked postings from the feed engine, ready to save as prospects or applications."
      />
      <WorkspacePanel title="Feed jobs" meta="Engine not connected">
        <EmptyState title="No feed jobs yet" detail="The engine sidecar and ingest table arrive in v0.4." />
      </WorkspacePanel>
    </div>
  );
}
