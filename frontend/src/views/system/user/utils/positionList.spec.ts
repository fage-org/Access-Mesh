import { describe, it, expect } from "vitest";
import { buildPositionPageQuery } from "./positionList";

/**
 * T-FE-057 回归锁：岗位管理列表保留停用岗位（管理面=全部状态，可发现可恢复）。
 * 旧实现（PositionTab 内联固定 status:1）下「默认不含 status 键」用例实证红。
 * 「当前有效岗位」候选排除停用项在授权页主体树请求层完成（SubjectTreePanel
 * status=1，2026-09-04 用户决策），与本列表互不影响。
 *
 * T-FE-058 追加：服务端 orgName 搜索 + 分页参数（旧实现固定 pageNum:1/pageSize:100
 * 且无 orgName 键——「分页透传」「搜索透传」用例在旧构造下实证红）。
 */
describe("岗位管理列表查询构造（T-FE-057）", () => {
  it("默认（无状态筛选）不含 status 键——管理列表显示全部状态", () => {
    const query = buildPositionPageQuery({ orgId: 101 });
    expect("status" in query).toBe(false);
  });

  it("状态筛选透传：启用→status:1、停用→status:0", () => {
    expect(buildPositionPageQuery({ orgId: 101, status: 1 }).status).toBe(1);
    expect(buildPositionPageQuery({ orgId: 101, status: 0 }).status).toBe(0);
  });

  it("orgId：null 不下发（岗位 Tab 未选组织的全量范围）、传值透传子树过滤", () => {
    expect("orgId" in buildPositionPageQuery({ orgId: null })).toBe(false);
    expect(buildPositionPageQuery({ orgId: 205 }).orgId).toBe(205);
  });

  it("orgType 恒为 2（岗位）且默认分页 pageNum:1/pageSize:20", () => {
    const query = buildPositionPageQuery({ orgId: 101 });
    expect(query.orgType).toBe(2);
    expect(query.pageNum).toBe(1);
    expect(query.pageSize).toBe(20);
  });
});

describe("岗位管理列表搜索与分页构造（T-FE-058）", () => {
  it("orgName：空串/缺省不下发（后端不过滤），传值透传服务端模糊搜索", () => {
    expect("orgName" in buildPositionPageQuery({ orgId: 101 })).toBe(false);
    expect(
      "orgName" in buildPositionPageQuery({ orgId: 101, orgName: "" })
    ).toBe(false);
    expect(
      buildPositionPageQuery({ orgId: 101, orgName: "车间" }).orgName
    ).toBe("车间");
  });

  it("翻页透传：第 6 页/页大小 50 原样下发（不再固定第一页）", () => {
    const query = buildPositionPageQuery({
      orgId: 101,
      pageNum: 6,
      pageSize: 50
    });
    expect(query.pageNum).toBe(6);
    expect(query.pageSize).toBe(50);
  });
});
