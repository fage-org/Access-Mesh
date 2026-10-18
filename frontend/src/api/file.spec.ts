import { beforeEach, expect, it, vi } from "vitest";
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/utils/http", () => ({ http: { request } }));
import { downloadFile, uploadFile } from "./file";
beforeEach(() => {
  request.mockReset();
});
it("rejects a JSON business error without attachment headers", async () => {
  const body = new Blob(
    [JSON.stringify({ code: 403, message: "无文件读取权限", data: null })],
    { type: "application/json" }
  );
  request.mockResolvedValue(body);
  await expect(downloadFile(1)).rejects.toThrow("无文件读取权限");
});
it("downloads JSON attachment bytes even when the contents resemble an error", async () => {
  const body = new Blob(['{"code":403,"message":"file contents"}'], {
    type: "application/json"
  });
  request.mockImplementation(async (_method, _path, config) => {
    config.beforeResponseCallback?.({
      data: body,
      headers: { "content-disposition": 'attachment; filename="data.txt"' }
    });
    return body;
  });
  expect(await downloadFile(1)).toBe(body);
});
it.each(["decoded", "malformed"])(
  "preserves the HTTP client's %s transport error",
  async kind => {
    const data =
      kind === "decoded"
        ? { code: 403, message: "无文件读取权限" }
        : new Blob(['{"broken"'], { type: "application/json" });
    const failure = { response: { status: 403, data } };
    request.mockRejectedValue(failure);
    await expect(downloadFile(1)).rejects.toBe(failure);
  }
);
it("keeps real binary content", async () => {
  const body = new Blob(["hello"], { type: "text/plain" });
  request.mockResolvedValue(body);
  expect(await downloadFile(1)).toBe(body);
});
it("uploads one multipart file with a category, a timeout and cancellation", async () => {
  request.mockResolvedValue({ code: 200, data: 1 });
  const signal = new AbortController().signal;
  const file = new File(["hello"], "readme.txt", { type: "text/plain" });
  expect(await uploadFile(file, "document", signal)).toBe(1);
  const config = request.mock.calls[0][2];
  expect(config.data.get("file").name).toBe("readme.txt");
  expect(config.data.get("bizType")).toBe("document");
  expect(config.headers["Content-Type"]).toBeUndefined();
  expect(config.timeout).toBeGreaterThan(10000);
  expect(config.signal).toBe(signal);
});
