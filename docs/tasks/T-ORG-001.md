---
doc_type: task
id: T-ORG-001
title: 统一本地主体 ID（B-lite：共享主体 ID，删除 OperatorSubjectResolver）
status: proposed
plan: docs/plans/product-vertical-slice-plan.md
domain: org-user
design_refs:
  - docs/design/access-service-architecture.md
  - docs/design/schema/access-service.sql
  - docs/design/permission-center/implementation.md
  - docs/design/default-org-tree-user-lifecycle.md
depends_on: [T-PERM-042]
blocks: [T-ACCESS-018, T-ADMIN-022]
acceptance:
  - "新建本地用户后 sys_user.id == abstract_user.id；先创建外部主体、再创建本地用户不会主键碰撞（主体专用序列或等价机制生效）"
  - "生产代码中 OperatorSubjectResolver 与 resolveOperatorSubjectId 引用为零（全量 grep 清零，含 javadoc）"
  - "登录 ID、审计操作者 ID（createdBy/updatedBy/deletedBy）、user_role.abstract_user_id、Sa-Token 会话、perm:effective-roles 缓存键数值一致且均为主体 ID"
  - "登录/审计/缓存对外接口形态不变：Sa-Token 仍登录同一 Long、审计仍记录该 Long、user_role 与角色展开缓存仍以主体 ID 为键——变化集中在 ID 分配、投影写入调用方与 Resolver 删除"
  - "缓存框架与 catalog 零修改：主体键缓存（effective-roles 等）本以 abstract_user.id 为标识符，统一后数值与键格式均不变；不新增迁移专用 evictAll、审计日志或兼容键；空库重建 runbook 含清理开发/验收 Redis 实例"
  - "既有用户投影链路调用方随统一主体 ID 切换（数值统一后 sys_user.id = abstract_user.id，投影 code 语义自然一致，无需投影切换代码）；USER/ROLE 投影全写路径补齐归 T-ACCESS-019"
  - "空库重建 runbook：按权威 DDL 重建后走全量种子 + 用户/角色/组织/菜单样例验证主体链；不做在线双读迁移"
  - "全量单测 + CI 门控全绿（CI 已由 T-ACCESS-017 落地，无需外部主机重复执行）；T-ACCESS-017 特征测试全绿（Resolver 映射特征测试随 Resolver 删除同步移除）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-23
---

# T-ORG-001 统一本地主体 ID（B-lite）

## 背景

本地用户现有三个数值独立 ID（sys_user.id 独立自增用于登录/审计；abstract_user.id 独立自增用于权限主体，创建顺序为先插 sys_user 再以其 id 为 external_id 插 abstract_user；resource_entity(ADMIN_USER).id 为实例授权代理键）。OperatorSubjectResolver 约 80 次调用分布于 18 个生产文件，已是系统性心智税；三空间同为 Long 导致实例门禁错传难以被断言识别（该缺陷已由 T-PERM-042 在 API 层面消除）。已确认采纳 B-lite：abstract_user.id 为唯一主体 ID，本地用户 sys_user.id = abstract_user.id，外部主体仅有 abstract_user 行；不引入全局对象 ID 中心，最多使用只服务于用户主体的数据库序列防碰撞。

本任务在 T-PERM-042（引擎显式 API）之后、类型收敛（T-ACCESS-018）与投影补齐（T-ACCESS-019）之前实施：投影在 T-ACCESS-019 直接以最终主体 ID + 最终类型码一次写成，本任务不需要动投影语义。

## 范围

- ID 分配机制：用户主体专用序列（或等价方案，按 T-ACCESS-016 定稿），本地用户创建写链路调整（abstract_user 与 sys_user 同 ID 落库，写入顺序按实现确定）。
- 删除 OperatorSubjectResolver 与全部调用点；会话、OperatorContext、审计填充、user_role、既有投影链路调用方统一主体 ID。
- 空库重建 runbook（含清理开发/验收 Redis）与全量回归。

## 当前口径

- 统一后保留的两个 ID 属不同概念（主体"谁" vs resource_entity"哪条授权记录"），不视为重复设计。
- 缓存零修改：主体键缓存标识符本就是 abstract_user.id，统一不改键；不做 evictAll 迁移（空库重建模式下开发 Redis 随 runbook 清理）。
- 外部同步主体业务键链路不变（abstract_user.external_id 语义保留）。
- 未上线、库可重建：不做在线迁移、不建双读/兼容层。
- 与 T-PERM-042 / T-ACCESS-018 分批独立提交，禁止一次提交同时修改身份、资源类型、引擎与缓存。

## 非目标 / 遗留

- 不删除 abstract_user / resource_entity 表，不做全平台统一 ID。
- 不重写缓存框架（保留冻结）。
- USER/ROLE 投影全写路径补齐归 T-ACCESS-019；bootstrap 种子归 T-ACCESS-020。
