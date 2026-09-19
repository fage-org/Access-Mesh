/**
 * 登录成功提示语义回归锁（T-FE-049 半成功诚实提示）。
 * 旧实现（index.vue 内联）：登录后恒弹 success「登录成功」（+forceResetPwd warning），
 * 菜单/权限加载失败或账号无菜单时无任何失败告知——半成功/空菜单两分支的
 * 「不含 success」断言在旧实现语义下必红（等价被测体：恒 success 列表）。
 * forceResetPwd 提示分支已删（T-FE-046）：强制改密改为路由守卫阻断
 * （登录后只放行改密页），登录页不再弹「请联系管理员重置」warning。
 */
import { describe, it, expect } from "vitest";
import { resolveLoginMessages } from "./messages";

describe("resolveLoginMessages（T-FE-049 半成功诚实提示）", () => {
  it("菜单加载成功：单条 success「登录成功」（既有行为不变）", () => {
    expect(
      resolveLoginMessages({
        menusCount: 3,
        menuLoadFailed: false
      })
    ).toEqual([{ type: "success", text: "登录成功" }]);
  });

  it("半成功（menus 空且拉取失败）：不弹 success、改弹 warning 如实告知 + 指引占位项重试——旧实现恒 success 必红", () => {
    const got = resolveLoginMessages({
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
      menusCount: 0,
      menuLoadFailed: false
    });
    expect(got.some(t => t.type === "success")).toBe(false);
    expect(got).toHaveLength(1);
    expect(got[0].type).toBe("warning");
    expect(got[0].text).toContain("无可用菜单");
    expect(got[0].text).toContain("联系管理员");
  });

  it("forceResetPwd=true 不再追加登录提示（T-FE-046 阻断流程取代 warning）——旧实现追加「初始密码」warning 必红", () => {
    // 旧入参形态（含 forceResetPwd: true）经宽类型传入：新签名已无该入参，
    // 运行时被忽略——旧实现会追加第二条 warning，断言长度与文案双红
    const legacyInput = {
      forceResetPwd: true,
      menusCount: 1,
      menuLoadFailed: false
    } as unknown as Parameters<typeof resolveLoginMessages>[0];
    const got = resolveLoginMessages(legacyInput);
    expect(got).toHaveLength(1);
    expect(got.some(t => t.text.includes("初始密码"))).toBe(false);
  });
});
