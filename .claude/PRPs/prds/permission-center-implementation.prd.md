# Permission-Center 完整实现

## Problem Statement

Permission-center 是 AccessMesh 系统的核心权限引擎，当前仅有 4 个骨架文件（~5% 完成），零业务逻辑实现。18 张数据库表已有 SQL 定义，但无对应的 Entity、Mapper、Service、Controller、DTO 等任何代码层实现。

## Evidence

- 审计结果显示：permission-center/src/main/java/ 下仅有 4 个文件（Application + 1 个 stub Service + 1 个 stub Controller）
- `PermissionServiceImpl` 中 5 个方法全部返回硬编码值，包含 2 个 TODO
- `plan/permission_center_schema.sql` 已定义 18 张表
- `plan/DESIGN.md` 和 `plan/PERMISSION_CENTER_IMPL_DESIGN.md` 已完成详细设计

## Proposed Solution

按照 ECC 分阶段实施流程，从零构建 permission-center 的完整实现，覆盖 18 张表的全部 Entity/Mapper/DTO/Service/Controller 层，实现鉴权查询、权限授权、用户管理、角色管理、资源管理等核心业务模块，并落实 L1/L2 缓存、版本管理、冲突检测、条件评估等高级特性。

## Key Hypothesis

We believe 分阶段实施（基础设施→核心鉴权→管理界面→高级特性）将降低实施风险并提高代码质量。We'll know we're right when 所有阶段编译通过且各阶段可独立验证。

## What We're NOT Building

- 数据库表结构变更 — SQL 已定义，代码层适配即可
- 前端管理界面 — 仅实现后端 API
- RocketMQ 消息消费 — 先实现同步逻辑，消息队列后续扩展
- 多语言条件评估引擎 — 先实现 4 种预置条件类型（DATE_RANGE/TIME_RANGE/IP_WHITELIST/IP_BLACKLIST）

## Success Metrics

| Metric | Target | How Measured |
|--------|--------|--------------|
| 编译通过率 | 100% | `mvn clean compile -q` 无错误 |
| 代码覆盖率 | 阶段 1-3 每阶段编译通过 | Maven 编译输出 |
| API 端点实现率 | 设计文档中所有端点 100% 实现 | 对照 DESIGN.md 接口清单 |

## Open Questions

- [ ] 是否需要为 permission-center 创建独立的 application.yml（类似 admin-service）
- [ ] 条件评估是否需要支持自定义扩展条件类型

---

## Users & Context

**Primary User**
- **Who**: 后端开发工程师 / AI 代理
- **Current behavior**: 从零开始手动实现各层代码
- **Trigger**: permission-center 骨架已就绪，需完整实现
- **Success state**: 所有设计文档中的功能均已编码实现，编译通过

**Job to Be Done**
When 实施权限中心模块, I want to 按照设计文档分阶段实现所有代码层, so I can 得到一个可运行、可测试、符合项目规范的完整服务。

**Non-Users**
- 前端开发（不在此模块工作）
- 运维部署（仅消费最终服务）

---

## Solution Detail

### Core Capabilities (MoSCoW)

| Priority | Capability | Rationale |
|----------|------------|-----------|
| Must | 18 个 Entity + 18 个 Mapper | 数据库层基础 |
| Must | DTO/Req/Resp 全量创建 | 接口入出参 |
| Must | AuthService + 鉴权链路 | 核心鉴权三模式 |
| Must | PermissionGrantService | 权限授权管理 |
| Must | 5 个调度层 Service | 业务调度入口 |
| Must | 12 个 Controller | API 端点暴露 |
| Must | Enum 全量创建 | 枚举常量 |
| Should | 12 个 DomainService | 公共逻辑复用 |
| Should | L1/L2 缓存实现 | Caffeine + Redis |
| Should | 权限版本管理 | 缓存失效机制 |
| Should | 冲突检测（ROLE_MUTEX + PERM_MUTEX） | 权限安全 |
| Should | 条件评估引擎 | DATE_RANGE/TIME_RANGE/IP 黑白名单 |
| Could | 操作日志异步写入 | 性能优化 |
| Could | 权限变更 diff 记录 | 审计排查 |
| Won't | RocketMQ 集成 | 后续扩展 |

### MVP Scope

阶段 1（基础设施）：Entity + Mapper + Enum + DTO + Config → 达到可编译可运行状态

### Implementation Phases

| # | Phase | Description | Status | Parallel | Depends | PRP Plan |
|---|-------|-------------|--------|----------|---------|----------|
| 1 | 基础设施层 | 18 Entity + 18 Mapper + 枚举常量 + 配置类 | pending | - | - | - |
| 2 | DTO 层 | 全量 Req/Resp DTO + VO 对象 | pending | - | 1 | - |
| 3 | Domain Service | 12 个 DomainService（缓存、版本、冲突、条件等） | pending | - | 2 | - |
| 4 | 核心鉴权 | AuthService + 鉴权链路 + PermissionGrantService | pending | - | 3 | - |
| 5 | 管理模块 | UserManage + RoleManage + ResourceManage Service/Controller | pending | with 6 | 3 | - |
| 6 | 配置管理 | BizDomain + TypeDefinition + DomainConfig + ServiceConfig + SystemConfig | pending | with 5 | 3 | - |
| 7 | 高级特性 | PermissionCondition + PermissionConflict + 变更日志 | pending | - | 4, 5, 6 | - |

