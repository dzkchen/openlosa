import { apiRequest } from "./client";

export type DashboardStats = {
  applicationsLast7Days: number;
  applicationsLast30Days: number;
  applicationsLast60Days: number;
  noUpdate: number;
  offers: number;
  ongoing: number;
};

export type DashboardSankeyLink = {
  from: string;
  to: string;
  count: number;
};

export function dashboardStatsQueryKey() {
  return ["dashboard", "stats"] as const;
}

export function dashboardSankeyQueryKey() {
  return ["dashboard", "sankey"] as const;
}

export async function getDashboardStats() {
  return apiRequest<DashboardStats>("/api/v1/dashboard/stats");
}

export async function getDashboardSankey() {
  return apiRequest<DashboardSankeyLink[]>("/api/v1/dashboard/sankey");
}
