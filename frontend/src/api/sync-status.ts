import { http } from "@/utils/http";
import { unwrap, type R } from "./_envelope";
import type { PageResp } from "./role-manage";

export type SyncStatus = {
  entityKind: string;
  sourceService: string;
  scopeKey: string;
  trackedItems: number;
  maxGeneration: string | null;
  lastFullGeneration: string | null;
  lastFullStatus: string | null;
  updatedAt: string;
};
export type SyncStatusQuery = {
  sourceService?: string;
  pageNum?: number;
  pageSize?: number;
};
export const listSyncStatus = async (data: SyncStatusQuery) =>
  unwrap(
    await http.request<R<PageResp<SyncStatus>>>(
      "post",
      "/api/access/sync-status/list",
      { data }
    )
  );
