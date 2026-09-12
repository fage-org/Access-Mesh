---
doc_type: task
id: T-ACCESS-002
title: 建立 access_db 最终 DDL 并收敛持久层模型
status: done
plan: docs/archive/2026-08-22/access-service-merge-plan.md
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
  - "合并两份现有 DDL且无表名、索引名和种子冲突；sys_sync_task 以过渡表保留至 T-ACCESS-005（同步链路代码仍在写入，过渡表删除与代码删除原子完成），不在 T-ACCESS-005 之后继续存在"
  - "system_config 与 operation_log 使用权威设计定义的合并结构，其他明确保留的表维持独立语义"
  - "本地权限投影具备 access-service 来源/所有权标识和稳定外部键约束"
  - "最终 DDL 预建 T-ACCESS-009 所需的任务执行持久化结构，覆盖稳定 execution key 及唯一约束、lease_owner、lease_until、状态和幂等标识"
  - "实体、基础 Mapper/XML 映射和表名引用与最终 DDL 一致，不包含旧数据库名；任务执行实体及基础字段映射由本任务创建，原子抢占/续租/条件完成 SQL 留给 T-ACCESS-009"
  - "自动化空库测试验证表、索引、约束与种子数据；不新增历史数据迁移脚本或 migration 框架"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-13
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
| 1 | type_definition 种子缺失修复 | 完整系统种子（user_type 3 + role_type 5 + resource_type 28 行，type_value 权威数值与 RoleType/ResourceType 枚举一致） |
| 2 | system_config 合并结构 | 字段超集（description/config_name/remark/is_system）+ 9 条种子键名保持现状（命名空间约定约束新增键） |
| 3 | 空库测试载体 | H2 适配执行 + Testcontainers 双轨 |
| 4 | 本地投影所有权 | abstract_user/abstract_role/user_role 三表加可空 `owner_service_code`（无默认值），不复制 maintain_source，resource_entity 复用现有字段 |
| 5 | 任务执行表 | `sys_task_execution`（execution_key 唯一约束 + lease_owner/lease_until/status/attempt_count） |
| 6 | 旧 DDL 处置 | 本任务验收后 4 个旧文件（admin-service.sql/permission-center.sql/两个 seed）标记 superseded |

**产出**：

1. **`docs/design/schema/access-service.sql`（权威最终 DDL，34 张表）**
   - admin 域 15 张（sys_sync_task 以过渡表保留，T-ACCESS-005 退役时与同步代码原子删除）+ permission 域 16 张 + 合并表 2 张（system_config/operation_log）+ 基础设施 1 张（sys_task_execution）
   - `system_config` 超集字段合并（config_key/config_value/description/config_name/remark/is_system），种子 9 条键名不变
   - `operation_log` 超集字段合并（user_id/username/request_url/request_body/response_code/cost_time + operator_id/operator_name，target_id 字符串化 VARCHAR(256) 覆盖业务键上限，request_body 限长 4000），索引合并去重（idx_operation_log_user 承接原 idx_audit_log_user）
   - `abstract_user`/`abstract_role`/`user_role` 增加可空 `owner_service_code`（无默认值）
   - `type_definition` 系统种子 36 行：user_type USER=1/SERVICE=2/ADMIN_USER=3（本地管理用户主体类型，SyncTaskBuilder 同步 abstract_user 必需）；role_type ORG=1/POSITION=2/PERSONAL=3/GROUP_ROLE=5/BASIC_ROLE=6；resource_type MENU=1/BUTTON=2/API=3/DATA=4（枚举权威）+ ROLE=5…DEPENDENCY=15（ResourceTypeCode 顺序）+ ADMIN_USER=16…ADMIN_SYNC_TASK=28（AdminResourceType 顺序）
   - `operation_permission` 种子 139 行：28 个静态 resource_type 各预置 CRUD 四操作（112 行，CREATE bit=1/VIEW bit=2/UPDATE bit=4 继承2/DELETE bit=8 继承2；DDL 直接种入的类型不触发运行时生成，必须在初始化阶段种入）+ 非预置扩展操作 15 行（原 seed-admin-operations 16 + seed-perm-operations 1 = 17 条，其中 ADMIN_ORG:VIEW/ADMIN_USER:VIEW 两条与 CRUD 预置 VIEW 完全重复（同 code/bit/mask），合并时消除；其余 bit 从 16 起与 CRUD 不冲突）+ 权限中心运行时必需操作 12 行（USER:MANAGE、ROLE:ASSIGN/REVOKE、RESOURCE:MANAGE、SERVICE:MANAGE/MANAGE_API_MAPPING/SYNC_INTERFACE、TYPE_DEFINITION:MANAGE、SYSTEM_CONFIG:MANAGE、OPERATION:MANAGE、DEPENDENCY:SYNC、API:ACCESS；代码实际校验，缺失时权限引擎 fail-closed）
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
   - `AccessServiceSchemaH2Test`（H2 PostgreSQL 模式适配执行，12 用例）：34 表、sys_sync_task 过渡表存在、4 组种子计数与 type_value 权威数值、owner_service_code 列、合并表超集字段、uk_system_config 唯一约束行为、role_resource_permission CHECK 行为、sys_task_execution 唯一约束、运行时必需操作对完整性
   - `AccessServiceSchemaPostgresTest`（Testcontainers 原样 DDL，12 用例）：含 H2 无法表达的软删部分唯一索引语义（uk_operation_permission_global 只约束 resource_type IS NULL 行）、COALESCE 索引列、NULLS NOT DISTINCT、JSONB String 参数绑定往返（PreparedStatement#setString）、运行时必需操作对完整性；每测试独立事务回滚防数据污染；Docker 可用时自动执行（本机无 Docker 跳过）
   - H2 适配规则：TIMESTAMPTZ→TIMESTAMP WITH TIME ZONE、去 USING GIN、数组列 DEFAULT ARRAY[]、位运算→BITAND、部分唯一索引仅含 delete_flag 谓词时去 WHERE 保留（业务谓词删除）、ON CONFLICT 尾缀删除

