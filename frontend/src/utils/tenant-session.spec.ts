import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import {
  startTenantSessionSync,
  captureTenantRequest,
  assertTenantRequest,
  assertTenantSessionCurrent,
  acceptLocalTenantSession,
  tenantSessionBlocked,
  TenantSessionChangedError
} from "./tenant-session";
let session: { token: string | null; expires: number | null };
let win: EventTarget;
let doc: EventTarget;
let stop: () => void;
const invalidate = vi.fn();
beforeEach(() => {
  session = { token: "A", expires: Date.now() + 60000 };
  win = new EventTarget();
  doc = new EventTarget();
  invalidate.mockReset();
  stop = startTenantSessionSync({
    read: () => session,
    invalidate,
    storageKey: "tenant-user-info-v2",
    window: win as Window,
    document: doc as Document
  });
});
afterEach(() => stop());
function storage(key: string | null) {
  const event = new Event("storage");
  Object.assign(event, { key });
  win.dispatchEvent(event);
}
describe("cross-tab tenant session boundary", () => {
  it("rejects an old page before the storage event arrives and never changes the shared new token", () => {
    session.token = "B";
    expect(() => captureTenantRequest()).toThrow(TenantSessionChangedError);
    expect(tenantSessionBlocked.value).toBe(true);
    expect(session.token).toBe("B");
    expect(invalidate).toHaveBeenCalledTimes(1);
  });
  it.each(["storage", "focus", "pageshow", "visibilitychange"])(
    "clears old context after %s even without a request",
    event => {
      session = { token: null, expires: null };
      if (event === "storage") storage("tenant-user-info-v2");
      else
        (event === "visibilitychange" ? doc : win).dispatchEvent(
          new Event(event)
        );
      expect(tenantSessionBlocked.value).toBe(true);
      expect(invalidate).toHaveBeenCalledTimes(1);
    }
  );
  it("ignores metadata-only and platform storage changes", () => {
    storage("tenant-user-info-v2");
    storage("platform-operator-session");
    expect(tenantSessionBlocked.value).toBe(false);
    expect(invalidate).not.toHaveBeenCalled();
  });
  it("allows local login/logout but rejects their previous in-flight response", () => {
    const old = captureTenantRequest();
    session.token = "B";
    acceptLocalTenantSession("B");
    expect(() => assertTenantRequest(old)).toThrow(TenantSessionChangedError);
    expect(() => assertTenantRequest(captureTenantRequest())).not.toThrow();
    expect(tenantSessionBlocked.value).toBe(false);
    session = { token: null, expires: null };
    acceptLocalTenantSession(null);
    expect(() => assertTenantSessionCurrent()).not.toThrow();
  });
  it("never automatically unblocks a page even if the original token reappears", () => {
    session.token = "B";
    storage("tenant-user-info-v2");
    session.token = "A";
    expect(() => captureTenantRequest()).toThrow(TenantSessionChangedError);
    expect(invalidate).toHaveBeenCalledTimes(1);
  });
  it("preserves the existing local expiry flow when cookie expiry leaves expired metadata", () => {
    session = { token: null, expires: Date.now() - 1 };
    win.dispatchEvent(new Event("focus"));
    expect(tenantSessionBlocked.value).toBe(false);
  });
});

describe("confirmed session reload", () => {
  it.each([
    ["http://localhost/#/role", "/#/", true],
    ["http://localhost/#/role", "/#/login", true],
    ["http://localhost/base/role", "/base/", false]
  ])(
    "navigates %s to %s with a real reload when only the hash changes",
    async (href, destination, needsReload) => {
      const { reloadTenantSession } = await import("./tenant-session");
      const location = { href, replace: vi.fn(), reload: vi.fn() };
      reloadTenantSession(location, destination);
      expect(location.replace).toHaveBeenCalledWith(
        new URL(destination, href).href
      );
      expect(location.reload).toHaveBeenCalledTimes(needsReload ? 1 : 0);
    }
  );
});
