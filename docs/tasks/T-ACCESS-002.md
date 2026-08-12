---
doc_type: task
id: T-ACCESS-002
title: 建立 access_db 最终 DDL 并收敛持久层模型
status: in-progress
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#4-管理事实与权限投影
  - docs/design/access-service-architecture.md#5-数据库与共享表
  - docs/design/access-service-architecture.md#81-多实例任务协调
  - docs/design/schema/access-service.sql
depends_on:
  - T-ACCESS-001
blocks: []
acceptance:
  - "创建可从空 PostgreSQL 执行的 docs/design/schema/access-service.sql，目标为 access_db.public"
  - "access-service.sql 创建后、本任务进入 review 前，将该文件补入本卡 design_refs，确保 T-ACCESS-002 自身的 design_writeback 直接覆盖最终 DDL；当前不创建悬空引用"
  - "合并两份现有 DDL且无表名、索引名和种子冲突；sys_sync_task 不进入最终结构"
  - "system_config 与 operation_log 使用权威设计定义的合并结构，其他明确保留的表维持独立语义"
  - "本地权限投影具备 access-service 来源/所有权标识和稳定外部键约束"
  - "最终 DDL 预建 T-ACCESS-009 所需的任务执行持久化结构，覆盖稳定 execution key 及唯一约束、lease_owner、lease_until、状态和幂等标识"
  - "实体、基础 Mapper/XML 映射和表名引用与最终 DDL 一致，不包含旧数据库名；任务执行实体及基础字段映射由本任务创建，原子抢占/续租/条件完成 SQL 留给 T-ACCESS-009"
  - "自动化空库测试验证表、索引、约束与种子数据；不新增历史数据迁移脚本或 migration 框架"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-12
---

# T-ACCESS-002 建立 access_db 最终 DDL 并收敛持久层模型

## 背景

项目无部署和历史数据，本任务直接产出最终空库结构，为后续同库事务提供唯一事实基础。

## 范围

- 合并、校对和执行最终 DDL。
- 预建多实例任务租约与幂等执行所需的完整持久化结构、实体及基础 Mapper/XML 映射；原子并发 SQL 与运行逻辑由 T-ACCESS-009 实现。
- 调整持久层对象、索引、约束和种子引用。
- 建立空库结构测试。

## 非目标

- 不编写 `admin_db`/`perm_db` 到 `access_db` 的迁移程序。
- 不引入 Flyway 或 Liquibase。

## 完成记录

### 实施摘要（2026-08-12）

**决策确认**（用户决策，逐项确认）：

| # | 决策点 | 结论 |
|---|---|---|
| 1 | type_definition 种子缺失修复 | 完整系统种子（user_type 2 + role_type 5 + resource_type 28 行，type_value 权威数值与 RoleType/ResourceType 枚举一致） |
| 2 | system_config 合并结构 | 字段超集（description/config_name/remark/is_system）+ 9 条种子键名保持现状（命名空间约定约束新增键） |
| 3 | 空库测试载体 | H2 适配执行 + Testcontainers 双轨 |
| 4 | 本地投影所有权 | abstract_user/abstract_role/user_role 三表加可空 `owner_service_code`（无默认值），不复制 maintain_source，resource_entity 复用现有字段 |
| 5 | 任务执行表 | `sys_task_execution`（execution_key 唯一约束 + lease_owner/lease_until/status/attempt_count） |
| 6 | 旧 DDL 处置 | 本任务验收后 4 个旧文件（admin-service.sql/permission-center.sql/两个 seed）标记 superseded |

**产出**：

1. **`docs/design/schema/access-service.sql`（权威最终 DDL，33 张表）**
   - admin 域 14 张（sys_sync_task 退役删除）+ permission 域 16 张 + 合并表 2 张（system_config/operation_log）+ 基础设施 1 张（sys_task_execution）
   - `system_config` 超集字段合并（config_key/config_value/description/config_name/remark/is_system），种子 9 条键名不变
   - `operation_log` 超集字段合并（user_id/username/request_url/request_body/response_code/cost_time + operator_id/operator_name，target_id 字符串化 VARCHAR(64)，request_body 限长 4000），索引合并去重（idx_operation_log_user 承接原 idx_audit_log_user）
   - `abstract_user`/`abstract_role`/`user_role` 增加可空 `owner_service_code`（无默认值）
   - `type_definition` 系统种子 35 行：user_type USER=1/SERVICE=2；role_type ORG=1/POSITION=2/PERSONAL=3/GROUP_ROLE=5/BASIC_ROLE=6；resource_type MENU=1/BUTTON=2/API=3/DATA=4（枚举权威）+ ROLE=5…DEPENDENCY=15（ResourceTypeCode 顺序）+ ADMIN_USER=16…ADMIN_SYNC_TASK=28（AdminResourceType 顺序）
   - `operation_permission` 非预置操作码种子 17 行（原 seed-admin-operations 16 + seed-perm-operations 1，二进制位分配不变）
   - `sys_task_execution` 预建（execution_key 唯一约束，T-ACCESS-009 原子 SQL 扩展）
