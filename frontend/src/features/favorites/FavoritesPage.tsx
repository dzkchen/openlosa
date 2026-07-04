import PageHeader from "../../components/layout/PageHeader";
import ApplicationTracker from "../applications/components/ApplicationTracker";

export default function FavoritesPage() {
  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Favorites"
        title="Saved focus list"
        description="Applications marked for repeated review and faster follow-up."
      />
      <ApplicationTracker
        emptyTitle="No favorites yet"
        emptyDetail="Mark applications as favorites from the tracker to keep them in this focus list."
        favoriteOnly
        title="Favorites"
      />
    </div>
  );
}
