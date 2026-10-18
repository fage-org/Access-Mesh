import {
  startTenantSessionSync,
  TenantSessionChangedError
} from "./tenant-session";
const { cookieJar } = vi.hoisted(() => ({
  cookieJar: new Map<string, string>()
}));
vi.mock("js-cookie", () => ({
  default: {
    get: (key: string) => cookieJar.get(key),
    set: (key: string, value: string) => cookieJar.set(key, value),
    remove: (key: string) => cookieJar.delete(key)
  }
}));
import { describe, it, expect, beforeEach, vi } from "vitest";

const { memStorage } = vi.hoisted(() => ({
  memStorage: new Map<string, string>()
}));

vi.mock("@/store/modules/user", () => ({
  useUserStoreHook: () => ({
    SET_AVATAR: vi.fn(),
    SET_USERNAME: vi.fn(),
    SET_NICKNAME: vi.fn(),
    SET_ROLES: vi.fn(),
    SET_PERMS: vi.fn()
  })
}));
// auth.ts 模块级还 import isString/isIncludeAllChildren（hasPerms 用，本 spec 不触达），
// 一并给出桩实现防命名导入缺位
vi.mock("@pureadmin/utils", () => ({
  storageLocal: () => ({
    getItem: (key: string) =>
      memStorage.has(key) ? JSON.parse(memStorage.get(key) as string) : null,
    setItem: (key: string, value: unknown) =>
      memStorage.set(key, JSON.stringify(value)),
    removeItem: (key: string) => memStorage.delete(key)
  }),
  isString: (v: unknown) => typeof v === "string",
  isIncludeAllChildren: vi.fn(() => true)
}));

import {
  getTenantSession,
  removeTenantSession,
  getPlatformSession,
  setPlatformSession,
  removePlatformSession,
  userKey,
  clearLegacyAuthentication,
  setTenantSession,
  platformSessionKey
} from "@/utils/auth";
import { readDraftOwner } from "@/views/perm/grant/utils/draft-storage";
beforeEach(() => {
  memStorage.clear();
  cookieJar.clear();
});
const platform = {
  sessionId: "operator-token",
  expires: Date.now() + 60000,
  account: {
    id: 1,
    username: "operator",
    name: "Operator",
    status: 1,
    forceResetPwd: false
  }
};
describe("independent identity storage", () => {
  it("tenant logout preserves the platform session", () => {
    setPlatformSession(platform);
    memStorage.set(
      userKey,
      JSON.stringify({ sessionId: "tenant-token", tenantId: 2, userId: 1 })
    );
    removeTenantSession();
    expect(memStorage.has(userKey)).toBe(false);
    expect(getPlatformSession()).toEqual(platform);
  });
  it("platform logout preserves tenant identity and its drafts", () => {
    memStorage.set(userKey, JSON.stringify({ tenantId: 2, userId: 1 }));
    setPlatformSession(platform);
    removePlatformSession();
    expect(memStorage.has(platformSessionKey)).toBe(false);
    expect(readDraftOwner()).toEqual({ tenantId: "2", userId: "1" });
  });
  it("legacy login and missing tenant drafts are not assigned to tenant 1", () => {
    memStorage.set(
      "user-info",
      JSON.stringify({ sessionId: "legacy", userId: 1 })
    );
    expect(getTenantSession()).toBeNull();
    memStorage.set(userKey, JSON.stringify({ userId: 1 }));
    expect(readDraftOwner()).toBeNull();
  });
});

it("stale tab cannot remove the cookie or metadata belonging to a new tenant session", () => {
  const expires = Date.now() + 60000;
  memStorage.set(userKey, JSON.stringify({ sessionId: "A", expires }));
  const stop = startTenantSessionSync({
    read: () => ({
      token: getTenantSession()?.sessionId ?? null,
      expires: getTenantSession()?.expires ?? null
    }),
    invalidate: vi.fn(),
    storageKey: userKey,
    window: new EventTarget() as Window,
    document: new EventTarget() as Document
  });
  try {
    memStorage.set(userKey, JSON.stringify({ sessionId: "B", expires }));
    memStorage.set(
      userKey,
      JSON.stringify({ sessionId: "B", tenantId: 2, userId: 22, expires })
    );
    expect(() => removeTenantSession()).toThrow(TenantSessionChangedError);
    expect(getTenantSession()?.sessionId).toBe("B");
    expect(readDraftOwner()).toEqual({ tenantId: "2", userId: "22" });
  } finally {
    stop();
  }
});

it("stale platform cleanup cannot remove either new platform credentials or tenant credentials", async () => {
  const { startPlatformSessionSync } = await import("./platform-session");
  const expires = Date.now() + 60000;
  memStorage.set(userKey, JSON.stringify({ sessionId: "tenant-A", expires }));
  setPlatformSession({ ...platform, sessionId: "operator-A" });
  const stop = startPlatformSessionSync({
    read: () => ({
      token: getPlatformSession()?.sessionId ?? null,
      expires: getPlatformSession()?.expires ?? null
    }),
    invalidate: vi.fn(),
    storageKey: platformSessionKey,
    window: new EventTarget() as Window,
    document: new EventTarget() as Document
  });
  try {
    memStorage.set(
      platformSessionKey,
      JSON.stringify({ ...platform, sessionId: "operator-B" })
    );
    expect(() => removePlatformSession()).toThrow(TenantSessionChangedError);
    expect(getPlatformSession()?.sessionId).toBe("operator-B");
    expect(getTenantSession()?.sessionId).toBe("tenant-A");
  } finally {
    stop();
  }
});

it("upgrade removes legacy credentials without migrating them or clearing unrelated state", () => {
  cookieJar.set("tenant-authorized-token-v2", "legacy-auth-secret");
  memStorage.set("tenant-user-info-v2", "{}");
  memStorage.set("platform-operator-session", "legacy-platform-secret");
  memStorage.set("preferences", "keep");
  clearLegacyAuthentication();
  expect(cookieJar.has("tenant-authorized-token-v2")).toBe(false);
  expect(memStorage.has("platform-operator-session")).toBe(false);
  expect(getTenantSession()).toBeNull();
  expect(memStorage.get("preferences")).toBe("keep");
});
it("persists only session metadata, never a supplied native token", () => {
  setTenantSession({
    sessionId: "marker",
    expires: new Date(Date.now() + 60000),
    accessToken: "secret"
  } as any);
  expect(memStorage.get(userKey)).not.toContain("secret");
  expect(cookieJar.has("tenant-authorized-token-v2")).toBe(false);
});

it("a replacement identity never inherits the previous account's display or permissions", () => {
  const expires = new Date(Date.now() + 60000);
  setTenantSession({
    sessionId: "A",
    expires,
    username: "alpha",
    avatar: "alpha-avatar",
    forceResetPwd: true,
    permissions: ["ROLE:MANAGE"]
  });
  setTenantSession({
    sessionId: "B",
    expires,
    username: "beta",
    userId: 2,
    tenantId: 3,
    forceResetPwd: false
  });
  expect(getTenantSession()).toMatchObject({
    sessionId: "B",
    username: "beta",
    userId: 2,
    tenantId: 3,
    forceResetPwd: false,
    avatar: "",
    permissions: []
  });
});
