import { useEffect, useState } from "react";
import { NavLink } from "react-router-dom";
import { navItems } from "./navItems";

type ApiStatus = "checking" | "online" | "offline";

const apiStatusStyles: Record<ApiStatus, { label: string; text: string; dot: string }> = {
  checking: {
    label: "Checking",
    text: "text-muted",
    dot: "bg-soft"
  },
  online: {
    label: "Online",
    text: "text-good",
    dot: "bg-good"
  },
  offline: {
    label: "Offline",
    text: "text-warn",
    dot: "bg-warn"
  }
};

export default function Sidebar() {
  const [apiStatus, setApiStatus] = useState<ApiStatus>("checking");
  const status = apiStatusStyles[apiStatus];

  useEffect(() => {
    const controller = new AbortController();

    async function checkApiHealth() {
      try {
        const response = await fetch("/api/v1/health", {
          signal: controller.signal,
          headers: { Accept: "application/json" }
        });

        if (!response.ok) {
          setApiStatus("offline");
          return;
        }

        const body = (await response.json()) as { status?: string };
        setApiStatus(body.status === "OK" ? "online" : "offline");
      } catch (error) {
        if (!controller.signal.aborted) {
          setApiStatus("offline");
        }
      }
    }

    void checkApiHealth();

    return () => controller.abort();
  }, []);

  return (
    <aside className="border-b border-line/70 bg-surface/80 px-4 py-4 backdrop-blur lg:sticky lg:top-0 lg:h-screen lg:border-b-0 lg:px-3">
      <div className="flex h-full flex-col gap-6">
        <div className="flex items-center gap-3 px-2">
          <div className="grid h-9 w-9 place-items-center rounded-md bg-text text-sm font-semibold text-canvas">
            OL
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold tracking-wide">OpenLOSA</p>
            <p className="truncate text-xs text-muted">Local application workspace</p>
          </div>
        </div>

        <nav aria-label="Primary navigation" className="flex gap-1 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible">
          {navItems.map((item) => {
            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  [
                    "group relative flex min-h-10 shrink-0 items-center gap-3 rounded-md px-3 text-sm transition",
                    "focus-visible:outline-none focus-visible:shadow-focus",
                    isActive
                      ? "bg-elevated text-text"
                      : "text-muted hover:bg-elevated/55 hover:text-text"
                  ].join(" ")
                }
              >
                {({ isActive }) => (
                  <>
                    <span
                      className={[
                        "absolute left-0 hidden h-5 w-0.5 rounded-full lg:block",
                        isActive ? "bg-gradient-to-b from-accent to-accent2" : "bg-transparent"
                      ].join(" ")}
                    />
                    <span
                      aria-hidden
                      className={[
                        "grid h-5 w-5 place-items-center rounded text-[11px] font-semibold",
                        isActive ? "bg-accent/18 text-text" : "bg-elevated/70 text-soft group-hover:text-muted"
                      ].join(" ")}
                    >
                      {item.marker}
                    </span>
                    <span>{item.label}</span>
                  </>
                )}
              </NavLink>
            );
          })}
        </nav>

        <div className="mt-auto hidden rounded-md border border-line/70 bg-canvas/55 p-3 lg:block">
          <div className="flex items-center justify-between gap-3 text-xs text-muted">
            <span>API</span>
            <span className={`inline-flex items-center gap-1.5 ${status.text}`} aria-live="polite">
              <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
              {status.label}
            </span>
          </div>
        </div>
      </div>
    </aside>
  );
}
