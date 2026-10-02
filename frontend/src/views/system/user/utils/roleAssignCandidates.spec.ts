/**
 * 分配候选类型收窄回归（T-PERM-097 外评处置直接保护）。
 * 与角色管理页 types.spec.ts（T-PERM-043 MANAGEABLE_ROLE_TYPES）对称：
 * 防止 GROUP_ROLE 被误加回分配候选（后端四入口已 20022，加回=复活恒失败
 * 选项）；PERSONAL 必须保留。
 */
import { describe, it, expect, vi } from "vitest";

// mock @/utils/http：阻断 http → store/router 链（同 role/utils/types.spec.ts 范式）
vi.mock("@/utils/http", () => ({ http: { request: vi.fn() } }));

import { ASSIGNABLE_ROLE_TYPE_CODES } from "./roleAssignCandidates";

describe("分配功能角色候选类型收窄（T-PERM-097）", () => {
  it("仅 BASIC_ROLE/PERSONAL——GROUP_ROLE 不进分配候选", () => {
    expect(ASSIGNABLE_ROLE_TYPE_CODES).toEqual(["BASIC_ROLE", "PERSONAL"]);
  });
});