2. **持久层收敛**
   - 删除 admin 域 `SysConfig`/`SysAuditLog` 实体、`SysConfigMapper`/`SysAuditLogMapper` 接口及 XML（4 语句并入 SystemConfigMapper.xml、paginateByTenantId 并入 OperationLogMapper.xml）
   - `SystemConfig`/`OperationLog` 实体迁入 `access.infrastructure` 跨域共享层（entity + mapper + XML namespace/resultType 同步），admin 与 permission 域均可引用，架构边界测试通过
   - `SystemConfig` 加 configName/remark/isSystem；`OperationLog` targetId Long→String + 6 个新字段
   - `AuditDomainService.asyncRecordLog` targetId Long→String；`OperationLogAspect` 删除 resolveTargetIdAsLong 强转；`OperationLogResp`/`LogQueryAppServiceImpl` targetId 同步
   - `AbstractUser`/`AbstractRole`/`UserRole` 加 ownerServiceCode；三个 Sync 实现 insert 分支按 `sourceService=='admin-service'` 写入 `'access-service'`（本地投影），外部同步/人工维护保持 NULL
   - 新建 `SysTaskExecution` 实体 + `SysTaskExecutionMapper` + XML 基础映射（infrastructure 包，MapperScan 扩展）
   - `AccessServiceApplication` @MapperScan 增加 infrastructure.mapper
   - 代码中无 `admin_db`/`perm_db` 数据库名字面量（核实结论）
3. **空库自动化测试**
   - `AccessServiceSchemaH2Test`（H2 PostgreSQL 模式适配执行，10 用例）：33 表、sys_sync_task 不存在、4 组种子计数与 type_value 权威数值、owner_service_code 列、合并表超集字段、uk_system_config 唯一约束行为、role_resource_permission CHECK 行为、sys_task_execution 唯一约束
   - `AccessServiceSchemaPostgresTest`（Testcontainers 原样 DDL，9 用例）：含 H2 无法表达的软删部分唯一索引语义（uk_operation_permission_global 只约束 resource_type IS NULL 行）、COALESCE 索引列、NULLS NOT DISTINCT；Docker 可用时自动执行（本机无 Docker 跳过）
   - H2 适配规则：TIMESTAMPTZ→TIMESTAMP WITH TIME ZONE、去 USING GIN、数组列 DEFAULT ARRAY[]、位运算→BITAND、部分唯一索引仅含 delete_flag 谓词时去 WHERE 保留（业务谓词删除）、ON CONFLICT 尾缀删除

**验证结果**：
- `mvn clean compile` + `test-compile` 全通过
- 全量 347 测试 0 失败 7 跳过（5 Testcontainers Docker + 2 OperatorContext mock）
- Context 启动测试 2/2、架构边界测试 3/3（合并表实体/Mapper 迁入 infrastructure 后 admin↔permission 无横向依赖）
- H2 空库测试 10/10 通过

**设计回写**：
- `access-service-architecture.md` §4.2/§5.1/§5.2/§8.1 已由实现落地：owner_service_code 语义、权威 DDL 文件、合并表结构、sys_task_execution 预建；§5.1 的"旧 DDL 验收后转为 superseded"已执行（4 个旧文件头部标记）
- 旧文件 superseded 影响登记至看板"设计变更待核对"

### 已知限制与后续
- H2 无法表达软删部分唯一索引（delete_flag 谓词）、NULLS NOT DISTINCT、COALESCE 索引列与 JSONB 路径索引，相关语义由 Testcontainers PostgreSQL 测试（Docker 环境）与应用层保证
- 全局域（biz_domain global=true）由管理 API 创建，不在 DDL 预置（现状一致，空库验收 T-ACCESS-011 观察）
- type_value 数值为权威定义（归档文档 SERVICE=10 历史数值作废重排，空库无存量数据影响）
- operation_log 模块标识 ADMIN/PERMISSION/ACCESS 统一与配置键命名空间（admin.*）迁移留待 T-ACCESS-007
