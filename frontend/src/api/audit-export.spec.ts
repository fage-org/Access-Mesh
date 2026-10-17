import { beforeEach, describe, expect, it, vi } from "vitest";
const { request } = vi.hoisted(() => ({ request: vi.fn() }));
vi.mock("@/utils/http", () => ({ http: { request } }));
import { exportAuditLogs } from "./audit-export";
beforeEach(() => {
  request.mockReset();
});
describe("audit export response boundary", () => {
  it("keeps filters and binary response, without pagination", async () => {
    const csv = new Blob(["id\r\n1"], { type: "text/csv;charset=UTF-8" });
    request.mockResolvedValue(csv);
    const filters = { since: "2026-09-01T00:00:00", action: "EDIT" };
    expect(await exportAuditLogs("operation", filters)).toBe(csv);
    expect(request).toHaveBeenCalledWith(
      "post",
      "/api/access/log/operation/export",
      expect.objectContaining({ data: filters, responseType: "blob" })
    );
  });
  it.each([200, 403])(
    "never downloads a JSON error carried as Blob (HTTP %i)",
    async status => {
      const body = new Blob(
        [
          JSON.stringify({ code: 11201, message: "请缩小筛选范围", data: null })
        ],
        { type: "application/json" }
      );
      if (status === 200) request.mockResolvedValue(body);
      else request.mockRejectedValue({ response: { status, data: body } });
      await expect(exportAuditLogs("login", {})).rejects.toThrow(
        "请缩小筛选范围"
      );
    }
  );
});
