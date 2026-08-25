/**
 * 角色管理页类型收窄回归（T-PERM-043 验收「前端隐藏 GROUP_ROLE」的直接保护）。
 * 与授予页 subject-tree.spec.ts 对称：防止 GROUP_ROLE 被误加回
 * MANAGEABLE_ROLE_TYPES（随常量自动收窄的下拉/树过滤将一并放开）。
 */
import { describe, it, expect, vi } from "vitest";

// mock @/utils/http：阻断 http → store/router 链（同 grant-store.spec.ts 范式）
vi.mock("@/utils/http", () => ({ http: { request: vi.fn() } }));

import { MANAGEABLE_ROLE_TYPES, ROLE_TYPE_CODE } from "@/api/role-manage";
import { isPageVisibleRoleType } from "./types";

describe("角色管理页类型收窄（T-PERM-043）", () => {
  it("MANAGEABLE_ROLE_TYPES 仅 BASIC_ROLE（GROUP_ROLE 隐藏）", () => {
    expect(MANAGEABLE_ROLE_TYPES).toEqual([ROLE_TYPE_CODE.BASIC_ROLE]);
  });

  it("isPageVisibleRoleType：仅 BASIC_ROLE 可见，GROUP_ROLE 与同步类型不进树", () => {
    expect(isPageVisibleRoleType("BASIC_ROLE")).toBe(true);
    expect(isPageVisibleRoleType("GROUP_ROLE")).toBe(false);
    expect(isPageVisibleRoleType("ORG")).toBe(false);
    expect(isPageVisibleRoleType("POSITION")).toBe(false);
    expect(isPageVisibleRoleType("PERSONAL")).toBe(false);
  });
});
