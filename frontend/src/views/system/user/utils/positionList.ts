import type { OrgPageQuery } from "@/api/user-manage";

/**
 * 岗位管理列表查询参数构造（T-FE-057 抽取；T-FE-058 补服务端搜索与分页）。
 * 管理面=全部状态：status 缺省不下发（后端不过滤），停用岗位保留在列表中——
 * 可发现、可经编辑弹窗重新启用（恢复入口形态=编辑弹窗，2026-09-22 用户拍板）。
 * 「当前有效岗位」候选排除停用项在授权页主体树请求层完成（SubjectTreePanel
 * org-tree status=1，2026-09-04 用户决策），与本列表互不影响。
 * 分页（F008 闭合）：orgName 服务端模糊搜索 + pageNum/pageSize 翻页——不再固定
 * 第一页 100 条本地过滤（第 101 个岗位静默不可见）。
 */
export function buildPositionPageQuery(args: {
  orgId: number | null;
  status?: number;
  orgName?: string;
  pageNum?: number;
  pageSize?: number;
}): OrgPageQuery {
  return {
    pageNum: args.pageNum ?? 1,
    pageSize: args.pageSize ?? 20,
    orgType: 2,
    ...(args.status !== undefined ? { status: args.status } : {}),
    ...(args.orgId != null ? { orgId: args.orgId } : {}),
    ...(args.orgName ? { orgName: args.orgName } : {})
  };
}
