import EmptyState from "../../components/layout/EmptyState";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";

export default function ProspectsPage() {
  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Prospects"
        title="Prospect list"
        description="Companies and roles discovered before they become tracked applications."
        action="Add prospect"
      />
      <WorkspacePanel title="Prospects" meta="0 rows">
        <EmptyState title="No prospects yet" detail="Quick-add and promotion flows arrive in v0.2." />
      </WorkspacePanel>
    </div>
  );
}
