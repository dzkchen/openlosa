import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { importApplicationsCsv } from "../../api/applications";
import PageHeader from "../../components/layout/PageHeader";
import ApplicationTracker from "./components/ApplicationTracker";

export default function ApplicationsPage() {
  const [addOpen, setAddOpen] = useState(false);
  const [importMessage, setImportMessage] = useState<string | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  const importMutation = useMutation({
    mutationFn: importApplicationsCsv,
    onMutate: () => {
      setImportMessage(null);
      setImportError(null);
    },
    onError: (error) => {
      setImportError(error instanceof Error ? error.message : "CSV import failed.");
    },
    onSuccess: (result) => {
      setImportError(null);
      setImportMessage(`Imported ${result.importedCount} application${result.importedCount === 1 ? "" : "s"}.`);
      void queryClient.invalidateQueries({ queryKey: ["applications"] });
    }
  });

  function handleImportChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = "";

    if (file) {
      importMutation.mutate(file);
    }
  }

  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Applications"
        title="Application tracker"
        description="The primary spreadsheet replacement for roles, company records, statuses, dates, tags, and notes."
        secondaryAction={importMutation.isPending ? "Importing" : "Import CSV"}
        onSecondaryAction={() => fileInputRef.current?.click()}
        secondaryActionDisabled={importMutation.isPending}
        action="New application"
        onAction={() => setAddOpen(true)}
        actionDisabled={addOpen}
      />
      <input
        ref={fileInputRef}
        type="file"
        accept=".csv,text/csv"
        className="hidden"
        onChange={handleImportChange}
      />
      {importMessage || importError ? (
        <div
          className={`rounded-lg border px-4 py-3 text-sm ${
            importError
              ? "border-warn/30 bg-warn/10 text-warn"
              : "border-good/30 bg-good/10 text-good"
          }`}
        >
          {importError ?? importMessage}
        </div>
      ) : null}
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
