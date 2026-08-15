---
doc_type: design
title: Admin Service 设计
status: adopted
domain: admin-service
last_reviewed: 2026-08-15
---

# Admin Service 设计

本文档是 admin-service 的精简设计入口。旧版完整设计已归档到 `../../archive/2026-04-28/admin-service-design.full.md`，仅用于追溯。

## 职责边界

- 负责用户、组织、菜单、认证、字典、通知、文件、审计、任务调度、系统设置等管理能力。
- 作为管理端前端的唯一后端聚合入口，负责聚合返回用户信息、菜单、角色、权限等前端消费结果。
- 负责维护多棵组织业务树；默认组织树作为租户内用户目录/身份池，负责用户生命周期。
- 负责把非默认组织树的成员变更限定为“已有用户与组织节点的关系管理”，不在普通组织成员管理中创建、禁用或删除真实用户。
- 负责把组织作为角色容器进行编排，驱动组织成员关系向权限角色关系映射。
- 负责把用户、组织、菜单等需要参与权限控制的对象同步或建模到权限中心。
- 不直接实现权限引擎，不直接读取权限中心数据库。
- 查询“用户能管理哪些组织/角色/菜单”时，通过权限中心运行时查询接口完成。

## 核心模块

| 模块           | 说明                                                                                           |
| -------------- | ---------------------------------------------------------------------------------------------- |
| 认证           | 登录、OAuth2、Token 生命周期、登录日志；登录只返回最小认证结果，后续通过聚合接口返回用户上下文 |
| 用户           | 用户档案、状态、默认组织树身份目录归属、与权限中心主体和用户管理资源同步                       |
| 组织           | 多组织树、组织状态、组织资源同步；组织既是业务树也是角色容器                                   |
| 菜单           | 菜单树、按钮、前端路由资源同步；对前端统一下发业务菜单与路由结果                               |
| 角色           | 通过权限中心角色与授权接口管理，并向前端聚合角色与权限结果；承接组织默认角色与岗位映射角色编排 |
| 字典/通知/文件 | 管理端通用支撑能力                                                                             |
| 审计/调度/设置 | 操作审计、任务调度、系统配置                                                                   |

## 管理端前端聚合约束

- 管理端前端不直接调用 permission-center，所有用户信息、菜单、角色、权限结果统一经 admin-service 聚合返回。
- 标准认证链为：`/auth/login -> /auth/userinfo -> /auth/user-menu`。
- `/auth/login` 只返回 token 与最小身份信息；`/auth/userinfo` 返回用户上下文；`/auth/user-menu` 返回 `menus + roles + permissions` 等前端初始化所需聚合结果。
- 业务路由与菜单的真实来源是后端聚合结果，而不是前端长期维护的 mock 路由数据。
- 前端 mock 可以保留并改造，用于基础前端验证和演示，但只能作为辅助验证能力，不得作为生产权限或路由事实源。
- 按钮权限统一使用稳定 `permissions` 权限码；模板式 `auths` / `meta.roles` 逻辑仅作为过渡兼容，不作为新增功能设计基准。

## 组织与角色容器约束

- 默认组织树是用户目录/身份池。`sys_org_tree_config.is_default=true` 表示身份目录树，不只是 UI 默认展示树；用户创建、禁用、删除、重置密码等生命周期操作只归默认组织树和 `ADMIN_USER` 权限管控。
- 非默认组织树只能管理“已有用户与本组织节点的关系”。添加成员时，候选用户必须来自默认组织树中操作者可见/可管理范围，不能默认暴露全租户用户。
- 组织由 admin-service 主维护，承担业务树语义；同时组织也是角色容器，用于承载组织默认角色、组织内岗位角色映射等管理规则。
- `user-org` 关系不是纯展示数据，必须稳定映射到 permission-center 的 `user-role` 事实。
- 用户加入组织时，admin-service 负责根据组织默认角色和岗位映射规则编排角色分配；permission-center 负责记录最终的用户角色关系和后续授权计算。
- 组织同步到 permission-center 时，`ADMIN_ORG` 资源镜像与组织角色容器语义并存：前者服务资源权限控制，后者服务角色分配与授权主链。
- `user-org` 写操作必须按树或按关系精确变更，禁止非默认组织树操作删除用户在默认组织树或其他组织树下的关系。

## 与权限中心的交互

