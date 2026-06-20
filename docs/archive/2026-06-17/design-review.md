# AccessMesh 设计评审 — 2026-06-17

> 状态：进行中（评审已完成，6 个工作单待逐项推进）
> 评审范围：`docs/design/` 全量 + `docs/design/schema/*.sql` + `docs/plans/{user-role-proxy-fix,improvement-plan,api-gap-analysis}.md` 根因核查
> 关联设计：本评审针对 `docs/design/` 全部权威文档，详见 §10 索引
> 评审日期：2026-06-17

## 目标

1. 检查跨服务链路对接（重心 1）的完整性、健壮性、失败可恢复性。
2. 评估对接简洁高效程度（重心 2），识别可收敛的端点、配置和数据模型。
3. 排查运维与功能维护痛点（重心 3），重点列出静默失败点。
4. 把发现按"用户/产品视角"打包为 6 个工作单，每个工作单含问题描述、方案选项、推荐方案、修改后体验。
5. 给出整体推进路线图与各工作单工期估算。

## 非目标

- 不考虑旧数据迁移或旧逻辑兼容（项目未上线）。
- 不引入新中间件（Spring Boot 3 + MyBatis-Flex + PostgreSQL + Caffeine/Redis/RocketMQ 栈内）。
- 不重复 `docs/plans/improvement-plan.md` 的前端实现节奏，本评审专注后端与契约。
- 不直接落地修复，本文档仅为"评审 + 方案确认"阶段产出；落地按工作单逐一开新 plan 推进。

---

## 1. 设计意图备忘（评审基准）

AccessMesh 是**通用权限事实源 + 鉴权引擎**，以 permission-center 暴露稳定业务键契约，让任意业务系统（admin/example/外部 SDK）只用 `subjectTypeCode/resourceTypeCode/operationCode` 完成同步、授权、鉴权，不感知内部主键。

**主要权衡**：
- admin→perm 走 **API + 本地 outbox（sys_sync_task）最终一致**，延迟容忍分钟级，接受部分失败（全量校准兜底）。主因是跨库解耦 + 主事务不被远程调用阻塞。
- 主体/组织做**双事实映射**（abstract_user + ADMIN_USER resource_entity；abstract_role(ORG/POSITION) + ADMIN_ORG resource_entity）——"谁能访问"与"谁能被管理"是正交语义，不可合并。
- type_definition 走运行时表（免版本发布）；biz_domain 仅做**管理分区**（防类型爆炸），不参与运行时鉴权。

**非目标**：不做实时强一致；不做跨租户授权；不做精确历史快照回放（recent-changes 给"可能影响"线索）；不做实时权限吊销（靠 TTL+版本递增）。

**已知意图存疑项（P1）**：domain_config 的 SCOPE/RELATION/BINDING 三种 config_type、PermQuery 的 forValidate/forResourceCheck 模式——均"预设但无调用方"。

---

## 2. 信任锚点结果（文档不一致清单）

文档自报数字与实际源码核实差异：

| # | 文档声明 | 实际 | 偏差 | 文档源 |
|---|---|---|---|---|
| D1 | permission-center API 107 个 | **109 个** | +2（AbstractRoleSync/AbstractUserSync 控制器未计入） | `implementation.md §1.2` 注释 `controller (19)` |
| D2 | admin-service API 21/22 个 | **90 个**（带 @RequestBody）/ 101 个（全 @PostMapping） | **+69~80** | `architecture.md §3.2` 表格"~75"、`admin-service-api-contract.md §1` "22"、`api-gap-analysis.md §合计` |
| D3 | permission-center 19 张表 | 19 张 ✅ | 0 | `permission-center.sql` |
| D4 | admin-service 18 张表（文件头注释） | **17 张**（分节序号跳过 4） | -1 | `admin-service.sql` 第 2 行注释 `(18 张表)` 错 |
| D5 | sync 端点 9 个 | **10 个**（4 对分领域 sync/full-sync + service-config/sync + resource-dependency/batch-sync） | +1 | `architecture.md` / `api-contract.md` |
| D6 | implementation.md "controller (19)" | 实际 **23 个** Controller（含 4 个 Sync 类） | +4 | `implementation.md §1.2` 列表过期 |
| D7 | `api-contract.md §6.2.2.6` 引用 §6.2.2.4，但 6.2.2.4 节序号晚于 6.2.2.6 出现 | 节序倒置 | — | `api-contract.md` |
| D8 | implementation.md "Mapper (18)" | 实际 18 个 ✅ | 0 | — |
| D9 | implementation.md "service/impl (20)" | 需对账，缺 ServiceSyncAppServiceImpl 等 | 需对账 | `implementation.md §1.2` |

**根本治理**：把数字从文档移出，改成脚本扫源码生成 `_metrics.md`（详见工作单 F-1.a）。

---

## 3. 待确认清单与用户答复（评审基准）

阶段 0 提交了 8 项"文档答不出、缺失会让评审结论悬空"的关键问题。用户答复（2026-06-17）已纳入评审基准：

| Q | 问题摘要 | 用户答复 | 评审基准修订 |
|---|---|---|---|
| Q1 | admin→perm 最终一致延迟容忍 | 分钟级可接受 | outbox 模型不列断链 |
| Q2 | 双事实映射部分失败可否接受 | 可接受（全量校准兜底） | 双事实结构合理 |
| Q3 | type_definition 为何运行时表 | 业务多变，免版本发布 | 不列编译时枚举化建议 |
| Q4 | biz_domain 是否有真实多租户定制 | 仅做管理分区，防类型爆炸 | 但 domain_config 中 SCOPE/RELATION/BINDING 仍无场景 → P1 |
| Q5 | 9 个 sync 端点为何不收敛 | 强类型隔离，维护性优先 | 不列收敛建议 |
| Q6 | PermQuery 8 模式是否都有调用方 | 部分预设，后期会调整 | 意图待确认 P1 |
| Q7 | scopeAll 端到端是否验证 | **未验证**，期望"调用方问题" | **P0 静默安全风险** |
| Q8 | DTO 双份维护是否需要合并 | 历史遗漏，可合并 | P2 改造确认 |

