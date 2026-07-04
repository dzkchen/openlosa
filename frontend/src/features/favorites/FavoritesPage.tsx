import EmptyState from "../../components/layout/EmptyState";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";

export default function FavoritesPage() {
  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Favorites"
        title="Saved focus list"
        description="Applications marked for repeated review and faster follow-up."
      />
      <WorkspacePanel title="Favorites" meta="0 rows">
        <EmptyState title="No favorites yet" detail="Favorite flags arrive with the application tracker in v0.1." />
      </WorkspacePanel>
    </div>
  );
}
