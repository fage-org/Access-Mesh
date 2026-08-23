---
doc_type: task
id: T-ACCESS-018
title: 资源类型收敛（五组合并 + 双常量合一 + 前端权限串）
status: done
plan: docs/plans/product-vertical-slice-plan.md
domain: cross-service
design_refs:
  - docs/design/schema/access-service.sql
  - docs/design/access-service-architecture.md
  - docs/design/permission-center/api-contract.md
  - docs/design/services/admin-service-api-contract.md
  - docs/design/frontend/README.md
depends_on: [T-ORG-001]
blocks: [T-ACCESS-019, T-PERM-043]
acceptance:
  - "权威 DDL 种子按 T-ACCESS-016 定稿映射重编：USER/ROLE/MENU/SYSTEM_CONFIG/ORG 五组合并、ADMIN_SYNC_TASK 删除、user_type ADMIN_USER 更名 LOCAL_USER；type_value 终值在同 tenant_id+type_key 内全局唯一（处理 USER=6 既有占用与 ADMIN_* 退役段）"
  - "ResourceTypeCode 与 AdminResourceType 两套常量合一，生产代码按新类型码全量切换（已核实影响约 50 个 Java 文件 + 安全矩阵）"
  - "前端权限串全量切换（已核实约 23 处：user 页 ADMIN_USER/ADMIN_ORG、role 页、config 页、permission-query 临时口径等），与后端类型码一致；无兼容别名双写"
  - "权限矩阵/种子/错误码/文档（api-contract、admin-service-api-contract、frontend 页设计）同步更新；种子、后端、前端权限串与现行文档中不再出现已合并的重复资源类型"
  - "范围排除：@OperationLog.targetType 契约为小写物理表名/逻辑对象码（sys_user、abstract_role 等，见 OperationLogAspect.resolveTargetType 与覆盖测试 KNOWN_TABLE_NAMES 白名单），与资源类型码是两个命名空间，不在本任务修改；若个别日志字段实际存储资源类型码，逐项列名处理，禁止全局替换"
  - "扩展操作按 T-ACCESS-016 §13.3 bit 终值表落地：USER:ENABLE=32（重分配）、USER:RESET_PASSWORD=64、ORG 六码同名同 bit；ADMIN_ROLE:GRANT/REVOKE 零消费者删除不迁移（AdminOperationCode.GRANT/REVOKE 常量一并删除），uk_operation_permission_typed_bit 无冲突"
  - "保留业务键按 T-ACCESS-016 §4.3 终态切换：subject 侧 ADMIN_USER→LOCAL_USER；resource 侧取消类型级保留（LocalProjectionOwner 资源保留常量与 sync 入口 rejectReservedResourceType 调用删除），同批补齐资源 sync 所有权检查——UPSERT/DISABLE/DELETE 任一 mutation 分支在进入前对命中实体统一 rejectIfLocalResource（owner=access-service 即 20045；DELETE 分支现状直接软删命中实体，必须覆盖），并核实 resource-entity/full-sync 清理范围按 sync_metadata(entityKind=RESOURCE_ENTITY, sourceService, scopeKey) 界定、不触及本地投影行（本地投影不写 sync_metadata）；管理入口（resource-entity create/update）类型保留清单换值 {USER, ORG, MENU}；补外部同步命中本地投影行 20045 的负向测试（含 DELETE）"
  - "单测 + PostgreSQL Testcontainers 全绿；空库执行新 DDL 后类型种子自洽（无类型码冲突、无悬挂引用）"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-23
---

# T-ACCESS-018 资源类型收敛

## 背景

归并后残留两套资源类型命名空间：DDL 种子同时存在 USER=6/ADMIN_USER=16、ROLE=5/ADMIN_ROLE=18、MENU=1/ADMIN_MENU=19、SYSTEM_CONFIG=11/ADMIN_CONFIG=22、ADMIN_ORG=17、ADMIN_SYNC_TASK=28 等；代码中引擎枚举 `ResourceTypeCode`（12 码）与管理门禁 `AdminResourceType`（14 码）并存；前端同一工程混用两套权限串（user 页 ADMIN_*、role/config 页非 ADMIN_*）。已确认在 E2E 前全量收敛，验收基线只定一次。

## 范围

- 权威 DDL type_definition 种子终态 + type_value 重编（按 T-ACCESS-016 定稿）。
- 双常量类合一与全量代码切换、安全矩阵与门禁注解更新。
- 前端权限串、路由守卫与页面级 perms 定义切换。
- ADMIN_SYNC_TASK 类型与其无引用种子删除；`role_resource_permission` 等 authorization 表中 resource_type 列语义随类型码切换核对（空库重建，无数据迁移）。

## 当前口径

- 无存量生产数据（权威 DDL 重建模式），迁移成本是代码/种子/前端/文档，不是数据搬移。
- 不建兼容别名层、不做双写；旧类型码直接删除，码值不复用（沿用 ErrorCodeContractTest 退役清单模式登记）。
- ADMIN_FILE/ADMIN_NOTICE 等无重复对象的类型不改名，防止范围扩大。

