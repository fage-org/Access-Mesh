import { http } from "@/utils/http";
import { unwrap, type R } from "./_envelope";
export type AuditExportKind = "operation" | "change" | "login";
const paths: Record<AuditExportKind, string> = {
  operation: "/api/access/log/operation/export",
  change: "/api/access/log/change/export",
  login: "/api/access/login-log/export"
};
async function rejectEnvelope(blob: Blob): Promise<never> {
  const envelope = JSON.parse(await blob.text()) as R<unknown>;
  unwrap(envelope);
  throw new Error("导出响应格式不正确");
}
export async function exportAuditLogs(
  kind: AuditExportKind,
  data: object,
  signal?: AbortSignal
): Promise<Blob> {
  const blob = await http.request<Blob>("post", paths[kind], {
    data,
    responseType: "blob",
    timeout: 90000,
    signal
  });
  if (blob instanceof Blob && blob.type.includes("json"))
    return rejectEnvelope(blob);
  if (!(blob instanceof Blob) || !blob.type.startsWith("text/csv"))
    throw new Error("导出响应格式不正确");
  return blob;
}