### Phase Details

**Phase 1: 基础设施层**
- **Goal**: 建立完整的数据库映射层和枚举系统
- **Scope**: 18 Entity（含完整 getter/setter）、18 Mapper 接口、Enum 全量（UserType, RoleType, ResourceType, ConflictType, ConfigType, GrantSource, ChangeSource, OperationType）、Config 类（Redis、Caffeine、MyBatis-Flex）
- **Success signal**: `mvn compile` 通过，TableDef 自动生成

**Phase 2: DTO 层**
- **Goal**: 建立全量 API 入出参 DTO
- **Scope**: ~20 Req DTO（AuthCheckReq, RoleCreateReq, RoleGrantReq, etc.）、~15 Resp DTO（AuthCheckResp, RoleResp, MenuTreeResp, etc.）、VO 对象（RolePermEntry, InterfaceSnapshot, RolePermSnapshot）
- **Success signal**: 编译通过，所有 DTO 使用 Java Record

**Phase 3: Domain Service**
- **Goal**: 实现可复用的领域逻辑
- **Scope**: UserRoleDomainService（有效角色解析+缓存）、PermissionVersionDomainService（版本管理）、PermissionChangeDomainService（变更日志）、OperationLogDomainService（操作日志）、PermCacheDomainService（L1/L2 缓存）、PermissionConflictDomainService（冲突检测）、PermissionConditionDomainService（条件评估）、ResourceDependencyDomainService（资源依赖）、ResourceEntityDomainService（资源树）、RolePermissionDomainService（角色权限）、AbstractUserDomainService、AbstractRoleDomainService
- **Success signal**: 编译通过，缓存逻辑正确

**Phase 4: 核心鉴权**
- **Goal**: 实现权限中心最核心的鉴权查询和授权管理
- **Scope**: AuthService/AuthServiceImpl（checkPermission 三模式鉴权链路）、PermissionGrantService/PermissionGrantServiceImpl（批量授权/撤销）、InternalPermissionController（替换现有 stub）
- **Success signal**: 鉴权链路完整（用户状态→角色解析→冲突过滤→授权匹配→条件评估→返回结果）

**Phase 5: 管理模块**
- **Goal**: 实现用户/角色/资源管理 API
- **Scope**: UserManageService/Impl（用户 CRUD+同步）、RoleManageService/Impl（角色 CRUD+树形）、ResourceManageService/Impl（资源 CRUD+树形+接口映射）、对应 3 个 Controller
- **Success signal**: 用户/角色/资源全生命周期 API 可用

**Phase 6: 配置管理**
- **Goal**: 实现系统配置类 API
- **Scope**: TypeDefinition、BizDomain、DomainConfig、ServiceConfig、SystemConfig 的 Service + Controller
- **Success signal**: 所有配置管理 API 可用

**Phase 7: 高级特性**
- **Goal**: 实现条件权限、冲突规则、变更日志等高级功能
- **Scope**: PermissionConditionController、PermissionConflictController、OperationLog 查询、PermissionChangeLog 查询
- **Success signal**: 高级特性 API 可用，全量编译通过

### Parallelism Notes

- Phase 5 和 Phase 6 可并行（依赖不同的 DomainService，互不干扰）
- Phase 1 和 Phase 2 可并行（DTO 不依赖 Entity，但 Entity 是运行时基础）
- Phase 3 必须串行在 Phase 1、2 之后（依赖 Entity 和 DTO）
- Phase 4 必须在 Phase 3 之后（依赖所有 DomainService）
- Phase 7 必须在 Phase 4、5、6 之后（最上层聚合）

---

## Decisions Log

| Decision | Choice | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| 实施方式 | 分 7 阶段渐进实现 | 一次性全部实现 | 降低风险，每阶段可独立验证 |
| DTO 风格 | Java Record | POJO + Lombok | 项目规范禁止 Lombok，Record 更简洁 |
| 缓存策略 | Caffeine L1 + Redis L2 | 仅 Redis | 双层缓存降低 Redis 压力，提高鉴权响应速度 |
| 权限同步 | 同步写入 | 异步 MQ | 当前阶段先用同步，MQ 后续扩展 |

---

## Research Summary

**Market Context**
- 权限中心采用 RBAC + 资源树 + 位运算操作的混合模型，在行业中属于较复杂的权限设计
- 相比简单 RBAC，额外支持了：分组角色、子权限/数据权限、条件权限、权限互斥、版本缓存失效
- 设计参考了 CASBIN、OPA 等开源权限引擎的思路

**Technical Context**
- MyBatis-Flex 已通过 APT 自动生成 TableDef，Entity 无需额外配置
- 已有完整的 SQL schema（18 表），无外键约束
- 项目依赖已配置：MyBatis-Flex、PostgreSQL、Redis、Caffeine、MapStruct、OpenFeign、Nacos
- Soft delete 约定：delete_flag=0 表示未删除，删除时填本行 id

---

*Generated: 2026-04-26*
*Status: DRAFT - needs validation*