---

## 4. 工作单全景（6 项）

下面 6 个工作单按"产品/业务视角"重新打包了原始 25 项发现。每个工作单含：问题描述（场景化） / 方案选项 / 推荐 / 修改后效果。

### 4.1 工作单 A：权限变更"延迟生效" 🚨 ✅ 已确认（2026-06-17）

**包含原始发现**：P0-1（permission_version 大面积漏递增）、P0-3（SubjectDomainService 用 evictBatch 非事务感知）

#### 问题描述

**场景**：HR 在管理后台禁用了员工"张三"。点完保存，期望张三立刻不能再访问系统。

**实际**：张三可能在接下来 30 秒内仍能访问敏感接口——尽管数据库里"已禁用"。

**真实根因（用户与 AI 联合调研后修订）**：

原方案 A 的前提"Gateway 通过版本号判断是否刷新缓存"**与代码事实不符**。源码调研结论：

| 维度 | 实际 |
|---|---|
| `permission_version` 表粒度 | 租户 + 角色（不是用户级） |
| Gateway 缓存内容 | `(user, service, method, path) → Boolean` 单值，**不是权限快照** |
| Gateway 失效机制 | 纯 TTL（10 秒），**完全没有版本号比对** |
| Gateway 是否读 `permission_version` | **完全没有**（gateway 模块全文搜不到 version 字样的业务代码） |
| `increment()` 当前的实际作用 | 仅作为 permission-center 内部 L2 缓存 key 的后缀，让 key 自动失效 |

即：补 `increment` 漏调用是在堵一个"根本不通向 Gateway 的洞"。原方案 A2 注解切面亦无法精确表达"递归子角色 + 祖先 GROUP_ROLE + 所有持有用户"这种动态影响范围。

#### 已确认方案：A'（融合"快照模式 + ThreadLocal 收集 + 删除 version"）

##### A'-1 Gateway 改为"快照模式"

- Gateway 缓存 key 从 `(user, path) → bool` 改为 `user → InterfaceSnapshot`（用户的全部可访问接口集合）
- 鉴权时本地内存匹配（O(1) hash 查找），不再每条路径打 RPC
- 已有现成接口 `POST /api/perm/auth/interface-snapshot` 可直接接入
- 缓存失效采用：**短 TTL（30-60 秒）兜底 + Redis pub/sub 主动广播失效信号**（见 A'-2）

##### A'-2 写路径用 `PermissionChangeContext`（ThreadLocal）+ AOP 统一处理

```java
// DomainService 在执行写操作时显式登记影响范围（领域专家最懂）
public void disableRole(Long tenantId, Long roleId) {
    role.setStatus(0);
    abstractRoleMapper.update(role);

    Set<Long> affectedUsers = collectAffectedUsers(tenantId, roleId);   // 含祖先 GROUP_ROLE 的持有者
    Set<Long> affectedRoles = collectDescendantRoles(tenantId, roleId); // 含子角色

    PermissionChangeContext.markAffectedUsers(tenantId, affectedUsers);
    PermissionChangeContext.markAffectedRoles(tenantId, affectedRoles);
}
```

AppService 入口 AOP 在事务提交后统一处理：
- 取出 ThreadLocal 中的 userIds → 批量 evict `EFFECTIVE_ROLES` / `INTERFACE_SNAPSHOT`
- 取出 roleIds → 批量 evict `ROLE_PERM_SNAPSHOT`
- **通过 Redis pub/sub 广播 `PermInvalidateEvent(tenantId, userIds)` 给所有 Gateway 副本**
- 事务回滚或异常 → 什么都不做（保证缓存不被错误数据污染）

##### A'-3 完全删除 `permission_version` 机制

理由：Gateway 不读它、内部缓存 evictBatch 已够用、key 拼装可用 evict 替代。

删除清单：
- 表：`permission_version`（schema 移除）
- 实体：`PermissionVersion`、`PermissionVersionTableDef`
- Service：`PermissionVersionDomainService` / `Impl` / `AppService` / `Impl`
- Controller：`PermissionVersionController`
- DTO：`PermissionVersionQueryReq` / `PermissionVersionResp`
- Mapper：`PermissionVersionMapper`
- 4 处 `increment` 调用（`PermissionGrantAppServiceImpl`）
- 缓存目录条目：`PermCacheCatalog.PERMISSION_VERSION`、key 中 `:{permissionVersion}` 后缀
- 设计文档段落：`overview.md §"缓存与一致性"`、`implementation.md §5.1/5.2` 的 version 描述、`project-rules.md` 的相关职责段

##### A'-4 失效广播实现（采用方案 2：TTL + Redis 广播推送）

- permission-center afterCommit 阶段：
  - `redissonClient.getTopic("perm:invalidate").publish(PermInvalidateEvent)`
- Gateway 启动时订阅该 topic：
  - 收到事件后，根据 `userIds` 批量 evict 本地 Caffeine 中对应 key
- 事件载荷：`{ tenantId, userIds[], roleIds[] (可选), eventId, occurredAt }`
- 失败兜底：广播失败不影响事务；30 秒 TTL 自然过期最终一致

#### 修改后会是什么样

| 视角 | 改前 | 改后 |
|---|---|---|
| **HR 禁用张三** | 最坏 30 秒延迟 | 1-2 秒内全 Gateway 副本失效；TTL 30-60 秒兜底 |
| **Gateway 性能** | 每条 path 都 RPC（除非命中 10s 缓存） | 用户首次访问拉一次快照，后续全本地命中 |
| **新功能开发者** | 须记得 `afterCommit { increment + evict }`（易漏静默） | DomainService 里 `markAffected*`，剩下 AOP 自动 |
| **运维认知** | "version 号是什么 / 为何不起作用"困惑 | 没有 version 概念，只有"快照 + TTL + 失效广播" |
| **代码净增减** | — | 减：删除 PermissionVersion 全套（~8 个文件 + 4 处调用）<br/>增：`PermissionChangeContext` + AOP + Redis topic 订阅器 |

