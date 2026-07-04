export type NavItem = {
  label: string;
  path: string;
  marker: string;
};

export const navItems: NavItem[] = [
  { label: "Summary", path: "/summary", marker: "S" },
  { label: "Applications", path: "/applications", marker: "A" },
  { label: "Prospects", path: "/prospects", marker: "P" },
  { label: "Contacts", path: "/contacts", marker: "C" },
  { label: "Outreach", path: "/outreach", marker: "O" },
  { label: "Feed", path: "/feed", marker: "F" },
  { label: "Favorites", path: "/favorites", marker: "V" }
];
