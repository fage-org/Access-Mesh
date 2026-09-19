/**
 * 登录成功提示语义回归锁（T-FE-049 半成功诚实提示）。
 * 旧实现（index.vue 内联）：登录后恒弹 success「登录成功」（+forceResetPwd warning），
 * 菜单/权限加载失败或账号无菜单时无任何失败告知——半成功/空菜单两分支的
 * 「不含 success」断言在旧实现语义下必红（等价被测体：恒 success 列表）。
 */
import { describe, it, expect } from "vitest";
import { resolveLoginMessages } from "./messages";

describe("resolveLoginMessages（T-FE-049 半成功诚实提示）", () => {
  it("菜单加载成功：单条 success「登录成功」（既有行为不变）", () => {
    expect(
      resolveLoginMessages({
        forceResetPwd: false,
        menusCount: 3,
        menuLoadFailed: false
      })
    ).toEqual([{ type: "success", text: "登录成功" }]);
  });

  it("半成功（menus 空且拉取失败）：不弹 success、改弹 warning 如实告知 + 指引占位项重试——旧实现恒 success 必红", () => {
    const got = resolveLoginMessages({
      forceResetPwd: false,
      menusCount: 0,
      menuLoadFailed: true
    });
    expect(got.some(t => t.type === "success")).toBe(false);
    expect(got).toHaveLength(1);
    expect(got[0].type).toBe("warning");
    expect(got[0].text).toContain("菜单与权限加载失败");
    expect(got[0].text).toContain("重试");
  });

  it("拉取成功但账号无菜单（零权限）：不弹 success、warning 引导联系管理员——旧实现恒 success 必红", () => {
    const got = resolveLoginMessages({
      forceResetPwd: false,
      menusCount: 0,
      menuLoadFailed: false
    });
    expect(got.some(t => t.type === "success")).toBe(false);
    expect(got).toHaveLength(1);
    expect(got[0].type).toBe("warning");
    expect(got[0].text).toContain("无可用菜单");
    expect(got[0].text).toContain("联系管理员");
  });

  it("forceResetPwd=true 追加非阻断 warning（6000ms，T-ADMIN-022 既有口径）——成功与半成功两形态均追加", () => {
    const ok = resolveLoginMessages({
      forceResetPwd: true,
      menusCount: 1,
      menuLoadFailed: false
    });
    expect(ok).toHaveLength(2);
    expect(ok[1]).toEqual({
      type: "warning",
      text: "当前密码为初始密码，请联系管理员重置",
      duration: 6000
    });

    const half = resolveLoginMessages({
      forceResetPwd: true,
      menusCount: 0,
      menuLoadFailed: true
    });
    expect(half).toHaveLength(2);
    expect(half[1].duration).toBe(6000);
  });
});
