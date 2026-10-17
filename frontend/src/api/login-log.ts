import { http } from "@/utils/http";
import { unwrap, type R } from "./_envelope";
import type { PageResp } from "./role-manage";

export type LoginLog = {
  id: number;
  username: string;
  clientId: string | null;
  loginType: string;
  status: number;
  ipAddress: string | null;
  userAgent: string | null;
  failReason: string | null;
  loginAt: string;
};
export const pageLoginLogs = async (
  pageNum: number,
  pageSize: number,
  filters: { since?: string; until?: string } = {}
) =>
  unwrap(
    await http.request<R<PageResp<LoginLog>>>(
      "post",
      "/api/access/login-log/page",
      { data: { ...filters, pageNum, pageSize } }
    )
  );
