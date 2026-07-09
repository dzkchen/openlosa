import { ResponsiveSankey } from "@nivo/sankey";
import { useMemo } from "react";

import type { DashboardSankeyLink } from "../../../api/dashboard";

type PipelineSankeyProps = {
  links: DashboardSankeyLink[];
};

function containsCycle(links: DashboardSankeyLink[]) {
  const nodes = new Set<string>();
  const outgoing = new Map<string, Set<string>>();
  const incomingCount = new Map<string, number>();

  for (const link of links) {
    nodes.add(link.from);
    nodes.add(link.to);
    if (!outgoing.has(link.from)) {
      outgoing.set(link.from, new Set());
    }
    if (!outgoing.get(link.from)?.has(link.to)) {
      outgoing.get(link.from)?.add(link.to);
      incomingCount.set(link.to, (incomingCount.get(link.to) ?? 0) + 1);
    }
  }

  const queue = [...nodes].filter((node) => !incomingCount.has(node));
  let visited = 0;
  for (let index = 0; index < queue.length; index += 1) {
    const node = queue[index];
    visited += 1;
    for (const target of outgoing.get(node) ?? []) {
      const nextCount = (incomingCount.get(target) ?? 0) - 1;
      incomingCount.set(target, nextCount);
      if (nextCount === 0) {
        queue.push(target);
      }
    }
  }

  return visited !== nodes.size;
}

export default function PipelineSankey({ links }: PipelineSankeyProps) {
  const data = useMemo(() => {
    const nodeIds = new Set<string>();
    for (const link of links) {
      nodeIds.add(link.from);
      nodeIds.add(link.to);
    }

    return {
      nodes: [...nodeIds].map((id) => ({ id })),
      links: links.map((link) => ({
        source: link.from,
        target: link.to,
        value: link.count
      }))
    };
  }, [links]);

  if (links.length === 0) {
    return (
      <div className="grid min-h-[28rem] place-items-center rounded-md border border-dashed border-line/70 bg-canvas/35 px-6 text-center">
        <div>
          <p className="text-sm font-medium text-text">No pipeline activity yet</p>
          <p className="mt-1 text-xs text-muted">Application status changes will appear here.</p>
        </div>
      </div>
    );
  }

  if (containsCycle(links)) {
    return (
      <div className="grid min-h-[28rem] place-items-center rounded-md border border-warn/30 bg-warn/5 px-6 text-center">
        <div className="max-w-sm">
          <p className="text-sm font-medium text-warn">Pipeline history contains a cycle</p>
          <p className="mt-2 text-xs leading-5 text-muted">
            Sankey diagrams require forward-only history. Use undo for status corrections, then refresh this view.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="h-[28rem] rounded-md border border-line/60 bg-canvas/35">
      <ResponsiveSankey
        data={data}
        margin={{ top: 28, right: 128, bottom: 28, left: 128 }}
        align="justify"
        colors={["#7e91ff", "#6f7ee8", "#29d3c2", "#39c484", "#ebb550", "#946ee8", "#66728a"]}
        nodeOpacity={0.95}
        nodeHoverOpacity={1}
        nodeHoverOthersOpacity={0.28}
        nodeThickness={12}
        nodeSpacing={18}
        nodeBorderWidth={1}
        nodeBorderColor={{ from: "color", modifiers: [["brighter", 0.35]] }}
        nodeBorderRadius={3}
        linkOpacity={0.22}
        linkHoverOpacity={0.68}
        linkHoverOthersOpacity={0.08}
        linkContract={2}
        enableLinkGradient
        labelPadding={12}
        labelTextColor="#eef1f7"
        valueFormat={(value) => `${value} application${value === 1 ? "" : "s"}`}
        theme={{
          text: { fill: "#eef1f7", fontSize: 11 },
          tooltip: {
            container: {
              background: "#141823",
              color: "#eef1f7",
              border: "1px solid #2a3140",
              borderRadius: "6px",
              boxShadow: "0 10px 30px rgba(0, 0, 0, 0.35)"
            }
          }
        }}
        role="img"
        ariaLabel="Application pipeline flow"
      />
    </div>
  );
}