## 非目标 / 遗留

- 不动业务域 CLASSIFY 分类模型（domain_config CLASSIFY 按 resourceTypeCode 关联，随类型码切换自然生效，不改三模式逻辑）。
- 主体 ID 统一（前置 T-ORG-001）与投影补齐（后续 T-ACCESS-019）不在本任务，三者独立提交；本任务在主体 ID 已统一后进行，类型码直接使用终值。

## 实施记录（2026-08-23）

按 T-ACCESS-016 §13 定稿一次性落地，全部验证通过（单测 678 + 容器 66 全绿、gateway 83 全绿、前端 vue-tsc 通过、全仓 install 通过）。

### 1. 权威 DDL 种子重编（docs/design/schema/access-service.sql）

- type_definition：36→31 行（user_type 3 + role_type 5 + **resource_type 23**）。五组管理类型从种子消失（ADMIN_USER=16/ADMIN_ORG=17/ADMIN_ROLE=18/ADMIN_MENU=19/ADMIN_CONFIG=22）、ADMIN_SYNC_TASK(28) 删除、新增 ORG=29；user_type `ADMIN_USER`→`LOCAL_USER`（值 3 不变，名「本地用户」）。
- operation_permission：139→117 条（CRUD 92 = 23×4 + 非预置扩展 13 + 运行时必需 12）。扩展操作按 §13.3 bit 终值表迁移：ADMIN_ORG 六码同名同 bit 迁 ORG(29)；ADMIN_USER:ENABLE bit16→**USER:ENABLE@32**（USER 下 16 被 MANAGE 占用）、RESET_PASSWORD@64 不变；ADMIN_ROLE:GRANT/REVOKE 删除不迁移。
- 表注释同步（sys_org/sys_user.user_type/resource_entity/abstract_user）。

### 2. 双常量合一（单一常量源 ResourceTypeCode）

- `ResourceTypeCode`（permission.enums）扩为终态 23 码（新增 MENU/BUTTON/DATA/ORG/ADMIN_DICT/ADMIN_DICT_DATA/ADMIN_OAUTH2_CLIENT/ADMIN_NOTICE/ADMIN_FILE/ADMIN_JOB/ADMIN_ORG_TREE_CONFIG），类 javadoc 指向 §13 注册表。
- **`AdminResourceType` 删除**（admin.security），26 个 main/test 文件切换：USER→USER、ORG→ORG、MENU→MENU、ROLE→ROLE、CONFIG→SYSTEM_CONFIG、DICT→ADMIN_DICT 等（值即终态码）。`AdminOperationCode.GRANT/REVOKE` 常量删除（零生产消费者）。
- `UserMenuQueryServiceImpl.EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES` 随之去重（原 ADMIN_ROLE+"ROLE" 双轨合一）并移除 SYNC_TASK。

### 3. 保留业务键终态（§4.3）

- subject 侧：`LocalProjectionOwner.SUBJECT_ADMIN_USER`→`SUBJECT_LOCAL_USER`（"LOCAL_USER"），全链路切换（投影写入/引擎门禁/菜单与角色查询/登录会话 `AuthServiceImpl` session subjectTypeCode/gateway 测试）。
- resource 侧：**取消类型级保留**——RESOURCE_ADMIN_* 常量与 `isReservedResourceType` 的 ADMIN 清单删除；`ResourceEntitySyncAppServiceImpl` 移除 sync/full-sync 入口的 rejectReservedResourceType，改为 `doSyncOneInternal` 在 existing 解析后统一 `rejectIfLocalResource`（20045，覆盖 UPSERT/DISABLE/DELETE 三分支与单条/批量两条路径）；full-sync 差异校准清理范围按 sync_metadata 界定的不变式加注。
- 管理入口：`isReservedResourceType` 语义换值 **{USER, ORG, MENU}**（引用 ResourceTypeCode 常量，无第三份字符串拷贝），仅 ResourceManageAppServiceImpl create/update 调用。

### 4. 前端权限串（frontend/src）

- user 页 `ORG_USER_PERMS`：ADMIN_ORG:*/ADMIN_USER:* 全量 → ORG:*/USER:*（含岗位精化码）；注释同步。
- grant 页 perms.ts、permission-query 页（subjectTypeCode ADMIN_USER→LOCAL_USER、选项标签）、api/auth.ts、api/permission-query.ts、user/index.vue、mock（login.ts/permission-query.ts）。
- vue-tsc --noEmit 通过。

### 5. 测试

