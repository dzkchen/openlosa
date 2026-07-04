import EmptyState from "../../components/layout/EmptyState";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";

export default function OutreachPage() {
  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Outreach"
        title="Outreach queue"
        description="Cold emails, LinkedIn messages, referral asks, replies, and follow-up dates."
        action="Queue outreach"
      />
      <WorkspacePanel title="Outreach" meta="0 rows">
        <EmptyState title="No outreach yet" detail="Due dates and status transitions arrive in v0.2." />
      </WorkspacePanel>
    </div>
  );
}
