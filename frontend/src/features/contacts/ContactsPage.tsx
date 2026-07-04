import EmptyState from "../../components/layout/EmptyState";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";

export default function ContactsPage() {
  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Contacts"
        title="Contact workspace"
        description="People, relationships, email addresses, LinkedIn URLs, and last-contacted dates."
        action="Add contact"
      />
      <WorkspacePanel title="Contacts" meta="0 rows">
        <EmptyState title="No contacts yet" detail="Contact tables and email finder entry points arrive in v0.2." />
      </WorkspacePanel>
    </div>
  );
}
