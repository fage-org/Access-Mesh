import { effectScope } from "vue";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { defer } from "@/test-support/async";
import type { FileResp } from "@/api/file";
const mocks = vi.hoisted(() => ({
  pageFiles: vi.fn(),
  getFile: vi.fn(),
  downloadFile: vi.fn(),
  uploadFile: vi.fn(),
  deleteFile: vi.fn(),
  message: vi.fn(),
  session: { id: "session-a" },
  permissions: new Set<string>()
}));
vi.mock("@/api/file", () => mocks);
vi.mock("@/utils/auth", () => ({
  getTenantSession: () => ({ sessionId: mocks.session.id }),
  hasPerms: (code: string) => mocks.permissions.has(code)
}));
vi.mock("@/utils/message", () => ({ message: mocks.message }));
import { useFileManager } from "./useFileManager";
const file = {
  id: 1,
  originalName: "readme.txt",
  bizType: "document"
} as FileResp;
const click = vi.fn();
const create = vi.fn(() => "blob:file");
const revoke = vi.fn();
beforeEach(() => {
  vi.clearAllMocks();
  mocks.session.id = "session-a";
  mocks.permissions.clear();
  ["CREATE", "VIEW", "DELETE"].forEach(op =>
    mocks.permissions.add(`ADMIN_FILE:${op}`)
  );
  mocks.pageFiles.mockResolvedValue({ items: [], total: 0 });
  vi.stubGlobal("document", {
    createElement: () => ({ click, remove: vi.fn() }),
    body: { appendChild: vi.fn() }
  });
  vi.stubGlobal("URL", { createObjectURL: create, revokeObjectURL: revoke });
});
afterEach(() => vi.unstubAllGlobals());
function setup() {
  const scope = effectScope();
  const hook = scope.run(useFileManager)!;
  return { scope, hook };
}
it.each(["filter", "session", "unmount"])(
  "discards late downloads after %s changes",
  async change => {
    const pending = defer<Blob>();
    mocks.downloadFile.mockReturnValue(pending.promise);
    const { hook, scope } = setup();
    const running = hook.download(file);
    if (change === "filter") hook.category.value = "image";
    if (change === "session") mocks.session.id = "session-b";
    if (change === "unmount") scope.stop();
    pending.resolve(new Blob(["data"]));
    await running;
    expect(click).not.toHaveBeenCalled();
    expect(create).not.toHaveBeenCalled();
    expect(mocks.message).not.toHaveBeenCalled();
    scope.stop();
  }
);
it("downloads and releases its Object URL", async () => {
  mocks.downloadFile.mockResolvedValue(new Blob(["data"]));
  const { hook, scope } = setup();
  await hook.download(file);
  expect(click).toHaveBeenCalledOnce();
  await vi.waitFor(() => expect(revoke).toHaveBeenCalledWith("blob:file"));
  scope.stop();
});
it("does not load or inspect files without VIEW, but still supports CREATE", async () => {
  mocks.permissions.delete("ADMIN_FILE:VIEW");
  mocks.uploadFile.mockResolvedValue(1);
  const { hook, scope } = setup();
  await hook.refresh();
  await hook.showDetail(file);
  expect(await hook.upload(new File(["data"], "readme.txt"), "document")).toBe(
    true
  );
  expect(mocks.pageFiles).not.toHaveBeenCalled();
  expect(mocks.getFile).not.toHaveBeenCalled();
  expect(mocks.uploadFile).toHaveBeenCalledOnce();
  scope.stop();
});
it("does not let an old filter response clear the newer result", async () => {
  const old = defer<{ items: FileResp[]; total: number }>();
  mocks.pageFiles
    .mockReturnValueOnce(old.promise)
    .mockResolvedValueOnce({ items: [file], total: 1 });
  const { hook, scope } = setup();
  const running = hook.refresh();
  hook.category.value = "document";
  await vi.waitFor(() => expect(hook.tableData.value).toEqual([file]));
  old.resolve({ items: [], total: 0 });
  await running;
  expect(hook.tableData.value).toEqual([file]);
  expect(hook.pagination.total).toBe(1);
  scope.stop();
});
it("cancellation reports unknown upload outcome and never auto-retries", async () => {
  mocks.uploadFile.mockImplementation(
    (_file, _category, signal: AbortSignal) =>
      new Promise((_resolve, reject) =>
        signal.addEventListener("abort", () => reject(new Error("canceled")))
      )
  );
  const { hook, scope } = setup();
  const running = hook.upload(new File(["data"], "readme.txt"), "document");
  hook.cancelUpload();
  expect(await running).toBe(false);
  expect(mocks.uploadFile).toHaveBeenCalledOnce();
  expect(mocks.message).toHaveBeenCalledWith(
    expect.stringContaining("结果尚未确认"),
    { type: "error" }
  );
  scope.stop();
});
it.each([
  [403, "无文件上传权限"],
  [413, "文件超出请求上限"]
])(
  "shows a definite HTTP %i rejection without claiming an unknown outcome",
  async (status, reason) => {
    mocks.uploadFile.mockRejectedValue({
      response: { status, data: { message: reason } }
    });
    const { hook, scope } = setup();
    expect(
      await hook.upload(new File(["data"], "readme.txt"), "document")
    ).toBe(false);
    expect(mocks.message).toHaveBeenCalledWith(reason, { type: "error" });
    expect(mocks.uploadFile).toHaveBeenCalledOnce();
    scope.stop();
  }
);
it("a failed delete retains rows; context changes while confirming prevent sending", async () => {
  mocks.pageFiles.mockResolvedValue({ items: [file], total: 1 });
  mocks.deleteFile.mockRejectedValue(new Error("无删除权限"));
  const { hook, scope } = setup();
  await hook.refresh();
  await hook.remove(file, async () => true);
  expect(hook.tableData.value).toEqual([file]);
  expect(mocks.message).toHaveBeenCalledWith("无删除权限", { type: "error" });
  mocks.deleteFile.mockClear();
  const confirm = defer<boolean>();
  const running = hook.remove(file, () => confirm.promise);
  mocks.session.id = "session-b";
  confirm.resolve(true);
  await running;
  expect(mocks.deleteFile).not.toHaveBeenCalled();
  scope.stop();
});
