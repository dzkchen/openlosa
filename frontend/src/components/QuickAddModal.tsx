import { type FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createProspect } from "../api/prospects";
import { createTag, listTags, tagsQueryKey } from "../api/tags";
import type { Tag } from "../api/applications";

const validationId = "quick-add-error";

type QuickAddModalProps = {
  open: boolean;
  onClose: () => void;
};

function parseTagNames(value: string) {
  const seen = new Set<string>();
  return value
    .split(/[,\n]/)
    .map((tag) => tag.trim())
    .filter((tag) => {
      if (!tag) {
        return false;
      }
      const key = tag.toLowerCase();
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
}

function findTagByName(tags: Tag[], name: string) {
  const key = name.toLowerCase();
  return tags.find((tag) => tag.name.toLowerCase() === key);
}

async function resolveTagIds(names: string[]) {
  if (names.length === 0) {
    return [];
  }

  let availableTags = await listTags({ sort: "name", dir: "asc" });
  const tagIds: number[] = [];

  for (const name of names) {
    const existing = findTagByName(availableTags, name);
    if (existing) {
      tagIds.push(existing.id);
      continue;
    }

    try {
      const created = await createTag({ name });
      availableTags = [...availableTags, created];
      tagIds.push(created.id);
    } catch (error) {
      const refreshedTags = await listTags({ q: name, sort: "name", dir: "asc" });
      const refreshed = findTagByName(refreshedTags, name);
      if (refreshed) {
        availableTags = [...availableTags, refreshed];
        tagIds.push(refreshed.id);
        continue;
      }
      throw error;
    }
  }

  return tagIds;
}

function isMissingTagError(error: unknown) {
  return error instanceof Error && /^Tag \d+ was not found$/.test(error.message);
}

function getFocusableElements(root: HTMLElement | null) {
  if (!root) {
    return [];
  }

  return Array.from(
    root.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )
  ).filter((element) => !element.hasAttribute("disabled") && element.getAttribute("aria-hidden") !== "true");
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Something went wrong.";
}

export default function QuickAddModal({ open, onClose }: QuickAddModalProps) {
  const queryClient = useQueryClient();
  const dialogRef = useRef<HTMLFormElement>(null);
  const nameInputRef = useRef<HTMLInputElement>(null);
  const previousActiveElementRef = useRef<Element | null>(null);
  const isSavingRef = useRef(false);
  const [name, setName] = useState("");
  const [url, setUrl] = useState("");
  const [note, setNote] = useState("");
  const [tagText, setTagText] = useState("");
  const [validation, setValidation] = useState<string | null>(null);

  const tagsQuery = useQuery({
    queryKey: tagsQueryKey({ sort: "name", dir: "asc" }),
    queryFn: () => listTags({ sort: "name", dir: "asc" }),
    enabled: open
  });
  const tags = useMemo(() => tagsQuery.data ?? [], [tagsQuery.data]);

  const createMutation = useMutation({
    mutationFn: async () => {
      const cleanName = name.trim();
      if (!cleanName) {
        throw new Error("Name is required.");
      }

      const tagNames = parseTagNames(tagText);
      const tagIds = await resolveTagIds(tagNames);
      const input = {
        name: cleanName,
        url: url.trim() || undefined,
        note: note.trim() || undefined,
        tagIds
      };

      try {
        return await createProspect(input);
      } catch (error) {
        if (tagNames.length === 0 || !isMissingTagError(error)) {
          throw error;
        }

        return createProspect({
          ...input,
          tagIds: await resolveTagIds(tagNames)
        });
      }
    },
    onMutate: () => setValidation(null),
    onError: (error) => setValidation(errorMessage(error)),
    onSuccess: () => {
      setName("");
      setUrl("");
      setNote("");
      setTagText("");
      setValidation(null);
      void queryClient.invalidateQueries({ queryKey: ["prospects"] });
      onClose();
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ["tags"] });
    }
  });
  const isSaving = createMutation.isPending;
  useEffect(() => {
    isSavingRef.current = isSaving;
  }, [isSaving]);

  const requestClose = useCallback(() => {
    if (!isSavingRef.current) {
      onClose();
    }
  }, [onClose]);

  useEffect(() => {
    if (!open) {
      return;
    }

    setValidation(null);
    previousActiveElementRef.current = document.activeElement;
    window.setTimeout(() => nameInputRef.current?.focus(), 0);

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        requestClose();
        return;
      }

      if (event.key !== "Tab") {
        return;
      }

      const focusable = getFocusableElements(dialogRef.current);
      if (focusable.length === 0) {
        event.preventDefault();
        dialogRef.current?.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      if (previousActiveElementRef.current instanceof HTMLElement) {
        previousActiveElementRef.current.focus();
      }
    };
  }, [open, requestClose]);

  if (!open) {
    return null;
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (isSaving) {
      return;
    }
    if (!name.trim()) {
      setValidation("Name is required.");
      nameInputRef.current?.focus();
      return;
    }
    createMutation.mutate();
  }

  const hasValidation = Boolean(validation);
  const nameIsInvalid = hasValidation && !name.trim();

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-start justify-center bg-canvas/80 px-4 py-20 backdrop-blur-sm sm:py-24"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          requestClose();
        }
      }}
    >
      <form
        ref={dialogRef}
        onSubmit={handleSubmit}
        role="dialog"
        aria-modal="true"
        aria-labelledby="quick-add-title"
        tabIndex={-1}
        className="relative grid w-full max-w-2xl gap-4 rounded-lg border border-line/80 bg-surface p-5 shadow-2xl shadow-black/40"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="quick-add-title" className="text-lg font-semibold text-text">
              Quick add prospect
            </h2>
          </div>
          <button
            type="button"
            onClick={requestClose}
            disabled={isSaving}
            className="h-8 w-8 rounded-md border border-line/70 text-sm text-muted transition hover:bg-elevated hover:text-text disabled:cursor-wait disabled:opacity-60"
            aria-label="Close"
          >
            x
          </button>
        </div>

        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Name
          <input
            ref={nameInputRef}
            value={name}
            disabled={isSaving}
            aria-invalid={nameIsInvalid}
            aria-describedby={hasValidation ? validationId : undefined}
            onChange={(event) => setName(event.target.value)}
            className="h-11 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
            placeholder="Company or role lead"
          />
        </label>

        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          URL
          <input
            value={url}
            disabled={isSaving}
            aria-describedby={hasValidation ? validationId : undefined}
            onChange={(event) => setUrl(event.target.value)}
            className="h-11 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
            placeholder="https://..."
          />
        </label>

        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Note
          <textarea
            value={note}
            disabled={isSaving}
            aria-describedby={hasValidation ? validationId : undefined}
            onChange={(event) => setNote(event.target.value)}
            rows={3}
            className="resize-none rounded-md border border-line/80 bg-elevated/60 px-3 py-2 text-sm font-normal normal-case tracking-normal text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
            placeholder="Why this belongs on the list"
          />
        </label>

        <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.12em] text-muted">
          Tags
          <input
            value={tagText}
            disabled={isSaving}
            aria-describedby={hasValidation ? validationId : undefined}
            onChange={(event) => setTagText(event.target.value)}
            className="h-11 rounded-md border border-line/80 bg-elevated/60 px-3 text-sm font-normal normal-case tracking-normal text-text outline-none placeholder:text-soft focus:border-accent/70 focus:shadow-focus"
            placeholder={tags.length ? tags.slice(0, 3).map((tag) => tag.name).join(", ") : "Internship, Toronto"}
          />
        </label>

        {validation ? (
          <div id={validationId} role="alert" className="rounded-md border border-warn/30 bg-warn/10 px-3 py-2 text-sm text-warn">
            {validation}
          </div>
        ) : null}

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={requestClose}
            disabled={isSaving}
            className="h-10 rounded-md border border-line/80 px-3 text-sm font-medium text-muted transition hover:bg-elevated/70 hover:text-text disabled:cursor-wait disabled:text-soft"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSaving}
            className="h-10 rounded-md bg-text px-3 text-sm font-medium text-canvas transition hover:bg-text/90 disabled:cursor-wait disabled:bg-soft disabled:text-text/60"
          >
            {isSaving ? "Saving" : "Add prospect"}
          </button>
        </div>
      </form>
    </div>,
    document.body
  );
}