**验证结果**：
- `mvn clean compile` + `test-compile` 全通过
- 全量 372 测试 0 失败 19 跳过（5 Testcontainers Docker + 2 OperatorContext mock + 12 Testcontainers Postgres）
- Context 启动测试 2/2、架构边界测试 3/3（合并表实体/Mapper 迁入 infrastructure 后 admin↔permission 无横向依赖）
- H2 空库测试 12/12 通过

**设计回写**：
- `access-service-architecture.md` §4.2/§5.1/§5.2/§8.1 已由实现落地：owner_service_code 语义、权威 DDL 文件、合并表结构、sys_task_execution 预建；§5.1 的"旧 DDL 验收后转为 superseded"已执行（4 个旧文件头部标记）
- 旧文件 superseded 影响登记至看板"设计变更待核对"

### 评审修复（2026-08-12）

AI 评审 5 项问题处理结果（用户决策确认）：

| # | 评审问题 | 结论 | 处理 |
|---|---|---|---|
| 1 | sys_sync_task 表已删但同步链路代码仍写（admin 写操作回滚） | 成立 | 用户决策：保留过渡表。access-service.sql 恢复 sys_sync_task 定义并标注 `[T-ACCESS-005 退役]`（代码删除与过渡表删除原子完成）；架构 §4.3 回写；表数 33→34，测试存在断言反转 |
| 2 | 缺 user_type=ADMIN_USER 主体类型种子（SyncTaskBuilder 以 ADMIN_USER 同步 abstract_user，空库解析失败） | 成立 | 用户决策：补 `ADMIN_USER=3`（USER=1 外部人员/SERVICE=2 外部服务/ADMIN_USER=3 本地管理用户）；种子 35→36 行，测试补数值断言 |
| 3 | 静态资源类型无 CRUD 操作（DDL 直插类型不触发运行时生成，且应用无生成逻辑，多数类型 fail-closed） | 成立 | 用户决策：为 28 个静态 resource_type 种入 CRUD 四操作（INSERT...SELECT CROSS JOIN VALUES，bit 1/2/4/8，与扩展码 16 起不冲突）；种子 17→127 行（扩展码中 2 条冗余 VIEW 与 CRUD 合并消除，复审确认）；H2/Postgres 测试断言每类型恰好 4 条 |
| 4 | JSONB 列与实体 String 映射（PGJDBC stringtype=VARCHAR 真实 PG 写入报 42804，全项目约 10 个 JSONB 列） | 成立 | 用户决策：application.yml 数据源 URL 加 `stringtype=unspecified`（服务端按目标列推断，一处配置覆盖全部 JSONB 列）；Testcontainers 测试连接同步加参数并补 system_config JSONB 往返用例（Docker 环境生效） |
| 5 | operation_log.target_id VARCHAR(64) 不足（configKey 128/roleExternalId 256 超长审计写入失败被吞） | 成立 | 用户决策：扩至 VARCHAR(256)；H2 测试补长度断言（character_maximum_length=256） |

