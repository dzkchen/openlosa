import { type PropsWithChildren, useCallback, useEffect, useState } from "react";
import QuickAddModal from "../QuickAddModal";
import Sidebar from "./Sidebar";

function isEditableShortcutTarget(target: EventTarget | null) {
  if (!(target instanceof HTMLElement)) {
    return false;
  }

  return target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
}

export default function AppShell({ children }: PropsWithChildren) {
  const [quickAddOpen, setQuickAddOpen] = useState(false);
  const openQuickAdd = useCallback(() => setQuickAddOpen(true), []);
  const closeQuickAdd = useCallback(() => setQuickAddOpen(false), []);

  useEffect(() => {
    const isApplePlatform = /Mac|iPhone|iPad|iPod/.test(navigator.platform);

    function handleKeyDown(event: KeyboardEvent) {
      if (event.defaultPrevented || event.isComposing || event.repeat || isEditableShortcutTarget(event.target)) {
        return;
      }
      const primaryKeyPressed = isApplePlatform ? event.metaKey && !event.ctrlKey : event.ctrlKey && !event.metaKey;
      if (primaryKeyPressed && !event.altKey && !event.shiftKey && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setQuickAddOpen(true);
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, []);

  return (
    <div className="min-h-screen bg-canvas/92 text-text">
      <div className="grid min-h-screen grid-cols-1 lg:grid-cols-[17rem_minmax(0,1fr)]">
        <Sidebar onQuickAdd={openQuickAdd} />
        <main className="min-w-0 border-l border-line/70">
          <div className="mx-auto flex min-h-screen w-full max-w-7xl flex-col px-5 py-5 sm:px-8 lg:px-10 lg:py-8">
            {children}
          </div>
        </main>
      </div>
      <QuickAddModal open={quickAddOpen} onClose={closeQuickAdd} />
    </div>
  );
}
