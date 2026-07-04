import { useState } from "react";
import PageHeader from "../../components/layout/PageHeader";
import ApplicationTracker from "./components/ApplicationTracker";

export default function ApplicationsPage() {
  const [addOpen, setAddOpen] = useState(false);

  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Applications"
        title="Application tracker"
        description="The primary spreadsheet replacement for roles, company records, statuses, dates, tags, and notes."
        action="New application"
        onAction={() => setAddOpen(true)}
        actionDisabled={addOpen}
      />
      <ApplicationTracker
        addOpen={addOpen}
        allowCreate
        emptyTitle="No applications yet"
        emptyDetail="Add the first role to start replacing the spreadsheet."
        onAddOpenChange={setAddOpen}
        title="Applications"
      />
    </div>
  );
}
