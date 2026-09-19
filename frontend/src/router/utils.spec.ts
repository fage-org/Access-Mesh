/**
 * 空侧栏占位项两态判定回归锁（T-FE-049）。
 * 旧实现：menus 为空恒渲染「菜单加载失败，点击重试」占位项——拉取成功但账号
 * 无菜单（零权限用户）被误导为可重试恢复（重试永远「失败」）。empty 分支断言
 * 在旧实现（恒 retry 占位项）下必红。
 * mock 说明：utils.ts 模块头部 import 链含 router 实例与三个 store hook（均仅
 * 函数内消费），此处 mock 断链——被测对象 resolveSidebarFallback 为纯函数。
 */
import { describe, it, expect, vi } from "vitest";

vi.mock("./index", () => ({ router: {} }));
vi.mock("@/store/modules/multiTags", () => ({
  useMultiTagsStoreHook: () => ({})
}));
vi.mock("@/store/modules/permission", () => ({
  usePermissionStoreHook: () => ({})
}));
vi.mock("@/store/modules/user", () => ({ useUserStoreHook: () => ({}) }));
vi.mock("@/layout/types", () => ({ routerArrays: [] }));

import { resolveSidebarFallback } from "./utils";

describe("resolveSidebarFallback 空侧栏占位两态（T-FE-049）", () => {
  it("拉取失败：可重试占位项「菜单加载失败，点击重试」（既有行为不变）", () => {
    const items = resolveSidebarFallback(true);
    expect(items).toHaveLength(1);
    expect(items[0].meta?.title).toBe("菜单加载失败，点击重试");
    expect(items[0].path).toBe("/menu-retry");
  });

  it("拉取成功但账号无菜单：占位项「当前账号无可用菜单」——旧实现恒 retry 占位必红", () => {
    const items = resolveSidebarFallback(false);
    expect(items).toHaveLength(1);
    expect(items[0].meta?.title).toBe("当前账号无可用菜单");
    expect(items[0].path).toBe("/menu-retry");
  });

  it("menuLoadFailed 未定义（store 旧态/恢复前）按失败占位兜底（fail-closed 方向）", () => {
    expect(resolveSidebarFallback(undefined)[0].meta?.title).toBe(
      "菜单加载失败，点击重试"
    );
  });

  it("每次调用返回 cloneDeep 副本，侧栏组件改动不污染常量", () => {
    const a = resolveSidebarFallback(true);
    const b = resolveSidebarFallback(true);
    expect(a[0]).not.toBe(b[0]);
    a[0].meta.title = "mutated";
    expect(resolveSidebarFallback(true)[0].meta?.title).toBe(
      "菜单加载失败，点击重试"
    );
  });
});
