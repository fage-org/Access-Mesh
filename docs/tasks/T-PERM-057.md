---
doc_type: task
id: T-PERM-057
title: 权限查询统一引擎重构——收编五套形态 + 判定面继承 + 评估拉平
status: proposed
plan: docs/plans/permission-query-unification-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/implementation.md#§3
  - docs/design/permission-center/implementation.md#§5.2
  - docs/design/permission-center/core-flows.md#§7
  - docs/design/permission-center/overview.md#引擎节
  - docs/design/permission-center/query-engine-unification.md
  - docs/design/access-service-rebuild-runbook.md#§3
depends_on: []
blocks: []
acceptance:
  - "引擎收编：getDeniedResourceCodes/getDeniedEntityIds 手写管线、query-resources 的 expandResourceScope、PermissionGrantDomainServiceImpl canGrant 直查管线全部收编进统一引擎（全仓无引擎外权限查询独立管线）；deleteRoles 局部级联口径注释与实现回归统一语义"
  - "入参模型落地：roleIds 主语（userId→角色解析入封装层含缓存）、resourceTypeCodes 集合、目标集下推/全量分层、位覆盖常开、条件三态、冲突开关、判定面继承、展示面展开、主资源上下文一等入参（形态见 query-engine-unification.md §3）"
  - "判定面继承落地：递归 CTE 目标闭包（镜像 selectDescendantIdsBatch 防环先例），默认值矩阵（管理面写门禁/读过滤面开、auth-check 关+参数、清单面不适用）+ 批量拒绝闭包回映射 + 闭包类型边界与软删行为显式定界"
  - "评估口径拉平：管理面门禁条件评估拉平为评估（22 个调用点文件全量门禁行为回归）+ 写门禁条件上下文装配定案 + 两级互斥过滤归属矩阵定案"
  - "缓存与链路核对：ROLE_PERM_SNAPSHOT/OPERATION_PERMISSIONS_BY_TYPE/EFFECTIVE_ROLES/网关快照键与失效不变；GoldenFixturePgIT selfAndAncestors 手工模拟收敛回引擎单点判定；OAuth2 委托链路排除声明写入 implementation §3"
  - "规范面同步：permission-query-pipeline skill 双副本（.claude/.agents）与 .claude/rules/permission-center-coding-standards.md 同步改写"
  - "收口回归：mvn test -T 1C 全量（含 E2E）；runbook §3 补升级 FAQ（读面可见集变大/写门禁变严的存量行为差异）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-09
---

# T-PERM-057 权限查询统一引擎重构

> 状态：proposed（2026-09-09 grill 定案立项；终态设计见 [query-engine-unification.md](../design/permission-center/query-engine-unification.md)，本卡不复制设计）
> 依赖：无（T-PERM-045 已取消，其范围「内部管理门禁统一启用子级继承」整体并入本卡）

## 背景

权限查询现状五套执行形态分叉（详见 query-engine-unification.md §1）；判定面继承缺位致「授权在父、查子」判定必拒，deleteRoles 局部规则与金样测试手工模拟各自补丁。2026-09-09 用户 grill 访谈定案统一引擎模型（Q1-Q15），双子代理评审修订后落盘设计。

## 范围

- 引擎统一重构与五套形态收编；入参模型/管线/结果模型（双轨）按设计 §2-§4、§8。
- 判定面继承与默认值矩阵（§5-§6）；评估口径拉平（§7）；实现边界九项（§10）。
- 文档回写：implementation §3 重写为统一引擎版（query-engine-unification.md 并入后转 superseded）+ §5.2 失效触发点核对；core-flows §7/§10.1/§15/§16、overview 引擎节同步；skill 双副本 + rule 同步；runbook FAQ。

## 非目标 / 遗留

- check 族三端点结果记录回传 → T-API-003。
- depend_on 单点闭合语义 → T-PERM-058（依赖本卡）。
- 权限视图/排查删除重设计 → T-PERM-059。
- API 授权模式改造（操作权限关联派生接口权限）→ T-PERM-054（方向已定、方案未定）。
