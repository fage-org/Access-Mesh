import { describe, expect, it } from "vitest";
import { operations, presetOperationsForType } from "./resource-fixtures";

// T-FE-017：路由段删除后，本 spec 改锁共享数据导出（唯一存活消费方 =
// resource-dependency mock；type-def / permission-grant mock 已随 T-FE-018 联调整删）；
// 原 list 路由空白串过滤契约用例随路由段一并退役（消费方已切真实后端，契约行为由 PgIT 侧覆盖）。

describe("resource-fixtures mock 共享数据", () => {
  it("种子操作按资源类型预置 CRUD 四操作，位字段为十进制字符串", () => {
    const menuOps = operations.filter(op => op.resourceTypeCode === "MENU");
    expect(menuOps.map(op => op.code).sort()).toEqual([
      "CREATE",
      "DELETE",
      "UPDATE",
      "VIEW"
    ]);
    for (const op of menuOps) {
      expect(op.binaryBit).toMatch(/^\d+$/);
      expect(op.inheritMask).toMatch(/^\d+$/);
    }
  });

  it("presetOperationsForType 为新类型联动预置 CRUD 四操作位（T-PERM-028 同事务预置语义）", () => {
    const before = operations.length;
    presetOperationsForType("NEW_TYPE_X");
    const added = operations.slice(before);
    expect(added).toHaveLength(4);
    expect(added.map(op => op.code).sort()).toEqual([
      "CREATE",
      "DELETE",
      "UPDATE",
      "VIEW"
    ]);
    expect(added.every(op => op.resourceTypeCode === "NEW_TYPE_X")).toBe(true);
    expect(added.every(op => op.resourceTypeName === null)).toBe(true);
  });
});
