---
doc_type: task
id: T-PERM-071
title: 依赖声明层（declaration 表 + manifest 通道 + 编译器 + starter）
status: proposed
plan: —（无所属计划；T-PERM-035 实现序列 035A）
domain: permission-center
design_refs:
  - docs/design/dependency-auto-grant.md#§2（总体架构与单写者分层）
  - docs/design/dependency-auto-grant.md#§3.1（permission_dependency_declaration）
  - docs/design/dependency-auto-grant.md#§3.2（service_manifest_sync）
  - docs/design/dependency-auto-grant.md#§3.4（resource_dependency 改造）
  - docs/design/dependency-auto-grant.md#§4（Manifest 协议与 starter）
  - docs/design/dependency-auto-grant.md#§5（DependencyCompiler）
  - docs/design/access-service-api-contract.md（manifest 章新登记+batch-sync 删除收口）
  - docs/design/schema/access-service.sql（DDL 落地）
  - docs/design/frontend/resource-dependency.md（autoGrant/SYNC 退役回写）
depends_on:
  - T-PERM-070
blocks: []
acceptance:
  - "schema 落地：declaration（两态+reason_code、code_type=64、操作码 TEXT[] 至多 1 个）+ manifest_sync（payload_hash、is_dirty）+ resource_dependency 改造（溯源列、depends_on 反向索引、auto_grant 列退役、maintain_source 收敛 MANIFEST/ADMIN_UI）"
  - "manifest full-sync 端点：三字段（schemaVersion/revision/dependencies）+ SyncResultResp 风格信封 + 幂等四条件（revision+hash+全 RESOLVED+非 dirty）；payload_hash 规范化契约（canonical bytes 排序含操作数组、SHA-256）与六类回归"
  - "DependencyCompiler：校验链（source 所有权→同 owner 蕴含目标资格→资源/操作码→自依赖→环已提交图优先；**ADMIN_UI 行豁免同 owner 第 2/3 步校验，仅做自依赖/环/操作码校验**——设计 §5.4 编译期 policy）+ 两态状态机 + 同键并集聚合（§5.5）+ declaration 写守卫（MANIFEST 行拒删改）+ 触发位单操作校验（多值 400）+ manifest 置脏联动"
  - "starter：JSON 清单 + Provider SPI + 单租户配置 + TenantRegistrationProvider 多租户 SPI + 两步编排（资源 full-sync→manifest，同 revision 重试）+ 启动本地最小校验"
  - "batch-sync 删除连同连带面：契约总册 §12.4/端点表行、DEPENDENCY:SYNC 操作位与 bootstrap 固定图授权行收口（含前端 perms.ts SYNC 项/路由 auths 派生/mock 角色矩阵同步删除）、autoGrant 字段链**全调用面**（后端 DTO + schema 列 + 前端 api 类型与表单载荷〔hook.ts buildCreate/UpdatePayload 恒携带〕与表格列/开关 + 契约 20048 行）前后端 SDK 同批锁步退役——严格 Jackson 下旧载荷回传已删字段即 400（T-ACCESS-036 先例），前端不同步则依赖页新建/编辑必断"
  - "manifest 端点（/api/access/integration/permission-manifest/full-sync）免 bootstrap 固定图 API 行/API 资源（经 Gateway M2M skipAuth 链，不进用户权限快照）——该判断写入契约注记"
  - "admin 依赖端点语义迁移（create/update/remove→declaration CRUD + 编译触发；list 增列 compile_status/reason/revision）；契约总册新章与错误码排号登记"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-19
---

# T-PERM-071 依赖声明层（035A）

## 背景

T-PERM-035 v1 设计定稿（2026-09-19，用户确认；docs/design/dependency-auto-grant.md adopted）——业务系统代码声明依赖关系，Access-Mesh 编译为实例级依赖图；resource_dependency 单写者（仅 DependencyCompiler），ADMIN_UI 也走 declaration。

## 范围

设计稿 §2–§5 全部：三张新表中的 declaration/manifest_sync + resource_dependency 改造、manifest 通道（消费 T-PERM-070 凭证）、编译器、registration starter。物化（035B）与观测（035C）不在本卡。

## 非目标 / 遗留

- 跨系统依赖、PENDING 家族、multi-bit 触发位、注解声明：设计稿 §14 演进项。
- 既有依赖种子行迁移：实测无对象（BootstrapGraphDefinition 无 resource_dependency 种子、全仓零 INSERT）——无迁移项。

## 验收对照

见 acceptance；`role_permission_auto_support` 表随 T-PERM-072（035B）落 DDL。
