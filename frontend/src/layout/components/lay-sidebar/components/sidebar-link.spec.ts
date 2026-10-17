import { describe, expect, it } from "vitest";
import { externalMenuHref } from "./sidebar-link";

describe("菜单外链地址", () => {
  it("无 name 的现役外链以 path 导航", () => {
    expect(externalMenuHref({ path: "https://example.com/docs" })).toBe(
      "https://example.com/docs"
    );
  });
  it("path 优先于模板 name，保留旧模板兼容", () => {
    expect(
      externalMenuHref({
        path: "https://example.com/current",
        name: "https://example.com/old"
      })
    ).toBe("https://example.com/current");
    expect(externalMenuHref({ name: "https://example.com/old" })).toBe(
      "https://example.com/old"
    );
  });
  it("本地菜单保持路由导航", () => {
    expect(
      externalMenuHref({ path: "/system/user", name: "User" })
    ).toBeUndefined();
  });
});