- 主体同步：将用户同步为权限中心 `abstract_user(subjectTypeCode=ADMIN_USER, subjectExternalId=sys_user.id)`。
- 用户管理资源同步：将用户同步为 `resource_entity(resourceTypeCode=ADMIN_USER, resourceCode=sys_user.id)`，支撑实例级 `ADMIN_USER` 权限。不存储 resource_entity 内部 ID。
- 组织资源同步：将组织同步为 `resource_entity(resourceTypeCode=ADMIN_ORG, resourceCode=sys_org.id)`，支撑实例级 `ADMIN_ORG` 权限和可管理组织查询。不存储内部 ID。
- 组织角色同步：将组织/岗位同步为 `abstract_role(roleTypeCode=ORG/POSITION, roleExternalId=sys_org.id)`。不存储内部 ID。
- 成员关系同步：将 `sys_user_org` 关系同步为权限中心 `user_role`，用于组织/岗位角色生效。
- 其他资源同步：将菜单、按钮、可管理角色等同步为 `resource_entity`。
- 角色与授权：角色资源权限 `role_resource_permission` 属于 permission-center 的权限管理域，admin-service 不把它纳入同步重试模块；只有在 admin-service 明确承接默认授权编排时，才通过权限中心正式授权接口发起操作。
- 运行时查询：使用 `auth/query-resources` 查询可管理组织、可分配角色、可见菜单等资源集合。
- 前端聚合：permission-center 提供权限事实与运行时查询结果，admin-service 负责把这些结果组装为管理端前端可直接消费的聚合响应。

所有同步和权限校验操作使用业务键定位，admin-service 不存储 permission-center 的任何内部主键 ID。

### 同步任务模型

> **T-ACCESS-005**：内部 admin→permission 同步任务模型已退役。管理事实写入改由 `access.application` 同事务维护本地权限投影，见 [`../access-service-architecture.md`](../access-service-architecture.md) §4 与 `admin-service-api-contract.md` §3。以下段落仅描述已删除的历史模型，不得再实施。

admin-service 曾使用本地消息表 `sys_sync_task` 作为同步任务表。主业务事务内写入业务表和同步任务，事务外由调度器按 `syncAction -> Handler -> Feign/API` 重放（参见 `../archive/2026-08-15/admin-permission-sync.md`，已 superseded）。

同步动作收敛为 4 类领域级 action，具体行为由 payload 中的 `operation` 区分：

| syncAction | 来源 | 目标事实 | 常用 operation |
| ---------- | ---- | -------- | -------------- |
| `PERM_ABSTRACT_USER_SYNC` | `sys_user` | `abstract_user` | `UPSERT`、`DISABLE`、`DELETE` |
| `PERM_ABSTRACT_ROLE_SYNC` | `sys_org` | `abstract_role(ORG/POSITION)` | `UPSERT`、`DISABLE`、`DELETE` |
| `PERM_USER_ROLE_SYNC` | `sys_user_org` | `user_role(ORG/POSITION)` | `BIND`、`UNBIND` |
| `PERM_RESOURCE_ENTITY_SYNC` | `sys_user/sys_org/sys_menu` 等 | `resource_entity` | `UPSERT`、`DISABLE`、`DELETE` |

其中用户、角色、用户角色关系由 admin-service Handler 调用 permission-center 的专用 sync 接口；`resource_entity` 走 permission-center 专用幂等同步入口 `POST /api/perm/resource-entity/sync`。禁止新增全局万能 replay 入口。

`PERM_USER_ROLE_SYNC` 仅承载 `sys_user_org` 派生的组织/岗位关系，payload 必须满足 `sourceType=SYS_USER_ORG` 且 `roleTypeCode in (ORG, POSITION)`。BASIC_ROLE、GROUP_ROLE、PERSONAL 等功能角色分配，以及 `role_resource_permission` 授权关系，均走 permission-center 正式管理 API，不进入 `sys_sync_task`。

同步任务使用“事件键 + 业务键 + 版本”模型：`messageKey/eventKey` 表示单次变更事件唯一；`businessKey` 表示同一同步对象，格式以 `api-contract.md` §6.2.2.4 为准；`businessKeyHash` 为其 SHA-256 lowercase hex，用于唯一约束和高频查询；`syncVersion` 使用事件时间 + 序号，用于乱序保护；`payloadVersion` 表示 DTO 契约版本。同一 `tenantId + syncAction + businessKeyHash` 下未发送任务可合并为最新 payload；合并时 `messageKey/businessKey/payload/syncVersion/displayAttrs` 均覆盖为最新事件，旧事件 key 不再保留。已 `PROCESSING/SUCCESS` 的任务不改，旧版本到达 permission-center 后必须 no-op。

