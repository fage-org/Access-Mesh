import { beforeEach, expect, it, vi } from "vitest";
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/utils/http", () => ({ http: { request } }));
import { downloadFile, uploadFile } from "./file";
beforeEach(() => {
  request.mockReset();
});
it.each([200, 403])(
  "rejects JSON error blobs at HTTP %i instead of downloading them",
  async status => {
    const body = new Blob(
      [JSON.stringify({ code: 403, message: "无文件读取权限", data: null })],
      { type: "application/json" }
    );
    if (status === 200) request.mockResolvedValue(body);
    else request.mockRejectedValue({ response: { status, data: body } });
    await expect(downloadFile(1)).rejects.toThrow("无文件读取权限");
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
