---
doc_type: design
title: 权限依赖声明与自动授权（T-PERM-035）v1 设计
status: archived
superseded_by: docs/design/dependency-auto-grant.md
domain: access-service
last_reviewed: 2026-09-19
---

> **历史材料**：已由[当前简化设计](../../design/dependency-auto-grant.md)取代。下文状态、候选和任务描述均为形成时点的快照，不作为当前实施依据。

# 权限依赖声明与自动授权（T-PERM-035）v1 设计

> **状态口径**：`adopted`（2026-09-19 用户确认定稿——claude 首轮+codex sol 三轮外评 45 项发现全处置+两轮过度设计裁剪；定案登记 decision-registry 同日两行）。实现拆分任务卡 **T-PERM-070**（前置·服务认证）/ **071**（声明层 035A）/ **072**（物化 035B）/ **073**（观测 035C）。
>
> **决策来源**：外部设计稿三轮讨论（实例级声明 / 写时物化 / 热路径零图计算主架构）+ 现状代码级核实（schema / 同步通道 / 引擎 / 任务卡）+ 过度设计重评（2026-09-19，裁剪依据=平台当前仅 example-service 一个演示接入方、无跨系统与大规模角色场景）。
>
> **2026-09-19 修订**：①服务凭证升格**公共服务认证模块**（独立稿 [service-authentication.md](../../design/service-authentication.md) + 独立前置任务卡）；②Manifest 清单格式定案 **JSON**（无缩进敏感、与服务端请求 DTO 同构）；③§6.7 补触发时机与中断语义（全同步=中断即整体回滚，无断点续跑需求）；④claude 外评处置（P0/P1=0、P2×11+P3×7 逐条代码级核实全采纳）：触发面补⑦定义面变更、锁序改全序三段+入口矩阵、同键多声明**并集聚合**（用户拍板，§5.5）、闭包批量装载禁 N+1、multi-bit 触发按资源聚合、清单文件删 serviceCode/操作码复数化、幂等短路绑定「全 RESOLVED」、表清单三处对齐、§7③ 20055 歧义澄清 + declaration 写守卫；⑤codex sol 三轮复评处置（P1×14+P2×10 详见 registry 同日注记）；⑥**过度设计重评三项裁剪（用户拍板）**：触发位 v1 限单操作（§14.7）、TLS 信任域模型、support 二遍追踪 035B 一步到位。

---

## 1. 概述与范围

### 1.1 目标

业务系统用**代码声明**自己业务权限之间的依赖关系（"看报表 A 需要能读数据集 B"），Access-Mesh 在**写路径**编译声明并物化自动授权（AUTO_DEP）；管理员只操作显式授权（MANUAL），鉴权热路径零图计算。

四条核心原则：

```text
① 依赖是资源实例之间的安全关系（不是类型规则）；
② 依赖数据属于业务代码（不属于管理员配置）；
③ 依赖图计算只发生在写路径（不进 auth/check 热路径）；
④ AUTO_DEP 是系统物化结果（只读、canGrant=false、管理员只碰 MANUAL）。
```

### 1.2 v1 非目标（全部列入 §14 演进方向，设计不丢）

| 非目标 | 一句话边界 |
|---|---|
| 跨系统依赖（export 双边协议 + provenance 全链） | v1 依赖边必须连接**同一 owner 服务**的资源；跨系统整块进 §14.1 |
| 类型级依赖（TypeDependencyRule） | 依赖边只挂实例；scope_all 种子不参与闭包（含 AUTHORITY_ROOT） |
| 条件路径级追踪 | 条件直传取最宽路径（§6.4），不做逐路径条件评估 |
| API / 菜单 visibility AUTO_DEP | API 权限由操作权限关联派生（2026-09-09 定案，T-PERM-054）；菜单可见走 projection |
| 规模熔断与异步重建 | v1 依赖/授权变更一律同步强一致 rebuild；不对称熔断+队列进 §14.4 |
| 跨租户依赖、deny 依赖、负权限依赖 | 不做 |

### 1.3 与既有定案的关系

- 2026-08-28「暂缓能力维持暂缓（auto-grant 写入口 20048 拒 true）」：维持至 035A 落地时随 `auto_grant` 列退役一并收口（§3.4）。
- 2026-08-30「DEPENDENCY 写门禁一律类型级」：管理面 declaration 端点（原 create/update/remove 语义迁移）维持 DEPENDENCY:CREATE/UPDATE/DELETE 类型级门禁；manifest 通道改为**服务凭证认证**（§9），不走 DEPENDENCY:SYNC 人权限门禁。
- `resource_dependency.maintain_source` 已预留 `MANIFEST`/`SDK_SCAN` 枚举值（schema 注释），本设计正好承接并收敛（§3.4）。
- T-PERM-048 条件双轨制 INLINE「1:1 属于授权记录不可共享」：随本稿条件直传**修订为 1 属主 + N 系统派生引用**，回收时序移 rebuild 后（2026-09-19 拍板，见 §6.4；registry T-PERM-048 行已加部分修订注记）。

---

## 2. 总体架构

```text
对接业务系统（如 example-service / 未来 report-service）
   │  声明方式二选一：JSON 清单（静态资源） / Provider SPI（业务库动态实例）
   ▼
PermissionManifest（serviceCode + revision + schemaVersion + dependencies[]）
   │  perm-registration-spring-boot-starter 启动时编排两步同步
   │    步骤① POST /api/access/resource-entity/full-sync（现有通道，复用全部门禁与信封）
   │    步骤② POST /api/access/integration/permission-manifest/full-sync（新通道）
   │    两步携带同一 revision；步骤②失败同 revision 重试（步骤①幂等）
   ▼
┌─────────────────────────────── access-service（写路径）───────────────────────────────┐
│  permission_dependency_declaration   ←── 源事实（Source of Truth，业务键四元组）        │
│           │ DependencyCompiler（同 owner 校验 / 操作码解析 / 环检测·已提交图优先）      │
│           ▼                                                                          │
│  resource_dependency                 ←── 编译产物（Compiled Projection，单写者）        │
│           │ AutoGrantMaterializer（rebuildRole：种子闭包→desired→diff）                │
│           ▼                                                                          │
│  role_resource_permission (AUTO_DEP) + role_permission_auto_support（推导溯源）        │
└──────────────────────────────────────────────────────────────────────────────────────┘
   │
   ▼
PermQueryEngine（零改动：三条鉴权 SQL 均无 grant_source 谓词，AUTO_DEP 行天然被消费）
```

### 2.1 三层数据分层（表职责焊死）

| 层 | 表 | 职责 | 写者 |
|---|---|---|---|
| 声明（SoT） | `permission_dependency_declaration` | 业务系统/管理员声明的源事实（业务键） | manifest 通道、ADMIN_UI 通道 |
| 编译产物 | `resource_dependency` | 实例级依赖图（解析后的实体 id + 操作位） | **仅 DependencyCompiler**（单写者） |
| 物化 + 溯源 | `role_resource_permission`(AUTO_DEP 行) + `role_permission_auto_support` | 有效自动授权 + 每条授权的推导路径 | 仅 AutoGrantMaterializer |

`service_manifest_sync`（manifest 摄入状态）为支撑表；`service_credential`（服务凭证）归属**公共服务认证模块**（[service-authentication.md](../../design/service-authentication.md)，本稿 §9 消费）。