- schema 双测试（H2/Postgres）：计数 31/117、必需操作清单 25 对（ORG 六码/USER 两码，删 ADMIN_ROLE 两对）、终值断言（LOCAL_USER=3、ORG=29、USER=6、USER:ENABLE@32）；**新增退役登记测试**（沿用 ErrorCodeContractTest 模式）：七个旧类型码不得再现 + 退役 type_value 段 16/17/18/19/22/28 不得被占用。
- `ResourceEntitySyncAppServiceTest` 新增三个负向测试：外部 sync UPSERT/DISABLE/DELETE 命中 owner=access-service 行 → BizException 20045 且未发生 update/softDelete（DELETE 分支覆盖）。
- 21 个测试文件适配（stub 值同步换终值：如 LocalProjectionDomainServiceImplTest ORG stub 16→29、USER 16→6；MenuWritePostgresIT 投影断言 resource_type 19→1；LocalProjectionBatchSqlIT 16→6）。
- 双层结果：单测 execution 678（0F/0E/2 预存 skip）+ testcontainers execution 66（0F/0E）BUILD SUCCESS；gateway 83 全绿。

### 6. 文档同步

- api-contract（保留语义段改为所有权保护 + subject 更名 + 示例 JSON）、admin-service-api-contract（§2 常量表换 ResourceTypeCode、权限矩阵/各章节权限串全量、§3.4 投影与保护段、ENABLE toggle 措辞）、access-service-architecture（§3/§4.3 现行行为行）、architecture、core-flows、overview、permission-center-v3.5-design（演进表注记）。
- frontend 页设计（permission-grant/permission-query/role-manage）、org-user-permission-contract（**v1.5 版本行**：全量切换 + 历史行保留原词）、default-org-tree-user-lifecycle。
- **T-ORG-001 runbook fixture SQL 重编**（USER=6/ORG=29/MENU=1 + 两入口同码注记）；待执行任务卡（T-ADMIN-021/T-PERM-033）切终态码；.claude/rules 示例切换。

### 7. 范围外确认

- @OperationLog.targetType 契约（小写物理表名）未动，grep 确认无日志字段存储资源类型码需逐项处理。
- ADMIN_DICT/ADMIN_OAUTH2_CLIENT/ADMIN_NOTICE/ADMIN_FILE/ADMIN_JOB/ADMIN_ORG_TREE_CONFIG 未改名（§13.1 不改名清单）；resource_dependency.maintain_source 的 'ADMIN_UI' 为维护来源枚举，与资源类型码不同命名空间，未动。
- grep 终扫：非归档、非历史注记的旧类型码引用清零（版本历史表行、§13 映射表、schema 测试退役清单断言、guard 退役名负断言按设计保留）。

## 评审修复记录（2026-08-23，第一轮 AI 评审）

核实结论：P1/P2×2/P3 四项全部属实，已修复；双层复验全绿（单测 683 = 原 678 + 新增 5；容器 66）。

- **P1（公共类型反向碰撞）**：USER/ORG/MENU 合并为公共类型后，本地投影写路径按 (type, code, default) 命中外部同步行时会无条件接管（`upsertResource` 改写 owner=access-service）/误删（单条与批量禁用、删除），且被接管行的外部 `sync_metadata.target_id` 悬挂，后续 full-sync 差异校准可反删已属本地的行。修复：`LocalProjectionGuard.rejectIfForeignResource`（反向防线，20045）；`upsertResource` 与 `batchUpsertAdminUsers` 命中非本地行 fail-closed 拒绝接管；单条/批量禁用与删除按 `isLocalOwner` 过滤跳过外部行（本地生命周期不阻断、不触碰外部行）。新增 4 个反向碰撞测试（单条 upsert 拒绝/单条 delete 跳过/批量 upsert 拒绝/批量 delete 过滤）；既有批量 fixture 补 owner=access-service。接管被阻断后 sync_metadata.target_id 不可能指向本地行，「full-sync 反删」链路在源头关闭。
- **P2（所有权检查前移）**：原实现 applyVersion（外部 sync_metadata 持久化）与 parent 解析先于所有权检查——命中本地行的 UPSERT 携带无效 parent 时以 dependencyMissing 正常返回并提交外部元数据，绕过 20045。修复：`doSyncOneInternal` 内类型解析 → existing 解析 → `rejectIfLocalResource` 全部前移到 applyVersion 与 parent 解析之前（所有权是安全边界，优先级最高）；单条与 full-sync 共用该路径。新增前置拒绝测试（断言 20045 且 applyVersion/resolveResourceId 从未被调用）；stale 用例补类型解析桩适配新顺序。
- **P2（现行文档残留旧口径）**：admin 契约 §2 常量表标题与 §8.3 OAuth2 门禁仍写已删除的 `AdminResourceType`；org-user 契约调用图/甲层表仍用旧常量名、§8 源码核对段仍列收敛前 12 码清单；v3.5 设计 §2.4 迁移表仍写「ADMIN_MENU 行 status=DISABLED」（实际种子已直接删除）；架构 §3 菜单段「ADMIN_MENU 投影」漏改。全部改为终态口径（`ResourceTypeCode`、23 码清单、「v3.5 迁移时 DISABLED、T-ACCESS-018 起种子直接删除」）。
- **P3（常量收敛）**：`AuthServiceImpl` 私有 `SUBJECT_TYPE_LOCAL_USER` 字面量改为复用 `LocalProjectionOwner.SUBJECT_LOCAL_USER`（单一事实源）；`ResourceTypeCode` 类 javadoc 退役清单笔误 `MENU`→`ADMIN_MENU`。