#### 估算工期：3-4 天

> 工期上调：相比原 A2 的 1-2 天，新方案多了"Gateway 快照模式改造 + Redis 广播订阅"，但**删除 version 机制**会抵消部分工作量。整体仍可控。

---

### 4.2 工作单 B：数据权限"空集即全部" 🚨 ✅ 已确认（2026-06-17）

**包含原始发现**：P0-2（scopeAll 端到端未验证）

#### 问题描述

**场景**：example-service 调权限中心："用户 u-10001 在'销售报表'里能看哪些部门?"

**权限中心返回**（三种情况都可能）：
1. `{ allowed: true, items: [{部门 A}, {部门 B}] }` → 只能看 A、B 两个部门
2. `{ allowed: true, items: [], scopeAll: true }` → 能看全部部门（全量授权）
3. `{ allowed: true, items: [] }` → 没有任何范围权限，**应该禁止查询**

**业务方常见误读**：看到 `items: []` 就写"那不加 SQL 过滤呗" → 查了所有部门 → **数据泄露**。

**当前文档态度**：反复警告"空 ≠ 全量，必须看 scopeAll"（`api-contract.md §6.7`、`overview.md §"范围权限"`），Q7 答复是"调用方写错就是调用方的问题"。

**问题在于**：作为开源权限平台，把数据权限的语义责任甩给业务方，等于在最敏感的能力上不做防呆。`improvement-plan.md` 痛点 #4 已明确"动态数据权限待验证"，端到端从未跑通。

#### 已确认方案：B2 协议层防呆（B3 SDK 不做、B4 延后）

##### B2 返回结构改造（采纳）

返回体由 `{allowed, items[], scopeAll}` 改为：

```json
{
  "allowed": true,
  "scopeMode": "INSTANCE | ALL | NONE",
  "items": [...],          // 仅 INSTANCE 时非空
  "scopeTypeCodes": [...]  // 可选：哪些资源类型在 ALL 范围内
}
```

- 强制业务方按枚举 `scopeMode` 三分支编程（`switch`），编译期暴露遗漏分支
- `scopeMode=NONE` 时业务方应直接返回空结果，不发 SQL
- `scopeMode=ALL` 时业务方不加范围过滤
- `scopeMode=INSTANCE` 时业务方使用 `items[]` 加 `IN(...)` 过滤

兼容策略（已确认）：**直接换**。理由：项目未上线，评审基准 §"非目标"已声明不考虑旧数据兼容；半兼容方案（新老并存 / 增量字段）保留歧义，违背 B2 全部价值。

##### B3 SDK 强制 helper（不做）

理由（用户决策）：
- 接入方不一定使用 QueryWrapper（MyBatis-Flex 风格）
- 接入方甚至不一定是 Java 程序，权限中心定位是**多语言通用平台**
- Java SDK helper 只能服务 Java 业务方，与"通用权限事实源"定位不符
- 改用文档 + 协议层强类型（B2）覆盖更广

##### B4 端到端测试（延后到 example-service）

- B4 测试归属于 example-service 模块（验证业务方接入正确性）
- example-service **暂不实现**，本工作单不交付 B4
- 待 example-service 立项时，将"无范围授权 → 调用查询 → 必须返回空"列为黄金路径测试基线

#### 修改后会是什么样

| 视角 | 改前 | 改后（仅 B2） |
|---|---|---|
| **业务接入开发者** | 必须读懂 scopeAll 文档；忘看就泄露 | 拿到 `scopeMode` 枚举，IDE 提示三种取值；遗漏分支编译器 / 静态分析告警 |
| **多语言接入方** | Java/Go/Python/Node 都得自己处理空集语义 | 协议返回的是显式枚举，任意语言都能 switch/match，不再有"空 vs 全量"二义 |
| **代码评审** | 难以判断"items 空"分支是否正确 | 评审者能看到 `scopeMode` 是否被覆盖完整 |
| **平台定位** | 强依赖 SDK 安全性 | 协议自身安全，多语言生态对等 |

#### 估算工期：1-1.5 天

> 工期下调：相比原 2-3 天（B2+B3+B4），现仅做 B2 协议改造 + 文档同步。B4 延后到 example-service，B3 取消。

---

### 4.3 工作单 C：Gateway 失联兜底 🚨 ✅ 已确认（2026-06-17）

**包含原始发现**：P0-4（Gateway 失联默认行为未决）

#### 问题描述

**场景**：权限中心因 K8s 滚动升级或网络抖动，连续 5 分钟没响应 Gateway 鉴权调用。

**问题**：Gateway 此时应该：
- (A) 继续放行所有请求（可用性优先）?
- (B) 拒绝所有请求（安全优先）?
- (C) 用 30 秒前的缓存继续兜一段（折中）?

**现状**：`gateway.md` 全篇未写 permission-center 不可达时的默认行为；`api-contract.md §6.2` 也未明确。实现者按个人判断写。日后翻案成本极高。

#### 已确认方案：C1 + C2（默认 fail-closed，支持 stale-allow 可选）

##### C1 三模 fail-mode 配置

```yaml
gateway:
  perm:
    fail-mode: closed              # closed | open | stale-allow，默认 closed
    stale-grace-seconds: 30        # stale-allow 模式下，过期快照可继续使用的秒数
```