### 2.2 v1 依赖图形态：每服务独立岛

- **边限同 owner**：一条依赖边的 source 与 target 资源必须属于同一 owner 服务（类型级所有权派生，T-PERM-052：`type_definition.extra.managedMode=SYNC + syncSourceService`）。编译期违反 → REJECTED（reason=CROSS_OWNER）。
- **同 owner 规则蕴含目标资格门禁**：平台内部类型（USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION/CONDITION/**API**，owner=access-service 种子钉死——API 随 T-PERM-069 增补，恰是最易被误认为可作目标的类型）与 MANAGED 自定义类型（无服务 owner）自动被拒——无需独立的目标资格矩阵；声明型提权通道（如 `REPORT:x:VIEW → ROLE:admin:MANAGE`）结构性封死。
- **消费不限**：任何角色持有某服务资源的显式授权，该服务岛内的多级依赖闭包照常展开。限制的只是"谁能声明边"，不是"谁能消费边"。
- ADMIN_UI override 声明豁免同 owner 规则（§12）。

---

## 3. 数据模型（DDL 草案）

> 正式 DDL 随 035A 落入 `docs/design/schema/access-service.sql`（唯一权威）；本节为设计级草案。审计/软删列（created_by/updated_by/deleted_by/created_at/updated_at/deleted_at/delete_flag）全表同款，下文省略。

### 3.1 新表 `permission_dependency_declaration`（声明 SoT）

```sql
CREATE TABLE permission_dependency_declaration (
    id                        BIGSERIAL PRIMARY KEY,
    tenant_id                 BIGINT NOT NULL,
    owner_service_code        VARCHAR(128) NOT NULL,   -- 声明方服务（manifest 通道=凭证绑定的服务；ADMIN_UI 行='ADMIN_UI'）
    maintain_source           VARCHAR(32) NOT NULL,    -- MANIFEST | ADMIN_UI（收敛后仅两值，见 §3.4）
    sync_key                  VARCHAR(512) NOT NULL,   -- 服务内稳定逻辑键（source 四元组+target 四元组，BusinessKeyUtil 族构造）
    manifest_revision         VARCHAR(128),            -- 最近一次携带本声明的 revision
    -- source 四元组（业务键，不做实体解析——解析归编译期）
    source_resource_type_code VARCHAR(64) NOT NULL,
    source_resource_code      VARCHAR(256) NOT NULL,
    source_code_type          VARCHAR(64) NOT NULL,    -- 对齐 resource_entity.code_type 宽度 64
    source_operation_codes    TEXT[],                  -- 大写操作码数组（排序去重；**v1 至多 1 个**——多值清单级预检拒绝 400，multi-bit 触发为 v2 能力〔过度设计重评 2026-09-19 裁剪〕；NULL/空=任意操作触发，对齐 resource_dependency.source_operation_bits NULL 语义）；单码最长 64
    -- target 四元组
    target_resource_type_code VARCHAR(64) NOT NULL,
    target_resource_code      VARCHAR(256) NOT NULL,
    target_code_type          VARCHAR(64) NOT NULL,    -- 对齐 resource_entity.code_type 宽度 64
    required_operation_codes  TEXT[] NOT NULL,         -- 必填非空，大写操作码数组（排序去重）
    description               VARCHAR(512),
    -- 编译结果
    compile_status            VARCHAR(16) NOT NULL,    -- RESOLVED | REJECTED（两态，无 PENDING——同服务资源先于依赖同步）
    reason_code               VARCHAR(64)              -- REJECTED 时必填；RESOLVED 时 NULL
);
-- uk：(tenant_id, owner_service_code, maintain_source, sync_key) WHERE delete_flag = 0
--     FULL diff 按 (owner_service_code + maintain_source) 范围清理（对齐既有 batch-sync FULL 语义）
-- idx：(tenant_id, compile_status) 部分索引（REJECTED 排障面）
```

`reason_code` 取值（封闭枚举，v1）：

| reason_code | 语义 | 重驱动方式 |
|---|---|---|
| CROSS_OWNER | source/target 非同 owner 服务（含内部类型、MANAGED 类型目标） | 改声明（跨系统需求等 §14.1） |
| RESOURCE_MISSING | source 或 target 资源行不存在 | 服务重新 full-sync / 管理员重存声明 |
| TYPE_MISSING | 类型不存在或未声明 SYNC | 管理员建类型后重新提交 |
| OPERATION_INVALID | 操作码在该类型下未定义（fail-closed，对齐 20005 先例） | 修声明 |
| SELF_DEPENDENCY | source == target（对齐现有 20044 自依赖拒绝） | 修声明 |
| CYCLE | 与已提交编译图成环（已提交图优先，§5.3） | 改声明（后到者让步） |

**无自动 resolver**：v1 状态机只有两态，REJECTED 的重驱动=重新提交（服务带新 revision 重传 manifest / 管理员重存声明触发重编译）。不存在 afterCommit 事件重试机制（跨系统 PENDING 家族随 §14.1 整体后置）。

### 3.2 新表 `service_manifest_sync`（manifest 摄入状态）

```sql
CREATE TABLE service_manifest_sync (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL,
    service_code  VARCHAR(128) NOT NULL,
    revision      VARCHAR(128),           -- 最近成功处理的 revision
    payload_hash  VARCHAR(64),            -- 声明清单规范化摘要（排序去重后哈希）——Provider 动态数据变化（revision 不变）不被短路吞掉
    status        VARCHAR(16) NOT NULL,   -- SUCCESS | PARTIAL | FAILED
    is_dirty      BOOLEAN NOT NULL DEFAULT false,  -- 外部失效置脏：③④⑦等非 manifest 入口改变 declaration/编译行时
                                                   -- 同事务置 true；manifest 同步成功置 false——短路判据第四条件
                                                   -- （防"历史 SUCCESS 掩盖当前 REJECTED/缺失"）
    last_sync_at  TIMESTAMPTZ NOT NULL
);
-- uk：(tenant_id, service_code) WHERE delete_flag = 0
```

用途：幂等判断（口径唯一以 §4.2 三条件为准——同 revision + payload_hash 一致 + 上次项级全 RESOLVED）、Admin UI 展示（ownerServiceCode / revision / lastSyncAt / status）。

### 3.3 服务凭证表（归属公共服务认证模块）

`service_credential` 表（tenant + service 粒度、credential_id + BCrypt secret_hash、多凭证并存轮换）**升格为公共服务认证模块的组成部分**，DDL 草案与认证协议迁移至独立设计稿 [service-authentication.md](../../design/service-authentication.md)（含现有认证体系全景盘点；独立前置任务卡承载）。本稿 v1 新表因此为 **3 张**（declaration / manifest_sync / auto_support）+ resource_dependency 改造。

### 3.4 改造 `resource_dependency`（compiled projection 定位）

| 改动 | 内容 |
|---|---|
| **加溯源列** | `declaration_id BIGINT`（**诊断性「最近编译来源」**——同键多声明按并集聚合为单行编译产物，见 §5.5；删除声明按编译键重编译而非按本列定位）、`compiled_revision VARCHAR(128)`——UI 展示 Manifest Revision 有据可查 |
| **加反向索引** | `idx_resource_dependency_depends_on (depends_on_resource_entity_id) WHERE delete_flag = 0`——反向闭包（"谁依赖 X"→受影响角色）的关键查询，现状只有按源查的索引 |
| **auto_grant 列退役** | 该列语义已被本设计整体取代（compiled 图内全部边都参与自动授权，无第二种有效语义）；路径=deprecated（现状 20048 拒 true 维持）→ migration → remove；manifest 协议不暴露该字段 |
| **maintain_source 收敛** | 入库值收敛为 `MANIFEST`（manifest 通道，SDK_SCAN 只是 Manifest 的生成方式，数据库不感知）与 `ADMIN_UI` 两值；`SDK_SCAN`/`SERVICE_SYNC` 值不再写入（注释与 @Pattern 同步收敛） |
| 保留 | 现有 uk（三元组+COALESCE(source_bits,0)）、owner_service_code、sync_key、软删结构原样 |

### 3.5 新表 `role_permission_auto_support`（物化溯源）

```sql
CREATE TABLE role_permission_auto_support (
    id                         BIGSERIAL PRIMARY KEY,
    tenant_id                  BIGINT NOT NULL,
    abstract_role_id           BIGINT NOT NULL,
    auto_permission_id         BIGINT NOT NULL,   -- role_resource_permission.id（grant_source=AUTO_DEP 行）
    dependency_id              BIGINT NOT NULL,   -- resource_dependency.id（触发边）
    source_resource_entity_id  BIGINT NOT NULL,   -- 边的源资源
    source_operation_bits      BIGINT NOT NULL,   -- 本路径触发该边的源操作 canonical 位
    condition_id               BIGINT,            -- 路径条件（条件直传：路径种子的 condition_id；NULL=无条件路径）
    parent_support_id          BIGINT,            -- 多级链上一跳 support（explain 链式溯源；一级边为 NULL）
    source_grant_id            BIGINT             -- 起点 MANUAL 种子行 id（explain 定位显式授权源头）
);
-- uk：(tenant_id, auto_permission_id, dependency_id, source_resource_entity_id,
--      source_operation_bits, COALESCE(condition_id,0), COALESCE(parent_support_id,0),
--      COALESCE(source_grant_id,0)) WHERE delete_flag = 0
--      ——「每种子×每路径一条」稳定语义（不依赖遍历次序去重）；部分唯一索引必须限有效行：
--        全量重建=同事务先软删旧 support 再插 desired，缺 WHERE 则第二次 rebuild 撞软删历史行整事务回滚
-- idx：(tenant_id, dependency_id)（边删除→support 收缩）；(tenant_id, auto_permission_id)（行删除级联）
```

- 无 origin_service_code 列（跨系统 provenance 随 §14.1 后置）。
- support 的存在性即存续判定：AUTO_DEP 行的 support **全量消失才删行**（多路径语义）。
- rebuildRole 采用"desired 全量重算 + support 全量重建"（不做增量引用计数——多级依赖下极易出错，全量重算正确性可证明）。

### 3.6 `role_resource_permission` 零改动（论证）

- uk 已含 `COALESCE(grant_source,'MANUAL')`（schema L1201）：同（角色，资源，操作位）的 MANUAL 行与 AUTO_DEP 行**合法共存两行**。
- `ck_role_resource_permission_manual_single_operation` 只约束 MANUAL 行单操作位；本设计 AUTO_DEP 行**主动采用单 canonical 操作位/行**（§6.5 反链物化），与 MANUAL 行形状对齐、不依赖多 bit 技巧。
- `condition_id IS NULL OR can_grant = false`：AUTO_DEP 行 canGrant 恒 false（§6.6），condition_id 允许——条件直传（§6.4）无需改约束。
- 三条鉴权 SQL（selectValidByRoleIds / selectScopeAllPermsByBitsBatch / selectInstancePermsByBitsBatch）均无 grant_source 谓词——**AUTO_DEP 行插入即被引擎消费，热路径零改动**。ROLE_PERM_SNAPSHOT（L2_ONLY/10s，仅 LIST 管线）自然携带 AUTO_DEP 行，缓存语义不变。
- `grant_dep_id` 列归宿（多路径模型下语义收窄）：填**首条/覆盖度最高的一条边**仅供排障（多路径真实溯源在 auto_support），或标注 deprecated 不写；`engine/core-flows.md §12` 与 `engine/implementation.md` 以 grantDepId 为 AUTO_DEP 精准清理键的旧叙述随 035B 回写清单点名更新（列现状零 Java 读取方，无功能风险）。

---

## 4. Manifest 协议与接入层

### 4.1 Manifest 模型

JSON 声明文件（推荐 `classpath:/META-INF/access-mesh/permissions.json`）：

```json
{
  "revision": "git:8ab29f2",
  "schemaVersion": 1,
  "dependencies": [
    {
      "source": {
        "resourceTypeCode": "REPORT",
        "resourceCode": "sales-monthly",
        "codeType": "SYSTEM",
        "operationCodes": ["VIEW"]
      },
      "requires": [
        {
          "resourceTypeCode": "REPORT",
          "resourceCode": "sales-template",
          "codeType": "SYSTEM",
          "operationCodes": ["VIEW"]
        }
      ],
      "description": "查看月度销售报表需要模板查看权（source.operationCodes 缺省=任意操作触发）"
    }
  ]
}
```

清单格式采用 JSON（2026-09-19 定案）：结构与服务端请求 DTO **严格同构**（文件格式=线格式，starter 原样提交请求体——全局 ObjectMapper 严格模式下未知字段即 400，故清单文件**不含** serviceCode 等线格式外字段，服务身份来自 starter 配置的凭证绑定）；操作码采用复数列表形态 `operationCodes`（与 declaration 表及既有 DTO 对齐）；无缩进敏感、结构边界由括号显式闭合；解析走 Jackson（仓库标准）。JSON 无注释——说明性信息走每条声明的 `description` 字段。

- 动态实例（业务库产生的报表/仪表盘）走 **Provider SPI**：

```java
public interface PermissionManifestProvider {
    Stream<DependencyDeclaration> dependencies();   // 从业务库流式产出声明
}
```

- **v1 砍注解声明**（@PermResource/@PermRequires 与 JSON 清单功能重叠；§14.6 后置）。JSON 清单（静态）+ Provider SPI（动态）覆盖全部场景；两种方式入库后统一 `maintain_source=MANIFEST`。
- starter 启动本地最小校验（重复 sync_key / 自依赖 / 本服务内显式环 / 空操作码），错误不必等到 Access-Mesh 才发现。

### 4.2 服务端接口（只收依赖声明）

```text
POST /api/access/integration/permission-manifest/full-sync
请求：{ schemaVersion, revision, dependencies: [ { source 四元组, target 四元组, description } ] }
响应：R<SyncResultResp 风格信封>（恒 code=200；data.accepted + itemResults[{businessKey, applied, retryClass, reason}]）
```

- **不含 resources[]**：资源同步沿用现有 `resource-entity/full-sync`（完整复用 20055 类型所有权门禁、sync_metadata 差异校准、逐条 itemResults 信封），服务端不做大事务编排。
- `serviceCode` 不在请求体——**身份来自凭证**（§9：credential → tenant+service 绑定）。
- FULL 语义：`existing(同 owner+maintainSource) - incoming = 删除候选`（清单即终态，对齐既有 FULL diff 口径）；declaration 增删后立即重编译受影响范围。
- 幂等：**同 revision 且 payload_hash 一致且上次项级结果全部 RESOLVED 且 `is_dirty=false`** 才短路返回无变化——payload_hash=清单规范化摘要（契约见下）：Provider SPI 的动态业务数据可在应用未发版、revision 不变时增删依赖，仅比 revision 会把已撤销的声明短路保留（=授权撤销失效）；is_dirty 防"服务端外部事件（§7③④⑦）已删除/降级 declaration，而历史 SUCCESS 状态掩盖当前缺失"——此时同 R/H 重传也强制全量重判。hash 不一致 / 存在 PARTIAL/REJECTED / is_dirty=true 时不短路——对全部声明重跑编译判定（资源/操作码可能已到位），结果落 `service_manifest_sync` 并清 dirty。REJECTED 的重驱动因此无需强制换 revision（与 §3.1「重新提交」为同一口径）。
- **payload_hash 规范化契约（确定性，防抖动短路）**：参与字段=每条声明的 source 四元组（resourceTypeCode/resourceCode/codeType/operationCodes）+ target 四元组 + requiredOperationCodes——`description` 等展示性字段**不参与**；`operationCodes` NULL 与空数组**等价**（都=任意触发/空集）；操作码数组**排序去重**；声明**按各自规范化后的完整 canonical bytes 字典序总排序**（比较器末键含两个操作数组——同资源对、不同操作组合的声明不得比较相等，否则 Provider 无序迭代仍进 canonical JSON）；canonical JSON（UTF-8）+ SHA-256——同一清单经 Provider 无序流两次产出必须得到同一 hash。回归矩阵：声明顺序互换、requires 顺序互换、NULL vs 空数组、重复项、Provider 迭代顺序变化、同资源六元组不同操作数组顺序互换六类。

### 4.3 starter：`perm-registration-spring-boot-starter`（新 SDK 模块）

```text
perm-sdk/
├── perm-common
├── perm-client-spring-boot-starter     # 既有：Feign 远程权限查询（职责不变）
├── perm-registration-spring-boot-starter  # 新增：声明加载/校验/同步
└── perm-gateway-spring-boot-starter
```

配置模型：

```yaml
access-mesh:
  registration:
    enabled: true
    endpoint: http://gateway:8080
    tenant-id: 1001                    # 单租户部署形态
    service-code: example-service
    credential-id: "..."
    credential-secret: "..."           # 静态凭证（§9）
```

多租户 SaaS 形态提供 SPI（业务系统返回租户清单，starter 逐租户独立执行两步同步）：

```java
public interface TenantRegistrationProvider {
    Stream<TenantRegistrationContext> tenants();   // {tenantId, credentialId, credentialSecret}
}
```

两步编排与失败处理：

```text
步骤① resource-entity/full-sync（资源清单）──成功──▶ 步骤② permission-manifest/full-sync（依赖清单）
   │ 失败：整体重试（幂等）                          │ 失败：同 revision 仅重试步骤②（步骤①幂等已落）
```

跨步失败不产生越权：资源删除路径自身完成依赖/AUTO_DEP 收窄清理（§7 触发面 ③），步骤②晚到只影响新声明生效时点（fail-closed 方向）。

---

## 5. DependencyCompiler（编译器）

### 5.1 校验链（每条声明，顺序执行）

```text
1. 格式与清单级预检（畸形清单零副作用：空操作码/字段缺失 → 整批 20044 风格拒绝）
2. source 类型解析与所有权：source 类型须为 SYNC 且 owner == 声明方服务（否则 TYPE_MISSING / CROSS_OWNER）
3. 同 owner 规则：target 类型 owner == source 类型 owner
   ——蕴含目标资格（内部类型 owner=access-service ≠ 业务服务 → CROSS_OWNER；MANAGED 无 owner → CROSS_OWNER）
4. 资源解析：source/target 资源行存在（RESOURCE_MISSING；操作码解析 fail-closed → OPERATION_INVALID）
5. 自依赖拒绝（SELF_DEPENDENCY）
6. 环检测：candidate graph（本次 diff + 其他方存量已编译图）联合校验（CYCLE）
```

### 5.2 两态状态机

```text
declaration ──编译──▶ RESOLVED（编译行写入 resource_dependency，带 declaration_id 溯源）
              └─────▶ REJECTED（reason_code 记录，不产生编译行；已有编译行【若为本次降级】随之软删）
```

无 PENDING、无自动 resolver（§3.1）。REJECTED 声明**保留入库**供诊断与 Admin UI 展示。

### 5.3 环检测：已提交图优先（first committed wins）

- 校验基线 = 已提交的 compiled graph（`resource_dependency` 有效行）+ 本次 diff；
- 闭环责任归**本次 diff**：存量边不回滚，后到者的 full-sync 被拒（REJECTED/CYCLE），须修改自己的 manifest；
- 实现：DFS 即可（不上 Tarjan/SCC——正确性等价、代码面更小）；
- 与并发控制配合（§8）：编译在租户编译锁内串行执行，杜绝"两服务并发提交互成环、双方都基于旧图判无环"的竞态。

### 5.4 管理面端点语义迁移

| 现端点（`/api/access/resource-dependency/*`） | 迁移后语义 |
|---|---|
| create / update / remove | **保留路径**，内部改为 declaration CRUD（maintain_source=ADMIN_UI）+ 触发重编译；门禁维持 DEPENDENCY:CREATE/UPDATE/DELETE 类型级 |
| list / graph / check | 保留（读取面；list 增列 compile_status/reason/revision） |
| **batch-sync** | **删除**（前端零消费方实证：frontend/src/api/resource-dependency.ts:30 注释"前端不调用"、端点未进 Gateway apiRoutes；仅后端测试引用）。连带面：契约总册 §12.4 整节与端点表行收口；`DEPENDENCY:SYNC` 操作位与 bootstrap 固定图授权行收口（删除后零消费方）；`autoGrant` 字段链（后端 DTO + schema 列 + 前端类型 + 契约 20048 行）前后端/SDK **同批锁步**退役——严格 Jackson 下旧前端回传已删字段即 400（T-ACCESS-036 先例） |

ADMIN_UI 通道 policy（§12）：**完全豁免**同 owner 规则（`owner_service_code='ADMIN_UI'` 行不参与 §5.1 第 2/3 步校验，仅做自依赖/环/操作码校验）——管理员显式建边的权能不超过其直接授权能力，保留原 §48 兜底价值；UI 明确标注 override。

### 5.5 同键多声明的编译聚合（并集聚合，2026-09-19 拍板）

编译产物按**编译键**（源实体 + 目标实体 + COALESCE(source_operation_bits,0)，即现有 uk）聚合为单行：

- `required_operation_bits` = 该键下**全部 RESOLVED 声明的并集**（MANIFEST 与 ADMIN_UI 任意来源组合）；
- 任一声明增删改 → 按编译键重编译：并集扩大/回缩，随后触发受影响角色 rebuild（§7②）；
- `declaration_id` 为诊断性「最近编译来源」，不是唯一溯源（删除声明按编译键重编译，不按本列定位）；
- 效果：ADMIN_UI override 对既有边**追加操作**自然表达（同键新声明并入并集）、无撞键、无静默接管；撤销 override = 删该 ADMIN_UI 声明 → 并集回缩。

**declaration 写守卫**（迁移后管理面端点）：create/update/remove 对 `maintain_source='MANIFEST'`（owner 为服务身份）的声明行**拒绝**（错误码随 035A 排号，提示「该行由服务清单维护，请修改 manifest」）——清单即终态的一致性边界；ADMIN_UI 行可自由增删改。依赖端点现状只有 DEPENDENCY:CREATE/UPDATE/DELETE 人权限门禁、**无所有权守卫**（已核实 DependencyAppServiceImpl 全部写入口），本守卫为新增必要项。

---

## 6. AutoGrantMaterializer（物化器）

### 6.1 种子定义（v1 精确口径）

```text
seed = 该角色全部有效 MANUAL 行，且：
  - scope_all = false（类型级授权不参与闭包——含 AUTHORITY_ROOT，A1 定案）
  - resource_entity_id IS NOT NULL（实例级）
  - depend_on IS NULL（子权限行不参与闭包——子权限与依赖无关，补1 定案）
  - condition_id 不限（带条件种子参与闭包，条件沿路径传播——补3 B 案，推翻原稿 §29 fail-closed）
```

种子**不按判定面继承展开**（引擎管理面「父实例授权覆盖子实例」默认开，与本闭包的差异**有意为 v1 边界**）：闭包种子仅认该资源实例上的显式 MANUAL 行本身；父授权覆盖子实例的场景由管理员显式授予子实例满足（登记为边界，避免实施期自行发明；展开与否随 §14 演进评估）。

### 6.2 闭包算法（伪码）

**装载策略（禁 N+1 红线）**：rebuild 事务内**一次批量装载**受影响范围（租户 + 服务岛/源资源集合）的全部编译边（IN 查询，走 `idx_resource_dependency_resource` 与 §3.4 反向索引），构建内存邻接图后再遍历；一次编译事务 rebuild 多个受影响角色时**共享同一内存图**，不逐角色重复查库。**装载范围不按 projection owner 过滤**：ADMIN_UI override 可建跨 owner 边（连通多个服务岛），存在 ADMIN_UI 跨 owner 边时**一次装载租户级 compiled graph**（正确性优先；按连通分量装载为后续优化）——聚合行上的 owner_service_code/maintain_source 仅为诊断字段，不参与闭包正确性。触发判定按**资源聚合**：`resourceEffectiveBits(R) = 该角色在 R 上全部显式行有效位的并集 ∪ 已推出的逻辑位`——multi-bit 触发位（source_operation_bits 多值）跨多行显式授权合并满足时同样触发（如 VIEW 行 + EXPORT 行合并满足 `VIEW|EXPORT` 触发位）；回归锁须含「跨行满足」与「单行满足」两用例。

**触发证明（TriggerProof，单操作×条件组）**：v1 触发操作**至多 1 个**（multi-bit 触发已裁剪，§14.7）——按 condition_id 分组聚合资源有效位：无条件显式行并入 NULL 组（**NULL 组存在即吸收一切非空组**——无条件行运行时本就覆盖一切）；每个非空条件的行自成一组。证明集：

- **指定操作触发**：每个「含该操作位的条件组」各产出一个证明，变体=组条件（NULL 组存在时为唯一证明、变体=NULL；否则每个非空条件组一个证明=多变体并存）；
- **任意操作触发**（sourceBits IS NULL，契约正式形态非脏数据）：每个「持有 ≥1 位的条件组」各产出一个证明；support 的 `source_operation_bits` 记**实际触发该证明的 canonical 位**（禁 NULL/0 占位）。

证明的 antecedent 为**单条授权行**（MANUAL uk 保证同 (角色,资源,位,条件) 唯一）——multi-bit 时代的多 antecedent support 机制随裁剪消失。

**条件多变体（"最宽合并"的完整语义）**：logical 状态为 `(resource, canonicalOp, conditionVariant)` **多变体**——仅相同条件可合并；**NULL 变体吸收（支配）一切非空变体**（=「最宽路径生效」定案的准确形态：NULL 是唯一最宽）；**两个不同非空条件 C1/C2 = 两个变体并存**，物化为两行（uk 已含 COALESCE(condition_id,0) 天然容纳；运行时快照本就按 conditionId 多变体消费，OR 语义）——只保留一个会在另一条件成立时少权、错误置 NULL 则越权。

**证明组的数据模型映射**：support 行=每跳×每变体一条（antecedent=触发证明所在授权行；§3.5 uk 含 source_grant_id 去重）；物化行 condition_id = 证明变体条件。回归锁须含四类用例：同操作无条件+条件并存（NULL 吸收为单行）、不同非空条件多变体并存（C1 行+C2 行）、任意触发多证明组、两条路径遍历顺序互换。

```java
rebuildRole(tenantId, roleId):
  seeds = loadSeeds(roleId)                                // §6.1
  logical = {}   // Map<(resourceId, canonicalOpBit, conditionVariant), ?>——多变体（NULL 变体吸收非空；C1/C2 并存）
  queue   = seeds.map(s -> node(s.resourceId, effectiveBits(s), s.conditionId, parent=null, grantId=s.id))
  while (!queue.isEmpty()):
    node = queue.poll()
    for (edge : adjacency(node.resourceId)):      // 内存邻接图（事务首批量预装载，禁逐节点查库——N+1 红线）
      // 触发证明集（TriggerProof，§6.2 触发证明规则的算法形态；v1 触发操作至多 1 个）：
      //   resourceBitsByCondition(R) = 按 condition_id 分组的有效位（无条件行并入 NULL 组、NULL 组吸收非空组）
      //   sourceBits 非 NULL（单操作）：每个含该位的条件组各一证明（NULL 组存在时为唯一证明）
      //   sourceBits IS NULL（任意操作触发）：每个「持有 ≥1 位」的条件组各产出一个证明（多证明多变体）
      proofs = buildTriggerProofs(resourceBitsByCondition(node.resourceId), edge.sourceBits);
      for (proof : proofs):
        for (opBit : canonicalBits(edge.requiredBits)):   // 逐 canonical 操作位
          variant = proof.conditionId                     // 条件直传：变体 = 完整证明组的条件（遍历顺序无关）
        if (logical.putNewVariant((edge.target, opBit), variant)):      // NULL 变体吸收非空；同条件合并；C1/C2 并存为新变体
          queue.add(node(edge.target, variant, parent=本跳 support, grantId=proof.grantIds))
  materializeDiff(roleId, logical)                         // §6.3–§6.6
```

- `effectiveBits = binaryBit | inheritMask`（复用 `OperationPermissionUtils`，与引擎语义同源——层级不是枚举关系而是位运算）。
- 触发判定采用**全位包含**（授权有效位 ⊇ edge.source_operation_bits，继承位计入），对齐 schema 注释"源资源授权含这些 bit 时才触发"；`source_operation_bits IS NULL` = 任意操作触发。

### 6.3 覆盖压制（suppression）

逻辑闭包与物理行分离：**逻辑上 (resource, op) 已满足即继续向下游展开**（否则 `VIEW→C` 无法计算）；物理落库前做压制判定：

```text
若存在显式行（MANUAL / AUTHORITY_ROOT，含 scope_all 类型级行）满足：
  effectiveBits(显式行) 覆盖 opBit
  且（显式行 condition_id IS NULL 或 == 路径条件）       ← 条件比较（补3 联动）
则该 (resource, op) 不落 AUTO_DEP 行（避免冗余行与 explain 噪音）
```

撤销显式授权后：同事务 rebuildRole 重算，依赖边仍在则 AUTO_DEP 行自动复现（§7 触发面 ①），无权限缺口、无残留越权。

### 6.4 条件直传（最宽路径生效）

- 物化行 condition_id = 该 (资源, canonical 操作) 的**条件变体**（§6.2 多变体：NULL 变体吸收一切非空变体=只物化一行；C1/C2 等不同非空条件各物化一行、OR 语义并存）；
- 多路径示例：`REPORT:A:VIEW(条件=工作时间)` 与 `DASHBOARD:D:VIEW(无)` 都依赖 `DATASET:X:READ` → 物化行条件=NULL（无条件路径放宽了整行），**两条件 support 并存**；
- explain 必须能显示"此权限被哪条无条件/更宽路径放宽"（§13/035C）——放宽可见性是本定案的组成部分；
- 压制判定含条件比较（§6.3）：仅无条件或同条件的显式覆盖才压制；窄条件显式行不压制宽路径 AUTO_DEP（后者可用时段更宽，非冗余）。
- support 按**种子×路径全量记录**（uk 含 source_grant_id，见 §3.5）：窄条件路径晚到不丢 explain；`merged 变化` 守门仅控制闭包展开（避免重复入队），不承担 support 去重。
- **INLINE 内联条件与 MANAGED 条件同样直传**（T-PERM-048 口径随本稿修订，2026-09-19 拍板）：INLINE 条件不变量从「1:1 属于授权记录不可共享」修订为「**1 属主（MANUAL 行）+ N 系统派生引用（AUTO_DEP 行）**」——系统物化派生不算用户态共享，用户侧 conditionCode 引用守卫不变（inline- 条件仍不可被显式引用）；**INLINE 回收时序移到 rebuild 完成之后**（属主 MANUAL 行与派生 AUTO_DEP 行同事务删除、引用真正归零才软删条件行，杜绝孤儿）：

  ```text
  撤销序列（同一事务）：删 MANUAL 行 → rebuild 删派生 AUTO_DEP 行 →
  条件引用真正归零 → 回收判定（移至 rebuild 后执行）→ 同事务软删 INLINE 条件行
  ```

  该序列是**全部会消灭 MANUAL/AUTO_DEP 引用的入口的统一模式**（触发面①③④⑤：授权撤销 / 资源删除 / 类型删除级联 / 角色删除）：入口开始前批量收集候选 conditionId → 完成自身删除与 rebuild（派生引用清零）→ **同事务末尾执行一次回收**——既有各入口的回收调用时序（含 T-PERM-048 处置③的级联回收先例，现均在 rebuild 之前执行）随 035B 统一后移，杜绝"AUTO_DEP 仍引用而跳过 → rebuild 删引用后无人再回收"的孤儿窗口。

- **support 完整性 035B 一步到位**（过度设计重评裁剪，2026-09-19）：`merged` 守门只保证权限结果正确——窄路径后到时其**下游** support、被压制逻辑节点的中间 support 不随队列出；物化器在 logical 闭包稳定后**同事务执行第二遍路径追踪**补全全部 support（后到窄路径下游、被压制节点挂推导节点）——support 从第一天完整，035C 不再需要 format_version / 全量回填 / explain 启用门禁。

### 6.5 反链物化（存储粒度）

- 每资源物化行集合 = logical 操作集合在操作覆盖偏序上的**反链压缩**（仅保留不被同资源其他已物化操作覆盖的 canonical 操作）：`{VIEW, UPDATE}` 且 UPDATE effective 含 VIEW → 只落 UPDATE 一行；`{EXPORT, APPROVE}` 不可互相覆盖 → 两行。**压缩必须同时满足"操作覆盖且条件不更窄"**（存活行与被压缩行同条件、或存活行条件为 NULL）——例：无条件 VIEW 变体与条件 C 的 UPDATE 变体并存时**不得**按"UPDATE 覆盖 VIEW"压缩（会把无条件可用收窄为仅 C 时可用，少权）。
- AUTO_DEP 行 = 单 canonical 操作位/行（与 MANUAL 形状对齐；不利用"AUTO_DEP 可多 bit"的 schema 容许）；diff 稳定、explain 清晰。
- 被压缩操作点的 support **一并归并挂到存活行**（auto_permission_id 指向压缩后行，explain 按覆盖关系展示被压缩点——如 VIEW 的 support 挂在 UPDATE 行下并标注「VIEW 被 UPDATE 覆盖」），Reconciler 的「AUTO_DEP 行 support 为空」判据不受压缩影响。

### 6.6 AUTO_DEP 行形状约束

```text
grant_source = 'AUTO_DEP'；can_grant = false（禁止转授权传播）；
condition_id = 最宽路径条件（可 NULL）；depend_on 恒 NULL（补1：子权限与依赖无关）；
每行挂 1..N 条 role_permission_auto_support（存续判定=全量消失才删）
```

现有只读护栏不变：apply-grant-plan 的 `assertMutable` 对 AUTO_DEP 行改/删拒绝（20034）。

### 6.7 触发时机与中断语义（v1 全同步）

**触发时机**——AutoGrantMaterializer 只在写路径被同步调用，共 7 个入口（即 §7 触发面），全部在**触发它的那个数据库事务内**执行：

```text
applyGrantPlan（授/撤/改 MANUAL）          → 同事务 rebuild 该角色
manifest full-sync / ADMIN_UI 声明增删改    → 编译事务内 rebuild 全部受影响角色
资源删除（sync DELETE / 清单漂移）          → 同步事务内收缩回收
类型删除级联 / 角色删除 / 条件变更          → 对应事务内回收或 rebuild
操作位变更/删除、类型所有权声明变更         → 定义写事务内重编译引用声明 + rebuild（⑦）
```

**中断语义**——v1 不存在"长任务中断继续"问题，这是全同步设计的直接收益：

- MANUAL diff + AUTO_DEP diff + support 重建在**同一事务**：进程崩溃 / kill / 任何中断 = **整体回滚**，不存在"算了一半"的中间态；
- 重试天然幂等：manifest 同 revision 重传无变化；applyGrantPlan 重放由唯一索引兜底（既有 translateUniqueViolation→20040 机制）；
- commit 后、缓存失效广播前崩溃：仅 ROLE_PERM_SNAPSHOT（L2_ONLY / 10s TTL）存在至多 10s 陈旧窗口，实例级 / scopeAll / 批量直查路径不受影响——fail-safe 方向只是"旧权限多可见至多 10s"，广播本身崩溃由对账（§13）兜底。

**长事务边界（诚实声明）**：编译事务持有租户编译锁期间，其他服务的 manifest 同步排队等待；单次 rebuild 的闭包规模 = 该服务岛内资源数（v1 每服务岛、报表/数据集量级，可控）。若未来单次依赖变更影响角色数显著增大，即为 §14.4 演进路径的启用信号（不对称熔断 + 持久化队列异步精确重建——那才是需要"断点续跑"机制的形态）。v1 无队列、无熔断、无 SCALE_EXCEEDED（C3 定案）。

---

## 7. rebuild 触发面（全量 7 项）

| # | 触发事件 | 处理 |
|---|---|---|
| ① | applyGrantPlan（授/撤/改 MANUAL） | 同事务 rebuild 受影响角色 |
| ② | 依赖编译 diff（manifest full-sync / ADMIN_UI 声明增删改） | 编译事务内：按变更边反向定位受影响角色（source 闭包反查持有角色）→ rebuild |
| ③ | 资源实例删除（resource-entity/sync 的 DELETE op / full-sync 清单漂移） | 该资源上 AUTO_DEP 行与以其为 source 的闭包收缩同事务回收；declaration 随 manifest 对齐删除。注：**资源**管理面 remove 对 SYNC 类型 20055 拒（ResourceManageAppServiceImpl 既有门禁）焊死的是「手删资源→复活」缝；**依赖端点**（迁移后 declaration remove）无所有权守卫，其守卫见 §5.5 |
| ④ | 类型删除级联（T-PERM-050 唯一回收路径） | 整类型 declaration/编译边/AUTO_DEP/support 同事务回收 |
| ⑤ | 角色删除 | 直接删该角色 AUTO_DEP 行 + support（无需 rebuild） |
| ⑥ | 条件编辑/删除（补3 联动：条件影响物化行 condition_id 与压制判定） | 定位引用该条件的 MANUAL 种子行与 AUTO_DEP 行 → rebuild 受影响角色 |
| ⑦ | **操作定义面变更**：binaryBit 变更 / **inheritMask 变更**（OperationUpdateReq 两者均开放，effectiveBits 同受影响）/ 操作删除 / 类型所有权声明变更（extra.managedMode/syncSourceService） | **binaryBit 变更与操作删除：存在任意有效授权引用（MANUAL/AUTHORITY_ROOT/AUTO_DEP）时整批拒绝**（对齐 20059 条件删除引用守卫先例——位变更=语义上换了操作，授权清零后才允许；同时杜绝存量"MANUAL 行存旧位"缺陷被闭包放大，弃全量迁移路线）。**inheritMask 变更与所有权声明变更：同事务重编译受影响声明 + rebuild 受影响角色**。⑦ 因引用拒绝不再消灭授权行，INLINE 回收面维持①③④⑤ |

资源实例**新增**不触发（scope_all 种子已排除）；export 撤回、PENDING resolver 不存在（v1 范围外）。

**manifest 置脏联动**：③④⑦ 及任何非 manifest 入口改变 declaration/编译行时，**同事务**将该服务（tenant+ownerServiceCode）的 `service_manifest_sync.is_dirty` 置 true（§4.2 短路第四条件）——否则外部失效事件（资源删除后恢复、声明被降级 REJECTED）会被"同 revision + 历史 SUCCESS"错误短路，依赖边与 AUTO_DEP 长期无法恢复。

---

## 8. 并发与事务

### 8.1 锁模型：全序三段 + 入口锁矩阵

```text
① 树写锁（RESOURCE_ENTITY / ABSTRACT_ROLE / SYS_ORG，既有 TreeWriteLockSupport）
     → ② 租户编译锁 dependency-compile:{tenantId}
          → ③ 角色锁 auto-grant-role:{tenantId}:{roleId}（多角色恒按 roleId 升序）
```

- **全序固定为 树锁 → 编译锁 → 角色锁，且树锁族内次序钉死 `SYS_ORG → ABSTRACT_ROLE → SYS_MENU → RESOURCE_ENTITY`**（对齐现存唯一多树持锁先例=组织写路径 SYS_ORG→ABSTRACT_ROLE→RESOURCE_ENTITY）；任何入口不得反向持锁（不得在角色锁内再取编译锁/树锁、不得在编译锁内再取树锁、多树获取必须按族内次序）。`TreeWriteLockSupport` 无 leaseTime、watchdog 持续续期、按 2026-09-06 定案不加 tryLock 保险丝——任何反序=两事务**无限期互等**并把树锁面一起拖死，锁序是唯一防线，须配锁序断言/评审口径。**存量反序登记**：`TypeDefinitionAppServiceImpl` 混合批删路径现行 RESOURCE_ENTITY→ABSTRACT_ROLE 与全序相反（既有缺陷、被本设计新增锁链放大），随 035A/035B 实施统一收敛；并发锁序测试须覆盖组织写×类型混合删除、操作定义变更×资源删除两组交错。
- 触发入口 × 取锁集合矩阵：

| 触发入口 | 取锁（按序） |
|---|---|
| ① applyGrantPlan | 角色锁（单角色，不取编译锁——无死锁面） |
| ② 依赖编译 diff（manifest / ADMIN_UI 声明） | 编译锁 → 受影响角色锁（升序） |
| ③ 资源删除 | 资源树锁 → 编译锁 → 角色锁（升序） |
| ④ 类型删除级联 | 既有类型删除锁/级联路径 → 编译锁 → 角色锁（升序） |
| ⑤ 角色删除 | 角色树锁 + 目标角色锁（只删行，不取编译锁） |
| ⑥ 条件变更 | 角色锁（受影响角色，升序） |
| ⑦ 定义面变更 | **RESOURCE_ENTITY 树锁**（OperationAppServiceImpl/TypeDefinitionAppServiceImpl 既有先例）→ 编译锁 → 角色锁（升序） |

- 实现沿用 `TreeWriteLockSupport` 模式（Redisson 事务内 lock + afterCompletion 释放、fail-closed）；授权写路径现状零锁（已核实 grant 包无任何锁），per-role 锁为新增必要项——rebuild 是"读种子→算闭包→diff 写回"，无锁则并发授权丢失更新（后算闭包覆盖前一笔撤销结果）。

### 8.2 事务边界

```text
单一事务：MANUAL diff + AUTO_DEP diff + support 重建 + 变更标记（PermissionChangeContext.markRoles）
commit 之后（afterCompletion）：缓存失效广播（evictAfterCommit 既有机制）——缓存失效不得置于 commit 前
```

---

## 9. 服务凭证（消费公共服务认证模块）

per-service 静态凭证已升格为**公共服务认证模块**（M2M 服务身份认证，多通道共用的平台能力）：现有认证体系全景盘点（三类信任主体 + SERVICE 上下文绑定）、`service_credential` 表 DDL、认证链（凭证头 → 凭证行 → SERVICE 上下文派生，消除自报头信任面）、凭证生命周期（签发/轮换/停用）、Gateway 与 SDK 直连两类接入形态、`X-Internal-Secret` 遗留形态分期退役判据——全部见独立设计稿 [service-authentication.md](../../design/service-authentication.md)（`status: adopted`，实现载体 T-PERM-070）。

本稿的消费要求：

- manifest 通道**强制**凭证认证：不收请求体 serviceCode，身份 = 凭证绑定的 tenant+service；
- 资源同步通道同时接受凭证——starter 两步编排只需携带一套凭证；
- 遗留 internal-secret 形态在模块分期退役前维持可用（内部基础设施互信不受影响）；
- 重放幂等（manifest 同 revision 重传无变化）——TLS 内网下无需签名/nonce 防重放（2026-09-19 重评定案）。

---

## 10. 多租户语义

```text
一次 manifest 同步 = 一个 tenant + 一个 service + 一个 revision
```

- `service_config` / `service_credential` / `service_manifest_sync` / `permission_dependency_declaration` 均按 tenant 隔离；
- starter 两形态：单租户（配置文件直配 tenant-id/credential）与多租户 SaaS（`TenantRegistrationProvider` 逐租户独立两步同步；各租户资源集不同时 Provider 按 TenantContext 产出差异化声明）；
- 不设计跨租户大协议。

---

## 11. API 契约草案（正式登记随 035A 入契约总册）

| 端点（全部 POST） | 语义 |
|---|---|
| `/api/access/integration/permission-manifest/full-sync` | 依赖声明 FULL 同步（§4.2；SyncResultResp 风格信封） |
| `/api/access/service-credential/*`（create/update/remove/list） | 凭证管理端点——契约与错误码归属 [service-authentication.md](../../design/service-authentication.md)（create 响应回传明文 secret 仅一次） |
| `/api/access/resource-dependency/explain` | 为何角色 R 有（或没有）资源 X 的操作 P：support 链 + 条件放宽来源（035C 交付）。**DEPENDENCY:VIEW 类型级服务端门禁**（对齐相邻 list/graph/check 先例）；入参用角色业务键解析、禁收裸内部 roleId；**服务凭证可调端点集合限定为 M2M 通道**（资源同步 + manifest 同步），explain 等管理端点不开放凭证调用——该限制在 **access-service 服务端强制执行**（仲裁器之后按 `authMethod=CREDENTIAL × 精确路径白名单` 拒绝，不依赖 Gateway 拦截、SDK 直连同样受限；白名单清单单源共享供 Gateway 与 access-service 两处消费，见 [service-authentication.md](../../design/service-authentication.md) §3.3） |
| `/api/access/resource-dependency/*`（create/update/remove/list/graph/check） | 语义迁移为 declaration 操作（§5.4），batch-sync 删除 |

错误码：perm 段顺延排号（035A 实施时从当前最大号续排，随批登记契约总册）；declaration 级 REJECTED 以 item 级 reason_code 呈现（不走 HTTP 错误码）；20048（auto_grant 拒绝）随列退役；AUTO_DEP 只读维持 20034。业务键构造/解析唯一入口 `BusinessKeyUtil`；sync 契约键（percent-encoded）维持 `SyncKeyCodec`。

---

## 12. Admin UI 定位

从"主要编辑器"转为**观察 + 排障 + override**：

- 依赖页展示列：Source / Target（业务键）、Owner Service、Maintain Source（MANIFEST/ADMIN_UI）、Manifest Revision、Compile Status + Reason、Cross-eligible（同 owner ✓/override）、Affected Roles、AUTO_DEP Count；
- override 表单=ADMIN_UI declaration（豁免同 owner 规则，UI 红字标注"维护来源：ADMIN_UI（override）"，不应成为常规生产配置方式）；
- 授予页/角色权限视图：显式权限与自动权限分组展示（自动权限只读 + 来源链入口）；条件放宽在 support 链上标注。

---

## 13. Reconciliation 对账（035C）

后台任务 `PermissionDependencyReconciler` 周期检查四层一致性：

```text
declaration RESOLVED 但编译行缺失 / 编译行 declaration_id 溯源悬空
AUTO_DEP 行 support 为空 / support 指向已删边
资源已删但 AUTO_DEP 残留
抽样角色 desired vs actual 闭包比对（物化漂移兜底）
```

**035C 无回填负担**：support 完整性由 035B 二遍路径追踪保证（§6.4，过度设计重评裁剪）——035C 不需要 support_format_version / 全量回填 / explain 启用门禁。

---

## 14. 演进方向（非约束，设计完整保留）

### 14.1 跨系统依赖：export 双边协议 + provenance 全链

v1 后置的核心场景（report×customer×finance 多系统依赖）。已定稿设计要点：

- 新表 `permission_export_declaration`（owner、resource、operation_bits、consumers 白名单/"*"、revision）——双边协议的存储前提；
- **穿透两堵法**（v2 择一）：provenance 校验（闭包携带 `originServiceCode`=种子资源 owner，物化期逐路径校验"目标是否 export 给 origin"，含目标方内部边）或闭包截断（跨 owner 节点即止，fail-closed 少给）；
- export 撤回必须同步强一致回收边与 AUTO_DEP（新增方向可异步、撤销方向绝不延后）；
- 状态机扩 PENDING 家族（PENDING_RESOURCE/PENDING_TYPE/PENDING_EXPORT + afterCommit resolver 自动重试）。

### 14.2 类型级依赖（TypeDependencyRule）

scope_all 种子闭包的替代正道：显式的类型级依赖规则对象，不把实例依赖隐式扩展成类型语义（A1 定案的 v2 出口）。

### 14.3 条件路径级追踪

support 全链携带条件上下文、引擎按路径评估——解决最宽路径放宽问题（§6.4 的彻底形态）；代价是打破"物化行=普通行"的热路径零改动原则。

### 14.4 不对称熔断 + 持久化重建队列

大规模角色场景出现后启用：扩权方向超限可延迟（拒绝式 REJECTED/SCALE_EXCEEDED 或激活式 active 标记）；收窄方向**必须先同步保守撤权**（宁多删）→ commit → 持久化队列（`role_auto_rebuild_queue`）异步精确重建（幂等消费、崩溃续跑）。原则：新增/扩权宁可晚给；删除/收窄宁可多撤——两边都直接拒绝不是 fail-closed。

### 14.5 scope_all 种子闭包

授权根（AUTHORITY_ROOT 本身是 scope_all）参与闭包的完整语义（逐实例展开的写放大问题待量化）。

### 14.6 Java 注解声明方式

@PermResource/@PermRequires 启动扫描生成 Manifest（与 JSON 清单同为静态声明，v1 已裁）。

### 14.7 multi-bit 触发位（sourceOperationCodes 多值）

v1 已裁剪（清单级预检拒绝 400，2026-09-19 过度设计重评）：多值触发需分组证明 / 多 antecedent support / 跨条件组合 fail-closed 一整套机制，当前无真实场景（实际依赖声明几乎全为单操作触发）；v2 有真实需求时再上。

---

## 15. 文档关系与落地时点

| 事项 | 时点 |
|---|---|
| 本稿转 adopted（用户确认后）→ 拆分任务卡：**前置**（服务认证模块，独立稿 [service-authentication.md](../../design/service-authentication.md) 承载）、035A（声明层：declaration/manifest_sync 表+manifest 通道+编译器+starter+batch-sync 删除）、035B（物化器+applyGrantPlan 挂接+触发面+锁模型+**support 二遍路径追踪**）、035C（explain+Admin UI 改版+Reconciler，无回填负担） | 本稿确认后 |
| T-PERM-035 暂缓门禁解除（design-review §11 E4，需 PM 重申）+ 任务卡重写 | 同上（用户动作） |
| `schema/access-service.sql` 落 DDL：035A=declaration/manifest_sync + resource_dependency 改造；035B=auto_support；service_credential 随前置卡（认证模块稿） | 035A / 035B / 前置卡 |
| 契约总册新章（manifest 通道/凭证/explain）+ 错误码排号 | 035A |
| `engine/core-flows.md` §12 场景九、`implementation.md` grantDepId 清理链、schema `permission_condition.source` INLINE 注释（1:1 → 1 属主+N 派生引用）按实现回写；既有各入口 INLINE 回收调用统一后移至 rebuild 后 | 035B（design_writeback） |
| `engine/implementation.md`、extension-guide 接入章节（starter 使用指南） | 035A/035B 后随批 |
