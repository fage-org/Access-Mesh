/**
 * 强制改密标记清除锁（T-FE-046）。
 * clearForceResetPwdFlag 是改密成功后解除路由阻断的存储侧写点（跨标签共享
 * userKey，其余标签下次导航重读即放行）——写丢字段或写错键会把用户锁死在
 * 改密页，本 spec 直接锁其存储行为。mock 断环说明：@/store/modules/user 为
 * setToken 依赖（Pinia+router 链，本被测函数不触达）、@pureadmin/utils 的
 * storageLocal 用内存 Map 替 localStorage（node 环境无 DOM）。
 */
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

import { clearForceResetPwdFlag, userKey } from "@/utils/auth";

beforeEach(() => {
  memStorage.clear();
});

describe("clearForceResetPwdFlag（T-FE-046）", () => {
  it("阻断标记置 false：其余字段原样保留（会话保留不强制重登）", () => {
    memStorage.set(
      userKey,
      JSON.stringify({
        username: "admin",
        roles: ["BASIC_ROLE"],
        permissions: [],
        userId: 1,
        forceResetPwd: true
      })
    );

    clearForceResetPwdFlag();

    const stored = JSON.parse(memStorage.get(userKey) as string);
    expect(stored.forceResetPwd).toBe(false);
    expect(stored.userId).toBe(1);
    expect(stored.username).toBe("admin");
    expect(stored.roles).toEqual(["BASIC_ROLE"]);
  });

  it("userKey 不存在（登出/未登录态）：静默无写（removeToken 已清空，不重建空对象）", () => {
    clearForceResetPwdFlag();
    expect(memStorage.has(userKey)).toBe(false);
  });
});
