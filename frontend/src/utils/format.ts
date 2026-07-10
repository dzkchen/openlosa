const DATE_FORMAT = new Intl.DateTimeFormat(undefined, { dateStyle: "medium" });

export function formatDate(value: string | null | undefined, fallback = "Not set") {
  if (!value) {
    return fallback;
  }
  return DATE_FORMAT.format(new Date(`${value.slice(0, 10)}T00:00:00`));
}
