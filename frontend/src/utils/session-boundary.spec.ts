import { describe, expect, it, vi } from "vitest";
import { createSessionBoundary, SessionChangedError } from "./session-boundary";
describe("independent realm boundaries", () => {
  it("invalidates only the changed realm on shared browser events", () => {
    const win = new EventTarget();
    const doc = new EventTarget();
    let tenantToken = "tenant-A";
    let platformToken = "operator-A";
    const tenant = createSessionBoundary();
    const platform = createSessionBoundary();
    const tenantClear = vi.fn();
    const platformClear = vi.fn();
    const stopTenant = tenant.start({
      read: () => ({ token: tenantToken, expires: Date.now() + 60000 }),
      invalidate: tenantClear,
      storageKey: "tenant",
      window: win as Window,
      document: doc as Document
    });
    const stopPlatform = platform.start({
      read: () => ({ token: platformToken, expires: Date.now() + 60000 }),
      invalidate: platformClear,
      storageKey: "platform",
      window: win as Window,
      document: doc as Document
    });
    try {
      platformToken = "operator-B";
      const event = new Event("storage");
      Object.assign(event, { key: "platform" });
      win.dispatchEvent(event);
      expect(platform.blocked.value).toBe(true);
      expect(tenant.blocked.value).toBe(false);
      expect(platformClear).toHaveBeenCalledTimes(1);
      expect(tenantClear).not.toHaveBeenCalled();
      expect(() => tenant.assertRequest(tenant.captureRequest())).not.toThrow();
      tenantToken = "tenant-B";
      win.dispatchEvent(new Event("focus"));
      expect(tenant.blocked.value).toBe(true);
      expect(tenantClear).toHaveBeenCalledTimes(1);
      expect(platformClear).toHaveBeenCalledTimes(1);
    } finally {
      stopTenant();
      stopPlatform();
    }
  });
  it("an old cleanup handle cannot disable a restarted boundary", () => {
    const boundary = createSessionBoundary();
    let token = "A";
    const options = {
      read: () => ({ token, expires: null }),
      invalidate: vi.fn(),
      storageKey: "platform",
      window: new EventTarget() as Window,
      document: new EventTarget() as Document
    };
    const oldStop = boundary.start(options);
    const currentStop = boundary.start(options);
    oldStop();
    token = "B";
    expect(() => boundary.captureRequest()).toThrow(SessionChangedError);
    currentStop();
  });
});
