import { computed, onScopeDispose, ref, watch } from "vue";
import {
  deleteFile,
  downloadFile,
  getFile,
  pageFiles,
  uploadFile,
  type FileResp
} from "@/api/file";
import { RequestError, toErrorMessage } from "@/api/_envelope";
import { PERMISSION_CODE } from "@/constants/access";
import { getTenantSession, hasPerms } from "@/utils/auth";
import { usePagedList } from "@/utils/list-load";
import { message } from "@/utils/message";

/** 文件页生命周期；沿用公共分页与会话边界，不维护另一份授权范围。 */
export function useFileManager() {
  const category = ref("");
  const canView = computed(() => hasPerms(PERMISSION_CODE.FILE_VIEW));
  const canCreate = computed(() => hasPerms(PERMISSION_CODE.FILE_CREATE));
  const canDelete = computed(() => hasPerms(PERMISSION_CODE.FILE_DELETE));
  const detail = ref<FileResp | null>(null);
  const uploading = ref(false);
  const busy = ref(false);
  let disposed = false;
  let generation = 0;
  let uploadController: AbortController | undefined;
  let downloadController: AbortController | undefined;
  const stamp = () => ({ generation, session: getTenantSession()?.sessionId });
  const sameSession = (value: ReturnType<typeof stamp>) =>
    !disposed &&
    !!value.session &&
    value.session === getTenantSession()?.sessionId;
  const current = (value: ReturnType<typeof stamp>) =>
    value.generation === generation && sameSession(value);

  const list = usePagedList<FileResp>({
    errorText: "文件列表加载失败",
    contextKey: () => category.value,
    fetcher: async (page, size) => {
      const context = stamp();
      if (!canView.value || !context.session) return { items: [], total: 0 };
      try {
        return await pageFiles(page, size, category.value || undefined);
      } finally {
        // HTTP 会拒绝跨会话响应；同时作废分页代际，避免旧错误提示或数据残留。
        if (context.generation === generation && !current(context))
          list.clear();
      }
    }
  });
  function invalidate() {
    generation++;
    list.clear();
    detail.value = null;
    downloadController?.abort();
    uploadController?.abort();
  }
  watch(
    [category, canView],
    () => {
      invalidate();
      if (canView.value) void list.onSearch();
    },
    { flush: "sync" }
  );
  onScopeDispose(() => {
    disposed = true;
    invalidate();
  });

  async function refresh() {
    if (canView.value && !disposed) return list.loadTable();
    list.clear();
    return false;
  }
  async function showDetail(file: FileResp) {
    if (!canView.value || busy.value) return;
    const context = stamp();
    busy.value = true;
    try {
      const result = await getFile(file.id);
      if (current(context)) detail.value = result;
    } catch (error) {
      if (current(context))
        message(toErrorMessage(error, "文件信息加载失败"), { type: "error" });
    } finally {
      busy.value = false;
    }
  }
  async function download(file: FileResp) {
    if (!canView.value || busy.value) return;
    const context = stamp();
    busy.value = true;
    downloadController = new AbortController();
    try {
      const blob = await downloadFile(file.id, downloadController.signal);
      if (!current(context)) return;
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      try {
        link.href = url;
        link.download = file.originalName;
        link.hidden = true;
        document.body.appendChild(link);
        link.click();
      } finally {
        link.remove();
        // 下载导航在 click 后排队；让出当前任务后再释放，避免浏览器读取失效 URL。
        setTimeout(() => URL.revokeObjectURL(url), 0);
      }
    } catch (error) {
      if (current(context))
        message(toErrorMessage(error, "文件下载失败"), { type: "error" });
    } finally {
      busy.value = false;
    }
  }
  async function upload(file: File, bizType: string): Promise<boolean> {
    if (!canCreate.value || uploading.value) return false;
    const context = stamp();
    uploading.value = true;
    uploadController = new AbortController();
    try {
      await uploadFile(file, bizType, uploadController.signal);
      if (!current(context)) return false;
      message("文件已上传", { type: "success" });
      await refresh();
      return current(context);
    } catch (error) {
      if (current(context)) {
        const rejected =
          (error instanceof RequestError && error.kind === "business") ||
          [400, 401, 403, 409, 413, 415, 422].includes(
            (error as { response?: { status?: number } })?.response?.status ?? 0
          );
        message(
          rejected
            ? toErrorMessage(error, "上传失败")
            : "上传结果尚未确认，请刷新列表检查后再决定是否重试",
          { type: "error" }
        );
      }
      return false;
    } finally {
      uploading.value = false;
    }
  }
  async function remove(file: FileResp, confirm: () => Promise<unknown>) {
    if (!canDelete.value || busy.value) return;
    const context = stamp();
    busy.value = true;
    try {
      try {
        await confirm();
      } catch {
        return;
      }
      if (!current(context) || !canDelete.value) return;
      await deleteFile(file.id);
      if (!sameSession(context)) return;
      if (current(context)) message("文件已删除", { type: "success" });
      // 删除是已提交的事实；同会话下重新读取当前筛选，不回填旧筛选数据。
      await refresh();
    } catch (error) {
      if (current(context))
        message(toErrorMessage(error, "删除结果未确认，请刷新列表检查"), {
          type: "error"
        });
    } finally {
      busy.value = false;
    }
  }
  return {
    ...list,
    category,
    canView,
    canCreate,
    canDelete,
    detail,
    busy,
    uploading,
    refresh,
    showDetail,
    download,
    upload,
    remove,
    cancelUpload: () => uploadController?.abort()
  };
}
