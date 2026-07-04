import type { PropsWithChildren } from "react";

type WorkspacePanelProps = PropsWithChildren<{
  title: string;
  meta?: string;
}>;

export default function WorkspacePanel({ title, meta, children }: WorkspacePanelProps) {
  return (
    <section className="rounded-lg border border-line/70 bg-surface/76">
      <div className="flex items-center justify-between gap-4 border-b border-line/70 px-4 py-3">
        <h2 className="text-sm font-semibold text-text">{title}</h2>
        {meta ? <span className="text-xs text-muted">{meta}</span> : null}
      </div>
      <div className="p-4">{children}</div>
    </section>
  );
}