任务主表只允许 `syncAction + businessKey + payload` 参与执行路由；展示信息放入 `displayAttrs`，例如 `entityType/externalId/operationType`，严禁业务逻辑读取这些展示字段做判断。

`payloadVersion` 高于当前 Handler 支持上限时，Handler 必须拒绝执行并把任务置为 `FAILED`，错误分类记为 `NON_RETRYABLE`；后续由人工触发 `rebuild-from-fact` 生成当前版本 payload。低版本 payload 只有在 Handler 明确提供 adapter 时才可兼容执行。

任务状态固定为 `PENDING/PROCESSING/SUCCESS/FAILED`。调度器通过原子 claim 写入 `lockedAt/lockedBy` 后执行；失败且可重试时回到 `PENDING` 并设置 `nextRetryAt`，超过上限或不可重试时进入 `FAILED`。`PROCESSING` 必须支持崩溃恢复：按 `syncAction` 配置 stale lock timeout，任务满足 `status=PROCESSING AND lockedAt < now - timeout(syncAction)` 时可被其他 worker 重新 claim；worker 成功 claim 后必须刷新 `lockedAt/lockedBy`。默认 stale lock timeout：`PERM_ABSTRACT_USER_SYNC`、`PERM_ABSTRACT_ROLE_SYNC`、`PERM_RESOURCE_ENTITY_SYNC`、`PERM_USER_ROLE_SYNC` 为 60s，full-sync 阶段任务为 300s；通过 `application.yml` 的 `accessmesh.sync.scheduler.stale-lock-timeout.{syncAction}` 覆盖。首期人工补偿只支持 `retry-now/reset` 与 `rebuild-from-fact`，不开放手工编辑 payload；每次尝试首期只保留 `lastError`，详细排障依赖结构化日志和 trace。

全量校准任务必须写入 `batchKey` 与 `batchKeyHash`。同一次 full-sync 编排中的所有阶段任务共享同一个 `batchKey`，建议格式为 `sourceService={sourceService}&runId={uuid}`；实时单次同步的 `batchKey` 为空。调度器按 `batchKeyHash + phase` 分阶段推进，只有上一阶段同一批次任务全部 `SUCCESS` 后，才能 claim 下一阶段任务：

| phase | syncAction | 顺序 | 说明 |
| ----- | ---------- | ---- | ---- |
| `USER_SUBJECT` | `PERM_ABSTRACT_USER_SYNC` | 1 | 同步用户主体 |
| `USER_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` | 2 | 同步 `ADMIN_USER` 管理资源 |
| `ORG_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` | 3 | 同步 `ADMIN_ORG` 组织/岗位资源 |
| `ORG_ROLE` | `PERM_ABSTRACT_ROLE_SYNC` | 4 | 同步 `ORG/POSITION` 角色容器 |
| `USER_ROLE` | `PERM_USER_ROLE_SYNC` | 5 | 同步组织/岗位成员关系 |
| `MENU_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` | 6 | 同步菜单、按钮等前端权限资源 |
| `OTHER_RESOURCE` | `PERM_RESOURCE_ENTITY_SYNC` | 7 | 同步其他资源实体 |

同步保障分三层：单次变更实时生成任务；失败任务按显式 `retryClass` 退避重发；最后通过 permission-center 分领域全量校准接口兜底。全量校准采用单请求、强制 scope、分领域接口，由 permission-center 在限定范围内对比上报全量数据并补齐缺失、清理多余事实。

用户、组织和成员关系的完整边界见 `../default-org-tree-user-lifecycle.md`。

API 契约以 `../permission-center/api-contract.md` 为准，核心流程见 `../permission-center/core-flows.md`。

## 数据库

admin-service 表结构以 `../schema/admin-service.sql` 为准。本文档不重复维护字段、索引、约束。

## 实现约束

- 所有接口遵守 `../project-rules.md`。
- 权限相关对象引用权限中心稳定业务键，不依赖权限中心内部主键作为外部契约。
- 组织、菜单、角色等本服务业务数据由 admin-service 自己查询和展示；权限中心只返回权限事实和资源业务键。
