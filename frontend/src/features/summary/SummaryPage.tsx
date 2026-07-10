import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  dashboardSankeyQueryKey,
  dashboardStatsQueryKey,
  getDashboardSankey,
  getDashboardStats
} from "../../api/dashboard";
import {
  dueOutreachQueryKey,
  listDueOutreach,
  updateOutreach,
  type OutreachUpdateInput
} from "../../api/outreach";
import PageHeader from "../../components/layout/PageHeader";
import WorkspacePanel from "../../components/layout/WorkspacePanel";
import DueTodayList from "../outreach/components/DueTodayList";
import PipelineSankey from "./components/PipelineSankey";
import { errorMessage } from "../../api/client";

export default function SummaryPage() {
  const queryClient = useQueryClient();
  const statsQuery = useQuery({
    queryKey: dashboardStatsQueryKey(),
    queryFn: getDashboardStats
  });
  const sankeyQuery = useQuery({
    queryKey: dashboardSankeyQueryKey(),
    queryFn: getDashboardSankey
  });
  const dueQuery = useQuery({
    queryKey: dueOutreachQueryKey(),
    queryFn: listDueOutreach
  });
  const dueMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: OutreachUpdateInput }) => updateOutreach(id, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["outreach"] });
      void queryClient.invalidateQueries({ queryKey: ["contacts"] });
      void queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    }
  });

  const metrics = [
    { label: "Last 7 days", value: statsQuery.data?.applicationsLast7Days, tone: "text-accent" },
    { label: "Last 30 days", value: statsQuery.data?.applicationsLast30Days, tone: "text-text" },
    { label: "Last 60 days", value: statsQuery.data?.applicationsLast60Days, tone: "text-text" },
    { label: "No update", value: statsQuery.data?.noUpdate, tone: "text-warn" },
    { label: "Offers", value: statsQuery.data?.offers, tone: "text-good" },
    { label: "Ongoing", value: statsQuery.data?.ongoing, tone: "text-accent2" }
  ];
  const sankeyLinks = sankeyQuery.data ?? [];
  const dueItems = dueQuery.data ?? [];
  const dueError = dueMutation.error
    ? errorMessage(dueMutation.error, "Could not update outreach.")
    : dueQuery.error
      ? errorMessage(dueQuery.error, "Could not load due outreach.")
      : null;

  return (
    <div className="flex flex-1 flex-col gap-6">
      <PageHeader
        eyebrow="Summary"
        title="Pipeline overview"
        description="Application volume, current pipeline flow, and outreach requiring action."
      />

      {statsQuery.error ? (
        <div className="rounded-md border border-warn/30 bg-warn/10 px-4 py-3 text-sm text-warn">
          {errorMessage(statsQuery.error, "Could not load dashboard stats.")}
        </div>
      ) : null}

      <div className="grid gap-px overflow-hidden rounded-lg border border-line/70 bg-line/70 sm:grid-cols-3 xl:grid-cols-6">
        {metrics.map((metric) => (
          <div key={metric.label} className="bg-surface/95 px-4 py-4">
            <p className="text-xs font-medium uppercase tracking-[0.1em] text-muted">{metric.label}</p>
            <p className={`mt-3 text-3xl font-semibold tabular-nums ${metric.tone}`}>
              {metric.value ?? (statsQuery.isLoading ? "·" : "—")}
            </p>
          </div>
        ))}
      </div>

      <div className="grid flex-1 items-start gap-4 xl:grid-cols-[minmax(0,1.55fr)_minmax(20rem,0.65fr)]">
        <WorkspacePanel
          title="Pipeline flow"
          meta={sankeyQuery.isFetching ? "Refreshing" : `${sankeyLinks.length} links`}
        >
          {sankeyQuery.error ? (
            <div className="grid min-h-[28rem] place-items-center rounded-md border border-warn/30 bg-warn/5 px-6 text-center text-sm text-warn">
              {errorMessage(sankeyQuery.error, "Could not load pipeline flow.")}
            </div>
          ) : sankeyQuery.isLoading ? (
            <div className="grid min-h-[28rem] place-items-center rounded-md border border-line/60 bg-canvas/35 text-sm text-muted">
              Loading pipeline flow...
            </div>
          ) : (
            <PipelineSankey links={sankeyLinks} />
          )}
        </WorkspacePanel>

        <DueTodayList
          items={dueItems}
          isLoading={dueQuery.isLoading}
          errorMessage={dueError}
          disabled={dueMutation.isPending}
          onUpdate={(id, input) => dueMutation.mutate({ id, input })}
        />
      </div>
    </div>
  );
}
