import type { PropsWithChildren } from "react";
import Sidebar from "./Sidebar";

export default function AppShell({ children }: PropsWithChildren) {
  return (
    <div className="min-h-screen bg-canvas/92 text-text">
      <div className="grid min-h-screen grid-cols-1 lg:grid-cols-[17rem_minmax(0,1fr)]">
        <Sidebar />
        <main className="min-w-0 border-l border-line/70">
          <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col px-5 py-5 sm:px-8 lg:px-10 lg:py-8">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
