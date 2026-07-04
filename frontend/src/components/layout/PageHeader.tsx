type PageHeaderProps = {
  title: string;
  eyebrow: string;
  description: string;
  action?: string;
};

export default function PageHeader({ title, eyebrow, description, action }: PageHeaderProps) {
  return (
    <header className="flex flex-col gap-5 border-b border-line/70 pb-6 sm:flex-row sm:items-end sm:justify-between">
      <div className="max-w-2xl">
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-accent2">{eyebrow}</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-normal text-text sm:text-4xl">{title}</h1>
        <p className="mt-3 text-sm leading-6 text-muted">{description}</p>
      </div>
      {action ? (
        <button
          type="button"
          className="inline-flex h-10 items-center justify-center rounded-md bg-text px-4 text-sm font-medium text-canvas transition hover:bg-text/90 focus-visible:outline-none focus-visible:shadow-focus"
        >
          {action}
        </button>
      ) : null}
    </header>
  );
}
