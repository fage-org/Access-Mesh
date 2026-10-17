import { effectScope } from "vue";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { defer } from "@/test-support/async";
const { api, token, message } = vi.hoisted(() => ({
  api: vi.fn(),
  token: { value: "session-a" },
  message: vi.fn()
}));
vi.mock("@/api/audit-export", () => ({ exportAuditLogs: api }));
vi.mock("@/utils/auth", () => ({
  getToken: () => ({ accessToken: token.value })
}));
vi.mock("@/utils/message", () => ({ message }));
import { useAuditExport } from "./audit-export";
const click = vi.fn();
const create = vi.fn(() => "blob:test");
const revoke = vi.fn();
beforeEach(() => {
  vi.clearAllMocks();
  token.value = "session-a";
  vi.stubGlobal("document", { createElement: () => ({ click }) });
  vi.stubGlobal("URL", { createObjectURL: create, revokeObjectURL: revoke });
});
afterEach(() => vi.unstubAllGlobals());
it.each(["session", "unmount"])(
  "discards late export after %s changes",
  async change => {
    const pending = defer<Blob>();
    api.mockReturnValue(pending.promise);
    const scope = effectScope();
    const hook = scope.run(() =>
      useAuditExport("operation", () => ({ action: "EDIT" }))
    )!;
    const running = hook.download();
    if (change === "session") token.value = "session-b";
    else scope.stop();
    pending.resolve(new Blob(["csv"], { type: "text/csv" }));
    await running;
    expect(create).not.toHaveBeenCalled();
    expect(click).not.toHaveBeenCalled();
    expect(message).not.toHaveBeenCalled();
    scope.stop();
  }
);
it("downloads once and releases the object URL", async () => {
  api.mockResolvedValue(new Blob(["csv"], { type: "text/csv" }));
  const scope = effectScope();
  const hook = scope.run(() => useAuditExport("login", () => ({})))!;
  await hook.download();
  expect(click).toHaveBeenCalledTimes(1);
  expect(revoke).toHaveBeenCalledWith("blob:test");
  expect(hook.exporting.value).toBe(false);
  scope.stop();
});
