import { ref, onScopeDispose } from "vue";
import { exportAuditLogs, type AuditExportKind } from "@/api/audit-export";
import { getToken } from "@/utils/auth";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";
export function useAuditExport(kind: AuditExportKind, filters: () => object) {
  const exporting = ref(false);
  let disposed = false;
  let controller: AbortController | undefined;
  onScopeDispose(() => {
    disposed = true;
    controller?.abort();
  });
  async function download() {
    if (exporting.value || disposed) return;
    const token = getToken()?.accessToken;
    if (!token) return;
    exporting.value = true;
    controller = new AbortController();
    try {
      const blob = await exportAuditLogs(kind, filters(), controller.signal);
      if (disposed || token !== getToken()?.accessToken) return;
      const url = URL.createObjectURL(blob);
      try {
        const link = document.createElement("a");
        link.href = url;
        link.download = `${kind}-log.csv`;
        link.click();
      } finally {
        URL.revokeObjectURL(url);
      }
    } catch (error) {
      if (!disposed && token === getToken()?.accessToken)
        message(toErrorMessage(error, "日志导出失败"), { type: "error" });
    } finally {
      exporting.value = false;
    }
  }
  return { exporting, download };
}
