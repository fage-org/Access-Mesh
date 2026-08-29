---
doc_type: task
id: T-PERM-033
title: 权限排查后端门禁统一 + DTO 扩展（直连 /api/perm/*，无聚合层）
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§6.6
  - docs/design/permission-center/api-contract.md#§6.7
  - docs/design/permission-center/api-contract.md#§6.8
  - docs/design/permission-center/implementation.md
  - docs/design/frontend/permission-query.md
depends_on:
  - T-FE-013
blocks: []
acceptance:
  - "门禁设计定案（2026-08-29）：不引入独立排查权限码（原预案 PERMISSION_QUERY:VIEW 否决——权限码结构为「资源:操作」，PERMISSION_QUERY 是操作描述而非资源）；explain/recent-changes 门禁从 SYSTEM_CONFIG:VIEW 切换为被查目标实例 USER:VIEW/ROLE:VIEW（与 effective-permissions 同款：ROLE 未解析类型级兜底、USER 未解析返回空/NOT_FOUND）；query-scopes 维持运行时语义不加门禁（契约 §6.7 登记管理端排查复用）"
  - "explain 响应扩展：conditionEvaluations（逐项评估过程 + status OK/DISABLED/NOT_FOUND/INVALID fail-close）+ conflictDrops（互斥丢弃条目 + 命中规则）+ evaluationContextSource（ADMIN_INPUT/CURRENT_REQUEST）+ evaluatedClientIp；请求增可选 context.clientIp（缺省回退当前请求）；日期/时间条件按服务进程时钟评估不可模拟；敏感条件值脱敏（IP 掩码主机段，日期/时间原样）"
  - "recentChanges 按完整权限键 6 字段过滤（resourceTypeCode/operationCode/scopeMode 精确，domainCode/resourceCode/codeType 请求侧 null 通配）+ USER 目标保留 USER_ROLE_CHANGE；impactLevel 对齐 DIRECT/POSSIBLE"
  - "LOCAL_USER/USER 主体语义核对完成：resolveUserId 走 type_definition user_type + abstract_user(tenant,type,externalId)；LOCAL_USER external_id=sys_user.id 字符串（本地投影 T-ORG-001 同源）"
  - "query-resources / permission-view/* 契约核对完成：query-resources 字段名与 §6.6 逐项一致；唯一差异 resource-users（契约写「用户」实现返回角色授予分布）已登记 permission-query.md §9"
  - "前端 perms 常量切换完成：QUERY_VIEW(SYSTEM_CONFIG:VIEW) → USER_VIEW/ROLE_VIEW 任一命中（canQuery）；契约路径切换在 T-FE-019 联调执行"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-29
---

# T-PERM-033 权限排查后端门禁统一 + DTO 扩展

> 状态：done（2026-08-29 收口）
> 复杂度：🟡 中（单模块：access-service permission 域，跨域只读经 application.query）
> 前端任务：T-FE-013（Phase 1 mock 已完成）；契约路径联调切换归 T-FE-019

## 背景

T-FE-013 权限排查页前端已实现（Phase 1 mock 驱动，mock 路径 `/permission-query/*`）。

> **重基线（T-ACCESS-012，2026-08-22：取消聚合层）**：原「admin 域聚合层」方案取消——归并后 `/perm/**` 与 `/admin/**` 同路由到 access-service，页面直连契约端点，不新增 Controller/聚合 DTO/Gateway 路由（维持 3 路由契约）。

前端核实发现的后端现状问题（详见 `docs/design/frontend/permission-query.md` §8-9，均已随本任务收口）：

- explain 门禁 SYSTEM_CONFIG:VIEW、effective-permissions 目标实例 VIEW —— 口径割裂 → 统一为目标实例 VIEW
- query-resources/query-scopes 运行时接口无排查门禁 → 设计定案维持运行时语义（§6.7 登记）
- explain 响应无命中条件/条件评估/冲突详情 → conditionEvaluations/conflictDrops 扩展
- recentChanges 只按用户/角色取 50 条 → 按权限键 6 字段过滤
- LOCAL_USER/USER 主体类型语义待核对 → 核对结论登记 permission-query.md §3

## 范围（收口口径）

1. **门禁设计定案**：不新增 `ResourceTypeCode.PERMISSION_QUERY`（原五步清单取消）；explain（`PermissionViewAppServiceImpl.explain`）与 recent-changes（`LogQueryAppServiceImpl.getRecentChanges`）门禁切被查目标实例 `USER:VIEW`/`ROLE:VIEW`。
2. **explain DTO 扩展**：请求 `PermissionExplainReq.context.clientIp`（管理员模拟输入，缺省回退当前请求 `HttpRequestUtils`）；响应 `evaluationContextSource`/`evaluatedClientIp`/`conditionEvaluations`（新领域方法 `PermissionConditionDomainService.evaluateDetailed`，IP 掩码脱敏）/`conflictDrops`（新领域方法 `PermissionConflictDomainService.filterPermMutexWithDrops`，不触发冲突通知）。
3. **recentChanges 权限键过滤**：候选池 200（按目标+窗口）→ 6 字段匹配 + USER 目标保留 USER_ROLE_CHANGE → 返回上限 50；匹配 item 摘要（不再固定 items[0]）。
4. **主体语义核对**：结论登记 permission-query.md §3（LOCAL_USER external_id=sys_user.id 字符串）。
5. **query-resources 核对**：字段名与 §6.6 逐项一致，无差异。
6. **permission-view/* 核对**：唯一差异 resource-users（契约「用户」vs 实现角色维度）登记 permission-query.md §9，待有消费方时收口。
7. **前端切换**：perms.ts 常量 USER_VIEW/ROLE_VIEW + canQuery 任一命中；mock 矩阵无需增配（ORG_USER/ROLE_MANAGE VIEW 清单已含）。

## 非目标

- 不新增聚合层、聚合 Controller/DTO 或 Gateway 路由（T-ACCESS-012 决策）。
- 不改变三端点既有契约路径与请求/响应主体结构（explain 扩展为增量字段）。
- 前端 explain 新字段展示与契约路径切换归 T-FE-019 联调。

## 验收对照

见 frontmatter acceptance；设计回写：api-contract §6.7（query-scopes 排查复用登记）/§6.8（explain 契约扩展 + 门禁规则 + recent-changes 过滤语义）、permission-query.md §3/§8/§9，均已完成。

## 完成记录

- 2026-08-29 收口：门禁设计定案（无独立排查码）+ explain DTO 扩展（评估上下文两态/条件明细脱敏/互斥丢弃）+ recentChanges 权限键过滤 + 四项核对登记；新增领域两方法与 explain/recent-changes 门禁、明细、过滤的单元测试，回归 access-service mvn test 全绿，前端 typecheck 干净 + vitest 全绿。
