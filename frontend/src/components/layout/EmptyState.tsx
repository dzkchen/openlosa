type EmptyStateProps = {
  title: string;
  detail: string;
};

export default function EmptyState({ title, detail }: EmptyStateProps) {
  return (
    <div className="flex min-h-48 flex-col items-center justify-center rounded-md border border-dashed border-line/80 bg-canvas/45 px-6 py-8 text-center">
      <p className="text-sm font-medium text-text">{title}</p>
      <p className="mt-2 max-w-md text-sm leading-6 text-muted">{detail}</p>
    </div>
  );
}