| 模式 | 行为 |
|---|---|
| **closed**（默认） | permission-center 不可达 → 直接拒绝请求（403/503） |
| **open** | permission-center 不可达 → 全部放行（不推荐，仅 demo 场景） |
| **stale-allow** | 优先用 Caffeine 中"已过期但未驱逐"的快照继续兜底；超 `stale-grace-seconds` 后转 closed |

##### C2 监控指标

新增 metrics：
- `gateway.perm.unreachable.count`（permission-center 调用失败次数）
- `gateway.perm.fallback.closed.count`
- `gateway.perm.fallback.open.count`
- `gateway.perm.fallback.stale.count`

触发 stale 兜底时 `WARN` 日志 + Prometheus 告警规则。

##### 决策理由

- **默认 closed**：权限平台的本质——不知道有没有权限时默认拒绝；银行金库电子锁断电默认锁死，不开门
- **同时实现 stale-allow**：与工作单 A 的"快照模式"天然契合；Gateway 本就缓存 `user → InterfaceSnapshot`，让"刚过期的快照"再续命 30 秒近乎零成本，能显著提升抖动韧性；运维可在认知风险后切换到此模式
- **保留 open**：仅供内网 demo / 离线环境 escape hatch，文档明确警告勿在生产使用

##### 与工作单 A 的协作

- A'-1 Gateway 改"快照模式"后，缓存对象从 `Boolean` 变成 `InterfaceSnapshot`，正好支持 stale-allow（Boolean 无所谓"过期但未驱逐"，快照可以）
- A'-4 Redis 广播失效时，Gateway 收到事件后既要清 L1，也要让对应快照标记为"已显式失效"（不允许 stale-allow 继续使用）—— 即"权限主动撤销"高于"服务不可达兜底"

> **待设计（设计文档全面审计 2026-06-20 / S-006）**：上述"失效标记"机制的具体规范待设计，包括：(1) 标记维度（按 userId 还是 tenant+roleId，事件载荷仅 userIds 时如何处理影响范围更大的 roleIds）；(2) 标记生命周期（快照 TTL 过期后被同一用户重建时是否清理标记）；(3) 订阅恢复期事件补偿（Gateway 启动/重连 Redis 错过的事件如何补漏，是否需要 last-known-eventId 或强制清空本地快照）。落地前需补此规范。

#### 修改后会是什么样

| 视角 | 改前 | 改后 |
|---|---|---|
| **运维** | 抖动时不知发生什么；用户报"打不开" | Grafana 立刻看到 `fallback.{closed,stale}.count` 飙升 → 主动定位 |
| **SRE / 合规** | 文档没说默认行为 | SLA 中明确"安全优先 → 默认 fail-closed"，可写入合规审计 |
| **抖动期用户体验** | 不一致（个人判断决定开关） | 默认拒绝时返回标准 503 + 提示"权限校验暂不可用"；运维切 stale-allow 时大多数用户无感 |
| **集成测试** | 无场景模拟 | "杀 permission-center → Gateway 应 503"成为 CI 测试基线 |

#### 估算工期：0.5 天

---

### 4.4 工作单 D：防开发者"忘了"的机制化 ⚙️ ✅ 已确认（2026-06-17，全部采纳）

**包含原始发现**：P0-5（type_value 碰撞）、P1-3（businessKey 散落 15 处）、P1-8（OperationCode 漏配）、P1-10（payload_version 升级）

#### 共同根因

新人开发者（或 AI 生成代码）在新增功能时，总有一些"按规矩必须做"的步骤——既不在编译器视野内，也不在 IDE 提示里——靠 README 和 code review 兜底。漏一次就是 bug。下面 4 项都属于"靠纪律不靠机制"的隐患，对开源贡献者尤为不友好。

#### 已确认方案：D1 + D2 + D3 + D4 全做

##### D1 类型号（type_value）自动分配

- UI 不再显示 type_value 字段
- `TypeDefinitionAppServiceImpl.create` 加 `nextAvailableTypeValue(tenantId, typeKey)` 方法
- 软删的号**不复用**（保留为墓碑，避免历史数据混淆）
- 工期：≈ 0.5 天

##### D2 业务键 helper（`BusinessKeys`）

在 `perm-common` 加工具类：

```java
BusinessKeys.userSubject("ADMIN_USER", "10001")
  // → 标准化串
BusinessKeys.parseUserSubject("...")
  // → record(typeCode, externalId)
```

- 替换 admin-service 中 15+ 处散落拼接
- 加契约测试 `BusinessKeysParityTest`：对每种 entityKind 跑"拼接 → 解析 → 等于原对象"
- 工期：≈ 1 天

##### D3 操作码 `@AppliesTo` 编译期 + 启动期双重校验

```java
@AppliesTo({ResourceTypeCode.ADMIN_ORG, ResourceTypeCode.ADMIN_USER})
public static final String EXPORT = "EXPORT";
```

启动期校验内容：
1. 所有 OperationCode 常量必须带 `@AppliesTo`，否则启动失败
2. 常量声明的资源类型集 vs `OrgOperationCodeMapper` 配置一致性
3. 代码声明的常量集 vs 数据库 seed（`seed-admin-operations.sql`）一致性

漏配 → 启动失败（fail-fast），杜绝静默失效。

工期：≈ 1-1.5 天

##### D4 `SyncHandler` 版本声明 + 启动期校验

```java
public interface SyncHandler {
    int supportedPayloadVersionMin();
    int supportedPayloadVersionMax();
    default boolean supports(int v) {
        return v >= supportedPayloadVersionMin() && v <= supportedPayloadVersionMax();
    }
}
```

启动期扫描：
- 所有 Handler 版本范围**不允许出现"无 Handler 覆盖的版本"**（断层即报错）
- 重叠允许（同一 version 有多个 Handler，由分发器选择最高优先级）

管理 UI：列出"未匹配 payload_version 的同步任务"作为运维可见性。

工期：≈ 1 天

#### 修改后会是什么样

