---
doc_type: task
id: T-PERM-044
title: 四棵树（角色/组织/菜单/资源实体）move 并发成环窗口与递归 CTE 遇环不收敛统一加固
status: proposed
plan: ""
domain: permission-center
design_refs:
  - docs/design/frontend/role-manage.md#§8
  - docs/design/permission-center/api-contract.md#§5.2
depends_on: []
blocks: []
acceptance:
  - "四棵树（abstract_role / sys_org / sys_menu / resource_entity）move 的 check-then-update 并发窗口统一收口：根治方案二选一并四树一致——递归 CTE 改 UNION 去重（遇环自终止，不挂连接）或 move 对涉及行按 id 排序加锁 / 树级 advisory lock（消除窗口本身）。resource_entity 为 T-PERM-028 复评扩入（2026-08-30 用户决策）：moveResource 的跨类型/防环校验同为 check-then-act 无锁，子孙查询 selectDescendantIdsBatch 同为 UNION ALL 递归 CTE、后果同构"
  - "环路一旦落库后的自愈/检测能力：任一树出现 parent 环时树构建与鉴权查询不挂死（TreeBuilder 已有 visited 防环，缺口在 SQL 侧递归 CTE），并能定位环节点供订正"
  - "回归测试：PgIT 用 JDBC 制造 2-环后调用子孙/祖先递归查询，锁定不挂死且行为确定；并发交叉移动的窗口用例（可选，视根治方案；resource_entity 侧 T-PERM-028 已有 move 防环单测+PgIT 语义锁可复用）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-03
---

# T-PERM-044 四棵树（角色/组织/菜单/资源实体）move 并发成环窗口与递归 CTE 遇环不收敛统一加固

> 状态：proposed（2026-08-28 T-PERM-022 双轨评审登记，设计定案：登记跨域统一修）
> 依赖：无（独立维护债）
> 前置验收：见 acceptance

## 背景

T-PERM-022 为角色域 moveRole 补齐了环路防护（自身/子孙拒绝 20050，`ROLE_PARENT_INVALID`）与 sync/full-sync 同款判定，但防护为 check-then-update 非串行：两个写请求在同一瞬间交叉移动同一子树（A 挂 B 下、同时 B 挂 A 下）时双双通过校验、双双落库，数据库出现环。admin 域组织树（`ORG_PARENT_CYCLE` 10108）与菜单树（`MENU_PARENT_INVALID` 10207）为先例同构写法，同样未处理并发窗口。

后果放大项：四棵树的子孙/祖先查询均为 `UNION ALL` 递归 CTE，且全仓无 `statement_timeout` 配置——环一旦落库，鉴权链路在用的递归查询（如 `selectDescendantIdsBatch` / `selectAncestorGroupRoleIds`）在环上不收敛，可挂死连接占满连接池；`TreeBuilder` 有 visited 防环（树构建不挂），缺口在 SQL 侧。

## 范围

- 统一评估并落地：UNION 去重自终止（止损，不解决窗口）或行锁/advisory lock（根治窗口）或两者组合，四棵树同构统一。
- 全仓 `statement_timeout` 兜底策略评估（可选，随方案定）。
- 不改各树既有的类型一致/环路业务校验语义（T-PERM-022 已落地部分保持不变）。

## 优先级依据

触发窗口极窄（双管理员同瞬交叉拖拽同一子树），且当前无已知环数据；属低概率高影响维护债，随维护债批次排期。

## 登记追加

- `OrgServiceImpl.treeOrgs` 读路径两处向下递归（T-ADMIN-021 收口登记）——`keepMatching` 名称剪枝 / `buildTree` 子树构建无环防护：`scopeToSubtree` 的步数上限只保护祖先链方向，数据异常父环（move 并发窗口脏数据）下会 StackOverflow。本任务统一加固时一并覆盖（visited 集合或深度上限）。
