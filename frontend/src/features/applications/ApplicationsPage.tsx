import EmptyState from "../../components/layout/EmptyState";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";

export default function ApplicationsPage() {
  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Applications"
        title="Application tracker"
        description="The primary spreadsheet replacement for roles, company records, statuses, dates, tags, and notes."
        action="New application"
      />
      <WorkspacePanel title="Applications" meta="0 rows">
        <EmptyState title="No applications yet" detail="Application CRUD and inline editing arrive in v0.1." />
      </WorkspacePanel>
    </div>
  );
}