**新成员开发新功能视角**：
- 加新类型：UI 自动分配，不需要懂 type_value 内部规则（D1）
- 加新接口要业务键：用 `BusinessKeys.xxx`，不可能拼错（D2）
- 加新操作码：常量上写 `@AppliesTo(...)` + 在分发器加映射，**漏了就启动不起来**（D3）
- 升级同步协议：Handler 声明版本范围，启动期就发现版本断层（D4）

**整体效果**：从"小心翼翼地查文档怕漏"到"按 IDE 提示写就对"。

#### 估算工期：3.5-4 天

---

### 4.5 工作单 E：清理"预设但没人用"的功能 🧹 ✅ 已确认（2026-06-17，按推荐处理）

**包含原始发现**：P1-1（domain_config 5 种 type）、P1-2（PermQuery 4 种工厂方法无调用）、P3-2（RocketMQ 未启用）、P3-3（auto-grant 未实现）

#### 问题描述

文档里出现的若干设计项，实际上代码没用、对外没暴露，但仍占据"运维认知"和"新成员困惑"的成本。

| 项 | 现状 | Q&A 已确认 |
|---|---|---|
| `domain_config` 5 种 config_type 中 SCOPE/RELATION/BINDING | 引擎从不读取 | Q4: 业务域只做分类 |
| `PermQuery` 中 forValidate / forResourceCheck | 无对外接口 | Q6: 预设，后期会调整 |
| RocketMQ 设计文档保留（§15） | 当前不用 | architecture.md 说"未来事件预留" |
| 自动授权（auto-grant） | 表结构有，代码 TODO | improvement-plan §3.1 痛点 #3 |

#### 已确认方案：分类处理（按推荐）

| 项 | 处理 | 说明 |
|---|---|---|
| **E1 `domain_config` SCOPE/RELATION/BINDING** | **删除** | Q4 已确认 biz_domain 只做管理分区，不参与运行时鉴权；这三种 config_type 的设计前提（运行时使用）已不成立 |
| **E2 `PermQuery.forValidate` / `forResourceCheck`** | **删除** | Q6 已确认无调用方且后期会调整；同步移除引擎 dispatch 表中对应分支 |
| **E3 RocketMQ 设计文档段落（architecture.md §15）** | **加 footnote 保留** | 注明"**当前未启用**，作为未来扩展点保留"；不删段落，保留架构参考 |
| **E4 auto-grant 自动授权** | **保留 TODO + 排期到 Phase X** | 核心差异化能力，仅未排优先级；`implementation.md` 末尾加"未实现"明显标记，`core-flows.md` 相关流程图加 ⚠️ 标 |

#### 修改后会是什么样

**新成员第一周读文档视角**：
- 不再纠结"SCOPE_CONFIG 是干嘛的" —— 根本不存在
- 不再纠结"forValidate 和 forAuthCheck 区别" —— 只剩该有的工厂方法
- 看到 RocketMQ 段落底下有"当前未启用" —— 知道是基础设施铺垫
- 看到 auto-grant 有"排期到 Phase X" —— 知道是规划中

**文档体积**：约减少 15%。
**代码体积**：`domain_config` 写路径减 3 个 if 分支；`PermQuery` 删 2 个工厂方法 + 引擎管线减 1-2 处分支判断。

#### 估算工期：2-3 天

---

### 4.6 工作单 F：文档准确性 + 代码层简化 🧹 ✅ 已确认（2026-06-17，全部采纳）

**包含原始发现**：D1-D10（文档数字不一致）、P1-4（resource_entity ownership 双轨）、P1-6（DTO 双份）、P1-7（resource_entity 三字段冗余）、P1-11（runbook 缺）、P2-1（operation_log 与 change_log 关联）、P2-2（permission_conflict_rule 稀疏列）、P2-3（PermissionGrantAppService 14 依赖）、P2-4（Gateway/perm 协议优化）、P2-5（GROUP_ROLE JSONB → 关联表）

#### 共同问题

经过前面 5 个工作单的功能性改造，剩下的是"系统讲清楚自己"的事——文档说一套、代码做另一套；同一份信息散落多处。每件单看都不致命，但累积起来 = "新人怎么读都读不准、运维不敢按按钮"。

#### 已确认方案：F1.a ~ F1.e 整组推进

##### F1.a 文档数字自动化（采纳）

1. 写 Maven 插件 / shell 脚本，扫描 `@PostMapping` / `@GetMapping` / `CREATE TABLE` 等模式
2. 生成 `docs/design/_metrics.md`（机器维护文件，PR 自动更新）
3. 正文中的数字（API 数、表数、Controller 数等）全部改成"详见 _metrics.md"或脚注引用
4. CI 加文档对账测试，**数字过时 → PR 失败**

工期：≈ 1 天（含 CI 集成）

##### F1.b DTO 单源化（采纳）

1. `permission-center` Controller 直接消费 `perm-common` 的 Req/Resp
2. 删除 `permission/dto/req` 内部副本（~30 个类）
3. `PermCommonReqContractTest` 可下线

工期：≈ 1-1.5 天

##### F1.c ownership 单一事实源（采纳）

1. `service-config/sync` 路径迁移用 `sync_metadata` 记录 ownership
2. `resource_entity` 三字段（`sync_owner`、`sync_owner_id`、`sync_at`）保留为"展示属性"或直接删除
3. 索引 `idx_resource_entity_sync_owner` 删除

工期：≈ 0.5-1 天

##### F1.d 操作日志-变更日志关联（采纳）

1. `permission_change_log.request_id` 由 nullable 改为 NOT NULL
2. 一次写操作产生 1 条 `operation_log` + N 条 `permission_change_log`，通过 `request_id` JOIN
3. 管理 UI 提供"审计详情"一键展开

工期：≈ 0.5 天

##### F1.e 全量校准 runbook（采纳）

