import { describe, it, expect, beforeEach, vi } from "vitest";

const { memStorage } = vi.hoisted(() => ({
  memStorage: new Map<string, string>()
}));

vi.mock("@/store/modules/user", () => ({
  useUserStoreHook: () => ({ isRemembered: false, loginDay: 7 })
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
  getToken,
  removeToken,
  getPlatformSession,
  setPlatformSession,
  removePlatformSession,
  userKey,
  platformSessionKey
} from "@/utils/auth";
import { readDraftOwner } from "@/views/perm/grant/utils/draft-storage";
beforeEach(() => memStorage.clear());
const platform = {
  accessToken: "operator-token",
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
      JSON.stringify({ accessToken: "tenant-token", tenantId: 2, userId: 1 })
    );
    removeToken();
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
      JSON.stringify({ accessToken: "legacy", userId: 1 })
    );
    expect(getToken()).toBeNull();
    memStorage.set(userKey, JSON.stringify({ userId: 1 }));
    expect(readDraftOwner()).toBeNull();
  });
});
