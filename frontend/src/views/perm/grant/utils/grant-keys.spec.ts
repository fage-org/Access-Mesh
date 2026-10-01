import { describe, it, expect } from "vitest";
import {
  encodeKeyTuple,
  grantTupleKey,
  resourceNodeKey,
  resourceTupleKey,
  roleSubjectKey
} from "./grant-keys";

describe("grant-keys（T-FE-060 键编码单源）", () => {
  it("产物是 DOM 属性选择器安全的：不含引号/方括号/反斜杠（外评 P2 回归锁）", () => {
    // el-table-v2 固定列把 row-key 未经转义插入 [rowkey="…"]；纯 JSON.stringify 产物
    // （旧形态）含 " 与 [ ]，构成非法 CSS 选择器使 querySelectorAll 抛 SyntaxError
    const keys = [
      encodeKeyTuple("RES", "TD", 'a:"b', "c|d"),
      encodeKeyTuple("ALL", "TD", "\\", ","),
      grantTupleKey({
        resourceTypeCode: "TD",
        resourceCode: "a|b",
        codeType: "c",
        operationKey: "bits:6",
        scopeMode: "INSTANCE"
      }),
      resourceTupleKey({
        resourceTypeCode: "TD",
        resourceCode: "x[y]",
        codeType: "*"
      }),
      resourceNodeKey({ resourceTypeCode: "TD", code: "q,\\w", codeType: null })
    ];
    for (const key of keys) {
      expect(key).not.toMatch(/["[\]\\]/);
    }
  });

  it("单射：分隔符碰撞对各归各（| 与 : 段）", () => {
    expect(encodeKeyTuple("TD", "a|b", "c")).not.toBe(
      encodeKeyTuple("TD", "a", "b|c")
    );
    expect(encodeKeyTuple("TD", "a:b", "c")).not.toBe(
      encodeKeyTuple("TD", "a", "b:c")
    );
    expect(
      resourceTupleKey({
        resourceTypeCode: "TD",
        resourceCode: "a|b",
        codeType: "c"
      })
    ).not.toBe(
      resourceTupleKey({
        resourceTypeCode: "TD",
        resourceCode: "a",
        codeType: "b|c"
      })
    );
  });

  it("undefined 与 null 段归一为同一段值（对齐字段直比面的 ?? null 语义）", () => {
    expect(encodeKeyTuple("TD", undefined, "c")).toBe(
      encodeKeyTuple("TD", null, "c")
    );
  });

  it("roleSubjectKey：externalId 与数字 id 分命名空间", () => {
    expect(roleSubjectKey("5", 77)).not.toBe(roleSubjectKey(null, 5));
    expect(roleSubjectKey("5", 77)).toContain("ext:5");
    expect(roleSubjectKey(null, 5)).toContain("id:5");
  });
});