新建 `docs/ops/runbook-full-sync.md`，包含：
- 触发前置检查清单（scope 大小、租户备份、目标确认）
- 触发命令模板
- 实时监控指标（`deactivatedCount` 阈值告警等）
- 异常回滚步骤（**全量校准本身不可回滚** —— 只能反向 sync 补回）
- 真实故障案例 + 处理回顾（占位，未来填充）

> **追加（2026-06-20 审计 S-009）**：本 runbook 派生时一并补「同步任务故障 runbook」：
> - FAILED 任务告警阈值与运维 SLA（FAILED 数 > N 触发 P1 告警；N 待定）
> - 永久 FAILED 任务（NON_RETRYABLE / SECURITY_DENIED）对业务事实永久缺失的影响声明（如新员工 UPSERT 失败 → 该员工所有鉴权拒绝）
> - 自动 vs 手动修复判定（rebuild-from-fact 手动入口的触发时机）
> - SECURITY_DENIED 必须先排根因再决定是否 rebuild

工期：≈ 0.5-1 天（含追加的故障 runbook）

#### 修改后会是什么样

**新成员视角**：
- 第一周读文档，数字与代码完全对得上
- 对"DTO 双份"的疑问消失 —— 只有一份
- 看到"ownership 字段"知道唯一去 `sync_metadata` 查
- 审计页面一键看完整链路

**运维视角**：
- 全量校准从"高危黑盒操作"变成"按 runbook 操作"
- 文档每次 PR 自动对账，不会出现"文档说有但代码没了"

#### 估算工期：3-4.5 天

---

## 5. 整体路线图

| 顺序 | 工作单 | 工期 | 优先级 | 推进周次 |
|---|---|---|---|---|
| 1 | A 权限延迟生效（方案 A'：快照模式 + ThreadLocal + 删除 version） | 3-4 天 | 🚨 P0 | 第 1 周 |
| 2 | B 数据权限空集（仅 B2 协议改造；B3 不做、B4 延后） | 1-1.5 天 | 🚨 P0 | 第 1 周 |
| 3 | C Gateway 兜底 | 0.5 天 | 🚨 P0 | 第 1 周 |
| 4 | D 防呆机制（D1+D2+D3+D4 全做） | 3.5-4 天 | ⚙️ P1 | 第 2 周 |
| 5 | E 清理预设 | 2-3 天 | 🧹 P1 | 第 3 周 |
| 6 | F 文档+简化（F1.a~F1.e 全做） | 3-4.5 天 | 🧹 P2 | 第 3 周 |

**总工期**：约 2-3 周。

---

## 6. 跨重心共性问题

| 共性 | 表现 | 工作单覆盖 |
|---|---|---|
| **C1 隐式契约依赖纪律** | P0-1 increment 缺失、P0-3 evictBatch 错用、P1-3 businessKey 散落、P1-10 payload_version | A、D |
| **C2 配置项预设但无实现** | P1-1 domain_config、P1-2 PermQuery、P3-3 auto-grant | E |
| **C3 文档自报数字与实现脱节** | D1-D10 全部 | F-1.a |
| **C4 调用方误用易导致静默放行** | P0-2 scopeAll、P1-9 PermQuery 选错、P1-8 OperationCode 漏配 | B、D |
| **C5 双轨/双副本** | P1-4 sync ownership、P1-6 DTO、P1-7 resource_entity 字段 | F |

**最重要的根因**：C1 + C4 是当前最大共性风险。**修复方向是把"约定"升级为"机制"**：AOP、编译期校验、强类型 SDK helper、契约测试。Sprint 1+2 落地后可一次性消除一大批静默失败模式。

---

## 7. 新增实体全链路改动对比

### 7.1 新增"资源类型"（如 PROJECT）

| 步骤 | 改进前 | 改进后（A+D+E 实施后） |
|---|---|---|
| 1. type_definition | 选 type_value（碰撞风险） | UI 自动分配 |
| 2. 操作码 seed | 写 SQL 注册 | 同前 + 启动期校验 |
| 3. resource_entity 同步 | 调 `/api/perm/resource-entity/sync` | 同前 |
| 4. domain_config | 5 种类型选哪个 | 只剩 SUB_PERM/CLASSIFY |
| 5. permission_version | 手工 increment | **删除（无 version 概念）** |
| 6. 业务键 | 各处拼字符串 | `BusinessKeys.resource(...)` |
| 7. UI/SDK | mock + Feign DTO + Req/Resp 双份 | perm-common 共享 DTO |
| **改动文件数** | ~20 文件 | **~8 文件** |

### 7.2 新增"操作码"（如 EXPORT）

| 步骤 | 改进前 | 改进后 |
|---|---|---|
| 1. AdminOperationCode 常量 | 加常量 | 加常量 + `@AppliesTo` |
| 2. seed-admin-operations.sql | INSERT 一行 | 同前 + 启动期对账 |
| 3. service 实现 | 校验 + 手工 increment | AOP 自动 |
| 4. OrgOperationCodeMapper | 手工补映射，漏了静默 | 漏了启动失败 |
| **改动文件数** | ~10 文件 | **~7 文件** |

### 7.3 新增"业务域"（如 finance）

| 步骤 | 改进前 | 改进后 |
|---|---|---|
| 1. biz_domain | API 创建 | 同前 |
| 2. domain_config CLASSIFY | 加配置 | 同前 |
| 3. domain_config SCOPE/RELATION/BINDING | **不知道是否要写** | **不存在** |
| 4. domain_config SUB_PERM | 若需子权限 | 同前 |
| **改动文件数** | ~6 文件 | **~4 文件** |

---

## 8. 已知问题根因核查