**评审修复验证**：H2 空库测试 11/11 通过；全量回归（见下文验证结果）。

### 复审修复（2026-08-13）

AI 复审 4 项问题处理结果（冗余 VIEW 处置经用户澄清与决策）：

| # | 复审问题 | 结论 | 处理 |
|---|---|---|---|
| 1 | Postgres 测试共享自动提交连接：测试插入 2 条临时 operation 恰好把种子计数补成 129、JSONB 测试把 system_config 增至 10 条，Docker 环境必然失败 | 成立 | 每测试独立事务（@BeforeEach setAutoCommit(false) + @AfterEach rollback）防污染；种子断言修正为实际生效 127 条（CRUD 112 + 扩展 15）；经用户澄清确认：扩展码 ADMIN_ORG:VIEW/ADMIN_USER:VIEW 与 CRUD 预置 VIEW 在同一 resource_type 上完全重复（同 code/bit/mask），从种子定义中删除（合并消除），PG/H2 断言统一 127 |
| 2 | JSONB 测试用 Statement 拼接字面量不经过 PGJDBC setString 路径，不能证明 stringtype=unspecified 修复 | 成立 | 改为 PreparedStatement#setString 参数绑定（模拟实体 String 经 MyBatis 绑定 JSONB 列的真实路径） |
| 3 | 任务卡验收项仍要求 sys_sync_task 不进入最终结构；完成记录残留 33 表/VARCHAR(64)/10 用例/129 行等旧结论 | 成立 | acceptance 第 3 条修订为过渡表语义（保留至 T-ACCESS-005 原子删除）；完成记录全部数据修正（34 表、target_id 256、H2 11 用例、Postgres 11 用例、种子 127、全量 369 测试） |
| 4 | abstract_user.user_type 注释未含新增主体类型 | 成立 | 注释更新为 USER(1)/SERVICE(2)/ADMIN_USER(3) |

**复审修复验证**：H2 空库测试 11/11 通过；全量回归（见下文验证结果）。

### 评审修复补充（2026-08-13，运行完整性）

| # | 评审问题 | 结论 | 处理 |
|---|---|---|---|
| 1 | 空库缺少运行时实际使用的操作码（USER:MANAGE、ROLE:ASSIGN/REVOKE、RESOURCE:MANAGE、SERVICE:MANAGE/MANAGE_API_MAPPING/SYNC_INTERFACE、TYPE_DEFINITION:MANAGE、SYSTEM_CONFIG:MANAGE、OPERATION:MANAGE、DEPENDENCY:SYNC），解析不到即 fail-closed | 成立 | 全量扫描代码调用点，非 CRUD 必需操作对共 27 对（Admin 扩展码 15 + 权限中心运行时必需 12 缺失）；补齐 12 条权限中心运行时必需操作种子（评审 11 + 额外核实 API:ACCESS——接口鉴权 forInterfaceCheck 依赖，同样 fail-closed）；种子 127→139；新增"运行时必需操作对完整性"断言（H2/Postgres 双轨，27 对非 CRUD 清单） |
| 2 | 本地 resource_entity 投影未写入所有权标记（insert 分支只设 maintainSource/syncKey，未设 ownerServiceCode） | 成立 | 与另三个 Sync 实现对齐：sourceService==admin-service 时写入 'access-service'，外部同步保持 NULL；补 insert 两分支回归测试（本地投影标记/外部来源 NULL） |
| 3 | 两处文案残留旧口径（user_type 2、重复 VIEW 由 ON CONFLICT 跳过） | 成立 | 任务卡 user_type 2→3；Postgres 测试 DisplayName/失败消息去除 ON CONFLICT 表述 |

**评审修复补充验证**：H2 空库测试 12/12 通过；全量回归（见下文验证结果）。

### 已知限制与后续
- H2 无法表达软删部分唯一索引（delete_flag 谓词）、NULLS NOT DISTINCT、COALESCE 索引列与 JSONB 路径索引，相关语义由 Testcontainers PostgreSQL 测试（Docker 环境）与应用层保证
- 全局域（biz_domain global=true）由管理 API 创建，不在 DDL 预置（现状一致，空库验收 T-ACCESS-011 观察）
- type_value 数值为权威定义（归档文档 SERVICE=10 历史数值作废重排，空库无存量数据影响）
- operation_log 模块标识 ADMIN/PERMISSION/ACCESS 统一与配置键命名空间（admin.*）迁移留待 T-ACCESS-007
- sys_sync_task 过渡表删除与同步链路代码删除（T-ACCESS-005）原子绑定，见架构 §4.3
