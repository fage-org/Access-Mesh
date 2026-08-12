---
doc_type: task
id: T-ACCESS-001
title: 建立 access-service 工程骨架并物理归并源码
status: review
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#2-目标工程与部署单元
  - docs/design/access-service-architecture.md#3-模块边界
  - docs/design/access-service-architecture.md#9-api-sdk-与生态切换
  - docs/design/project-rules.md
depends_on: []
blocks: []
acceptance:
  - "新增单一 Maven 模块 access-service 与 AccessServiceApplication，编译基线统一为 Java 21"
  - "admin 与 permission 源码迁入 cn.ac.fage.accessmesh.access.admin / permission，跨域入口位于 access.application"
  - "AdminErrorCode 与 PermissionErrorCode 随领域迁入后继续分别维护原 1xxxx/2xxxx 码值，不合并、重编号或创建 4xxxx 错误码枚举"
  - "重名 Controller、Service、配置类和 Spring Bean 使用明确域前缀消除冲突，不启用 Bean 覆盖"
  - "access-service 模块可完成 Spring Context 启动和模块级测试，迁移过程不改变 HTTP 契约"
  - "补充架构边界测试骨架，能够在后续任务中扩展依赖白名单"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-12
---

# T-ACCESS-001 建立 access-service 工程骨架并物理归并源码

## 背景

两个服务必须先进入同一构建与运行容器，后续单库事务、安全上下文和缓存收敛才有稳定落点。

## 范围

- 建立 Maven、启动类、包结构和测试目录。
- 机械迁移两侧源码与资源，修复包名、扫描范围和简单 Bean 冲突。
- 建立 Java 21 编译基线和最小 Context 验证。

## 非目标

- 本任务不改变领域行为，不删除内部同步链路，不完成最终部署切换。

## 完成记录

### 实施摘要（2026-08-12）

**决策确认**（5 项，全部选 A）：
1. Java 21 全局升级（根 pom `java.version` 17→21）
2. `perm-entity` 模块合并进 access-service，删除独立模块
3. 旧模块（admin-service、permission-center）从根 pom 移除并删除目录
4. `TenantContextHolder` 和 `MybatisFlexTenantConfig` 统一到 `access.infrastructure`
5. 4 对重名 Controller 使用域前缀重命名（AdminXxxController / PermXxxController）

**工程变更**：
- 新增 `access-service` Maven 模块，`AccessServiceApplication` 位于 `cn.ac.fage.accessmesh.access`
- admin 源码迁入 `cn.ac.fage.accessmesh.access.admin`（221 main + 23 test = 244 文件）
- permission 源码迁入 `cn.ac.fage.accessmesh.access.permission`（319 main + 47 test = 366 文件）
- perm-entity 实体迁入 `cn.ac.fage.accessmesh.access.permission.entity`（18 文件）
- 35 个 mapper XML 迁入并更新 namespace
- 合并 `bootstrap.yml`（端口 9100、access_db、Redis DB 0、双域 type-aliases-package）
- 删除 `AdminServiceApplication` 和 `PermissionCenterApplication`，仅保留 `AccessServiceApplication`
- 旧模块 admin-service、permission-center、perm-entity 从根 pom 移除并删除目录

**冲突消除**：
- `TenantContextHolder` → 统一到 `access.infrastructure.TenantContextHolder`
- `MybatisFlexTenantConfig` → 统一到 `access.infrastructure.MybatisFlexTenantConfig`
- `AuthController` → `AdminAuthController` / `PermAuthController`
- `RoleController` → `AdminRoleController` / `PermRoleController`
- `UserController` → `AdminUserController` / `PermUserController`
- `UserRoleController` → `AdminUserRoleController` / `PermUserRoleController`
- 修复 `RoleResourcePermissionMapper.xml` 中 `&` 未转义为 `&amp;` 的 XML 语法问题

**验证结果**：
- `mvn compile`（全 reactor）BUILD SUCCESS — Java 21，557 源文件
- `mvn test-compile` BUILD SUCCESS — 72 测试文件
- 架构边界测试 3/3 通过（admin↔permission 无横向依赖、切片无循环）
- 启动类验证测试 3/3 通过（@SpringBootApplication/@MapperScan 配置正确、无重复启动类）
- `AdminErrorCode`（1xxxx）与 `PermissionErrorCode`（2xxxx）分别保留原包路径和码值
- HTTP 路由未改变（admin: /auth、/role、/user 等；permission: /api/perm/*）

**设计回写**：
- `access-service-architecture.md` §2/§3/§9 无需修改（实现与设计一致）
- `project-rules.md` §1.2 错误码分段无需修改（AdminErrorCode/PermissionErrorCode 未合并）
- `architecture.md` 旧双服务拓扑标注已存在（待 T-ACCESS-012 全量回写）