| Plan | 状态（声明） | 根因核查结果 | 后续 |
|---|---|---|---|
| **user-role-proxy-fix-plan** | "已完成，待最终验收" | M1-M13 + S1-S3 全完成；EXT-7/EXT-8 DEFERRED 与本评审 P0-1 间接相关 | ~~复发预警纳入工作单 D~~（**2026-06-20 审计 S-024 修正**：工作单 D 实际仅含防漏配/防拼错/防版本断层，未含 EXT-7/EXT-8 批处理性能复发预警；该两项当前**无主**，待单独立项或纳入后续评审）|
| **improvement-plan** | "Phase 1 第一阶段完成，P0 90%" | Phase 2 自动授权/动态数据权限验证未跑通；与 P0-2、P3-3 直接相关 | 工作单 B、E 覆盖 |
| **api-gap-analysis** | "16 个 🔧 接口契约定稿" | 仅覆盖"组织与用户"页 22 个接口；"21" 数字不可信 | D2 已纳入文档不一致清单，工作单 F 解决 |

---

## 9. 验收标准

本评审作为"评审 + 方案确认"阶段产出。后续每个工作单单独立 plan 推进，本文档不替代具体实施计划。

本文档归档前需满足：

- [ ] 6 个工作单的方案均经用户确认（采纳/调整/不做）
- [ ] 每个采纳方案派生独立的 plan 文档（如 `docs/plans/permission-version-aop-plan-{date}.md`）
- [ ] 派生 plan 在 `docs/plans/README.md` 索引中登记
- [ ] 用户决策记录追加到本文档 §11

---

## 10. 关联文档索引

### 设计文档（评审输入）

- `docs/design/architecture.md`
- `docs/design/project-rules.md`
- `docs/design/permission-center/{overview, implementation, core-flows, api-contract}.md`
- `docs/design/services/{admin-service, admin-service-api-contract, gateway}.md`
- `docs/design/cross-service/{README, admin-permission-sync}.md`
- `docs/design/{default-org-tree-user-lifecycle, org-user-permission-contract}.md`
- `docs/design/schema/{permission-center, admin-service, example-service, seed-admin-operations, seed-perm-operations}.sql`

### Plan 根因核查（评审输入）

- `docs/plans/user-role-proxy-fix-plan.md`
- `docs/plans/improvement-plan.md`
- `docs/plans/api-gap-analysis.md`

### 派生 Plan（评审输出）

派生 plan 创建后追加到此处。截至 2026-06-19 复核，**6 个工作单（A~F）尚未派生独立 plan 文档**；§11 决策记录中"已采纳方案"仅完成评审基准确认，落地实施需后续单独立项。

- (尚未派生)

---

## 11. 决策记录

> **落地状态：A/B/C 已派生 plan，D/E/F 暂缓（2026-06-20 审计 S-012 更新）**
>
> 6 个工作单 A~F 的方案均已 ✅ 采纳并记录于下表。
>
> **P0 三件已重启派生**（重启条件 1「v3.5 简化版设计契约稳定」已达成）：
> - 工作单 A → [perm-cache-invalidation-plan.md](perm-cache-invalidation-plan.md)（待启动）
> - 工作单 B → [scope-mode-migration-plan.md](scope-mode-migration-plan.md)（待启动）
> - 工作单 C → [gateway-fail-mode-plan.md](gateway-fail-mode-plan.md)（待启动，依赖工作单 A 快照模式）
>
> **D/E/F 暂缓**：D（防呆机制）/ E（清理预设）/ F（文档+简化）作为 P1/P2 跟进，暂不派生。重启条件：
> 1. A/B/C 落地完成
> 2. 前端 Phase 1 收尾完成
> 3. 出现 D/E/F 工作单覆盖场景的生产事故
>
> **待落地决策项清单**（D/E/F 重启时直接对照执行）：
>
> | 工作单 | 待落地内容 | 关联审计决策 |
> |---|---|---|
> | D | D1 type_value 自动分配 + D2 BusinessKeys + D3 @AppliesTo + D4 SyncHandler 版本声明 | — |
> | E | E1 删 domain_config SCOPE/RELATION/BINDING + E2 删 PermQuery 2 工厂 + E3 RocketMQ footnote + E4 auto-grant TODO 排期 | — |
> | F | F1.a 文档数字脚本化 + F1.b DTO 单源 + F1.c ownership 单源 + F1.d 日志关联 + F1.e 全量校准 runbook（含同步任务故障 runbook，S-009）| S-009=B（追加故障 runbook）|

| 日期 | 决策点 | 决策 | 备注 |
|---|---|---|---|
| 2026-06-17 | Q1 admin→perm 延迟容忍 | 分钟级可接受 | 评审基准 |
| 2026-06-17 | Q2 双事实部分失败 | 可接受（全量校准兜底） | 评审基准 |
| 2026-06-17 | Q3 type_definition 运行时表 | 保留 | 业务多变，免发版 |
| 2026-06-17 | Q4 biz_domain 定位 | 仅做管理分区 | 但 domain_config SCOPE/RELATION/BINDING 待删 |
| 2026-06-17 | Q5 9 sync 端点收敛 | 不收敛 | 强类型隔离优先 |
| 2026-06-17 | Q6 PermQuery 8 模式 | 部分预设删除/排期 | 工作单 E 处理 |
| 2026-06-17 | Q7 scopeAll 端到端 | 未验证，列 P0 | 工作单 B 处理 |
| 2026-06-17 | Q8 DTO 双份维护 | 可合并 | 工作单 F-1.b 处理 |
| 2026-06-17 | **A-1 Gateway 缓存模型** | **改为快照模式**（user → InterfaceSnapshot），不再用 (user,path) → bool | 工作单 A 决策 |
| 2026-06-17 | **A-2 写路径影响范围收集** | **ThreadLocal `PermissionChangeContext` + AOP afterCommit** | 拒绝注解方案（无法表达递归影响） |
| 2026-06-17 | **A-3 `permission_version` 表与服务** | **完全删除**（表/实体/Service/Controller/DTO/Mapper/4 处 increment/缓存目录条目/文档段落） | Gateway 不读、内部 evict 已够。**2026-06-20 审计 S-001=B 确认落实**：v3.5 §9.2 已登记此决策；落地待工作单 A 派生 plan（见 §11 顶部暂缓说明）|
| 2026-06-17 | **A-4 Gateway 失效广播** | **TTL（30-60s）+ Redis pub/sub 主动推送** | topic：`perm:invalidate`；事件含 tenantId+userIds |
| 2026-06-17 | **B-1 范围权限返回结构** | **直接换为 `{allowed, scopeMode, items[], scopeTypeCodes[]}`**（不做新老兼容） | 项目未上线；半兼容方案保留歧义 |
| 2026-06-17 | **B-2 SDK helper（perm-data-starter）** | **不做** | 接入方未必用 QueryWrapper / 未必是 Java；通用平台定位 |
| 2026-06-17 | **B-3 端到端黄金路径测试** | **延后归 example-service**；example-service 暂不立项 | 待 example-service 启动时一并实现 |
| 2026-06-17 | **C-1 Gateway 失联兜底默认值** | **fail-closed**（安全优先） | 权限平台本质：不知道权限时默认拒绝 |
| 2026-06-17 | **C-2 是否实现 stale-allow** | **实现** | 与 A'-1 快照模式天然契合，提升抖动韧性 |
| 2026-06-17 | **C-3 监控指标** | **加 4 项 metric + Prometheus 告警** | unreachable / fallback.{closed,open,stale}.count |
| 2026-06-17 | **D-1 type_value 分配** | **系统自动分配，软删不复用** | UI 不再暴露此字段 |
| 2026-06-17 | **D-2 业务键工具类** | **`perm-common.BusinessKeys` + `BusinessKeysParityTest`** | 替换 admin-service 15+ 处散落拼接 |
| 2026-06-17 | **D-3 操作码 `@AppliesTo`** | **启动期三重校验**（注解 / Mapper / seed 一致性）；漏配即启动失败 | fail-fast，杜绝静默失效 |
| 2026-06-17 | **D-4 SyncHandler 版本声明** | **接口加 min/max + 启动期断层检测 + UI 暴露未匹配任务** | 协议演进可见、可控 |
| 2026-06-17 | **E-1 domain_config SCOPE/RELATION/BINDING** | **删除**（代码、文档、UI 选项） | biz_domain 只做管理分区，运行时鉴权前提不成立 |
| 2026-06-17 | **E-2 PermQuery forValidate/forResourceCheck** | **删除工厂方法 + 引擎 dispatch 分支** | 全代码库无调用 |
| 2026-06-17 | **E-3 RocketMQ 段落** | **加 footnote 保留**（"当前未启用，未来扩展点"） | 不删，保留架构参考 |
| 2026-06-17 | **E-4 auto-grant** | **保留 TODO + 排期 Phase X**；implementation/core-flows 加未实现标 | 核心差异化能力 |
| 2026-06-17 | **F-1.a 文档数字自动化** | **采纳**：脚本扫描 → `docs/design/_metrics.md` + CI 对账 | 文档/代码永远一致 |
| 2026-06-17 | **F-1.b DTO 单源化** | **采纳**：删除 permission-center 内部 ~30 个 DTO 副本 | Q8 已确认 |
| 2026-06-17 | **F-1.c ownership 单源** | **采纳**：迁移到 `sync_metadata`；resource_entity 三字段+索引清理 | — |
| 2026-06-17 | **F-1.d 操作日志-变更日志关联** | **采纳**：`permission_change_log.request_id` 改 NOT NULL；UI 一键展开 | — |
| 2026-06-17 | **F-1.e 全量校准 runbook** | **采纳**：新建 `docs/ops/runbook-full-sync.md` | 高危黑盒操作 → 标准化流程 |

工作单方案确认（待）：

| 日期 | 工作单 | 决策（采纳/调整/不做） | 备注 |
|---|---|---|---|
| 待 | A 权限延迟生效 | ✅ **采纳方案 A'**（2026-06-17） | Gateway 改快照模式 + ThreadLocal 收集影响范围 + 删除 permission_version；失效用 TTL + Redis 广播推送 |
| 待 | B 数据权限空集 | ✅ **采纳 B2，B3 不做，B4 延后**（2026-06-17） | 仅做协议层改造（scopeMode 枚举）；不做 Java SDK helper（多语言定位）；端到端测试归 example-service，后者暂未实现 |
| 待 | C Gateway 兜底 | ✅ **采纳 C1+C2，默认 fail-closed，实现 stale-allow**（2026-06-17） | 三模配置（closed/open/stale-allow）+ 监控指标；与 A'-1 快照模式协作 |
| 待 | D 防呆机制 | ✅ **D1+D2+D3+D4 全部采纳**（2026-06-17） | 类型号自动分配 + BusinessKeys 工具 + @AppliesTo 启动期校验 + SyncHandler 版本声明 |
| 待 | E 清理预设 | ✅ **按推荐分类处理**（2026-06-17） | 删 domain_config SCOPE/RELATION/BINDING + PermQuery 2 工厂；RocketMQ 加 footnote 保留；auto-grant 保留 TODO + 排期 Phase X |
| 待 | F 文档+简化 | ✅ **F1.a~F1.e 全部采纳**（2026-06-17） | 数字脚本化 + DTO 单源 + ownership 单源 + 日志关联 + full-sync runbook |

---

## 12. 归档条件

满足以下任一时归档到 `docs/archive/YYYY-MM-DD/`：

- 6 个工作单的派生 plan 全部完成且稳定结论已沉淀到 `docs/design/`
- 评审被新一轮评审替代
- 评审只剩历史追溯价值

归档后必须：
- 在 `docs/archive/YYYY-MM-DD/README.md` 说明评审结论的当前权威入口
- 在 `docs/plans/README.md` 移除本文档索引行
