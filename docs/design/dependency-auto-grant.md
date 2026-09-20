---
doc_type: design
title: 权限依赖声明与自动授权（简化方案）
status: adopted
domain: access-service
supersedes: docs/archive/2026-09-20/dependency-auto-grant-path-design.md
last_reviewed: 2026-09-20
---

# 权限依赖声明与自动授权（简化方案）

> 本稿为已采纳的实施方向：资源同步与依赖声明保持独立，SDK 协调可选；保留写时物化，来源按需解释，不建立逐种子逐完整路径的持久 support。原完整路径存储、强制两步接入和跨 owner 手工 override 口径由本稿取代。
>
> 交付状态：服务认证前置 T-PERM-070 已完成；T-PERM-071～073 为待实施任务，T-PERM-078 承接实施前细化。当前代码仍拒绝 autoGrant=true（20048），本文不宣称自动授权已可用。§16 未决规则收敛后才能进入相关实现，不因方向 adopted 而跳过。

<a id="scope"></a>
## 1. 目标与范围

业务系统声明“授予资源 A 的某操作时，同时授予资源 B 的某操作”。平台在写事务中计算角色的自动权限，运行时消费普通授权行，不遍历依赖图。

例：REPORT/sales-monthly:VIEW → REPORT/sales-template:VIEW。角色显式获得月报查看权后自动获得模板查看权；撤销月报后，模板若无其他来源则回收。A 与 C 都依赖 B 时，撤销 A 不回收仍由 C 支持的 B；独立 MANUAL B 也不由自动回收删除。

**权限含义**：自动权限可独立使用，depend_on=NULL。它不表达“仅访问 A 时临时使用 B”；现役 depend_on 子权限/主资源上下文是另一模型，不在本稿合并。源权限的运行时互斥结果也不构成目标权限的用途限制。

### 1.1 已采纳原则

- 资源与依赖接口各自可用，不强制一个大清单。原有单资源 sync 与按类型范围 full-sync 保持；无依赖的接入方不加载 manifest、不配置依赖发布、不提交空依赖集合。
- 有依赖需求的接入方独立发布声明。SDK 可选协调资源准备与依赖发布，不替换成熟同步程序，不将两个 HTTP 请求称为原子事务。
- 保留 declaration 源事实、compiled 依赖图与 AUTO_DEP 授权结果分层。
- 按角色完整计算 desired 再与 actual diff；不以持久路径计数判定存续。
- 取消 role_permission_auto_support 表、全路径补录、support 回填及专属一致性检查。按需解释和对账复用同一推导函数。
- AUTO_DEP 只读、不可转授；普通管理员主要在角色授权页操作，依赖页面向诊断。
- v1 不提供跨 owner 或平台内部目标的手工 override。服务凭证、类型所有权与既有门禁继续生效。

### 1.2 保持的边界

实例级、同 owner 服务、单操作触发、条件直传与多条件 OR、同步物化保持。类型级 scope_all/AUTHORITY_ROOT 不作种子；父实例继承得到的权限不展开为种子。授权页直接说明边界。

API 与操作关联派生仍归 T-PERM-054，不通过依赖图授 API 绕过启动门禁；菜单可见性走既有投影。跨系统 export、类型级依赖、注解扫描、异步重建、多操作 AND 触发不进入本次范围。动态 SQL 消费仍归 T-PERM-036。

<a id="architecture"></a>
## 2. 总体架构

```text
仅同步资源的系统 → resource-entity/sync 或 full-sync → 资源事实
有依赖的系统 → permission-manifest/full-sync
              → permission_dependency_declaration（业务键源事实）
              → DependencyCompiler
              → resource_dependency（已解析图，单写者）
              → 角色种子 + 共享推导函数
              → role_resource_permission（AUTO_DEP，事务内 diff）
              → PermQueryEngine（消费授权行）

来源解释 / 对账 → 一致的种子、图与实际授权
               → 同一推导函数 → 共享推导图 / desired 与 actual 比较
```

资源删除引起的依赖失效与自动权限回收由平台内部触发。调用方只调资源 sync 也必须得到完整收窄行为，不要求额外发布 manifest 才撤掉旧授权。

<a id="data-model"></a>
## 3. 数据职责

正式 DDL 以 [schema/access-service.sql](schema/access-service.sql) 为唯一权威，以下待实施模型随对应任务落地。

### 3.1 声明事实 permission_dependency_declaration

保存租户、声明来源、稳定声明键、manifest revision、源/目标业务键和操作码、编译状态/原因。源触发操作至多一个，NULL/空集合表示任意操作触发；目标操作集合非空。类型/操作码与 codeType 宽度对齐现契约，codeType 维持 64 字符边界。

状态为 RESOLVED/REJECTED；原因保留 CROSS_OWNER、RESOURCE_MISSING、TYPE_MISSING、OPERATION_INVALID、SELF_DEPENDENCY、CYCLE。失败行保留用于诊断，由重新提交重驱动，不增加 PENDING/resolver。

MANIFEST 范围按 tenant + 服务身份 + 维护来源隔离，管理面拒改删。ADMIN_UI 同 owner 手工维护是否保留及其授权边界由 §16 M3 收敛，不默认恢复 owner 豁免。业务键复用 BusinessKeyUtil，sync 协议键走 SyncKeyCodecUtil。

### 3.2 摄入状态 service_manifest_sync

保留 tenant + service、revision、规范化 payload_hash、SUCCESS/PARTIAL/FAILED、is_dirty 与最近同步时间。revision/hash 用于重复提交识别，不天然具有新旧发布排序能力。

只有 revision 相同、hash 相同、上次项级全 RESOLVED 且非 dirty，才能语义无变化短路。资源/类型/操作定义等非 manifest 变更影响声明或图时，同事务置 dirty；重新判定后刷新状态，不能以历史 SUCCESS 掩盖当前缺失。

### 3.3 编译图 resource_dependency

仅 DependencyCompiler 写。保留源/目标实体、触发位与目标操作位，补目标反向索引以定位受影响角色。诊断 declaration_id 不代表聚合边唯一来源。

编译键为源实体 + 目标实体 + COALESCE(source_operation_bits,0)；同键 RESOLVED 声明的目标操作取并集，任一声明变化按键重编译。真实来源按键查询，删除不按诊断字段定位。

auto_grant 随声明通道落地退役，同步全部后端 DTO、前端请求/表单/列表及契约；旧接口退役前维持当前 20048。维护来源只保留实际启用通道，ADMIN_UI 是否继续写随 M3，不预建无调用方分支。

### 3.4 自动授权结果

复用 role_resource_permission：grant_source=AUTO_DEP、单 canonical 操作位/行、can_grant=false、depend_on=NULL、条件按推导变体保留。既有唯一性允许 MANUAL/AUTO_DEP 并列，多条件变体落库仍须对照 DDL 验证。

不新增 support 表，不把推导图/路径列表塞入授权行 JSON。grant_dep_id 不作存续或清理依据；单字段不能表达多来源，实施时核查读者后退役或仅保留明确兼容用途，不新增写依赖。

<a id="integration"></a>
## 4. 独立接入与可选协调

### 4.1 资源同步独立可用

现役接口保持，字段与错误语义见契约总册 §19：

| 接口 | 粒度 | 行为 |
|---|---|---|
| POST /api/access/resource-entity/sync | 一个资源 | UPSERT / DISABLE / DELETE |
| POST /api/access/resource-entity/full-sync | 当前租户 + 来源服务 + 一个资源类型的完整同步范围 | 清单内 upsert；清单外该同步范围旧事实软删 |

FULL 不是“批量修改几个资源”，也不按类型扫描删除其他维护来源。类型、编码、可选名称/状态与必填 syncVersion 均是现役要求，没有因自动授权新增。

简单系统新增 D 只发 D 的单条 UPSERT；首次导入可 FULL 提交完整 REPORT 清单。不需要理解依赖或安装 registration starter。资源停用/删除与自动授权的收缩、恢复由平台在 M1/触发面中闭合，不推给调用方额外发依赖来撤权。

### 4.2 依赖声明独立发布

计划端点 POST /api/access/integration/permission-manifest/full-sync 只收 schemaVersion/revision/dependencies，不含 resources。身份来自凭证，不信任请求体 serviceCode。静态 JSON 与请求 DTO 同构；动态 PermissionManifestProvider.dependencies() 只产依赖，不强迫返回资源目录。

声明沿用 source 业务键/操作与 requires 目标集合，例如 REPORT/sales-monthly 的 VIEW 要求 REPORT/sales-template 的 VIEW。资源可由既有同步程序预先登记，不要求每次依赖发布都重传全部资源。

FULL 代表该服务完整依赖声明；完整空依赖集合表示清空该范围，读取失败/分页未完不能伪装为空。畸形清单预检零副作用；合法项级失败沿 SyncResultResp 风格返回，HTTP 200 不等于全部成功。

语义 hash 按规范化 source/target 业务键与操作集合计算：操作排序去重、NULL/空触发等价、声明按完整 canonical bytes 总排序后以 UTF-8 SHA-256 计算。description 不计入语义 hash，但其更新不得被短路吞掉；展示字段独立更新规则在正式契约中钉清。幂等条件见 §3.2。

### 4.3 SDK 协调是可选便利

复用已有服务凭证及 perm.credential-id/credential-secret/allow-insecure。registration starter 提供独立 manifest 发布，可选择“先准备资源、再发布依赖”的门面；单租户与 TenantRegistrationProvider 多租户隔离维持，不再建另一套密钥配置。

协调方法接收明确的资源输入或复用接入方同步程序，不从依赖边猜完整资源目录。单条增量与按类型 FULL 均独立可用；只改依赖时可仅发 manifest。

不强制资源+依赖大文件，也不要求两个接口携带同一 revision：资源项仍用 occurredAt+sequenceNo，manifest revision/hash 标识清单。资源前置失败/部分失败时给出诊断，不盲目继续；依赖失败重试既定发布输入，不拼接重新扫描后的另一个快照。SDK 本地仅校验形状/重复键/自依赖/清单内环，资源/操作存在性、所有权与完整图由服务端验证。

### 4.4 动态发布与未决协议

动态 Provider 须有显式刷新/发布调用方，启动同步只覆盖启动时点。无真实消费者不预建队列、定时器或通用工作流。

新副本发布 {A,B} 后旧副本晚到 {A}，每项资源版本不能保护 FULL 的缺失删除；manifest git revision/hash 同样不能判断发布新旧。M1 确定同 scope 单发布者/部署协调或可靠发布代次等最小协议，禁止以本机 now() 把旧目录包装成新版本。

现役资源 FULL 的 items 为 @NotEmpty，最后一条删除不能直接提交空数组。M1 选择“保持约束并显式 DELETE”或“允许完整空 scope 快照”后回写契约；未决定前保留现役行为，不宣传空 FULL 已可用。

<a id="compiler"></a>
## 5. 声明编译与写权限

格式预检 → 源类型 SYNC 且 owner=声明服务 → 目标同 owner → 资源/操作存在 → 自依赖 → 与已提交图联合判环。服务声明不得指向平台内部类型或无服务 owner 的 MANAGED 类型，API 也不作目标。拒绝声明不产生边，已有声明降级时移除对应贡献。

候选图包含本次 diff 与其他来源存量，已提交图优先，后到成环候选拒绝。编译、聚合与角色重算批量装载，不循环单条查库。

服务声明在管理台只读。跨 owner/平台内部目标 override 不进入 v1，不能凭 DEPENDENCY 写权限替代目标转授权能力。

现役 create/update/remove 的迁移由 M3 决定：保留同 owner 手工声明需定义实际服务归属与目标授权边界，否则关闭相应写入口。无论哪种，MANIFEST 拒改删、list/graph/check 类型级门禁和存在性校验都不能放松。

原 resource-dependency/batch-sync 随 manifest 通道退役；DEPENDENCY:SYNC、bootstrap 行、前端 perms/routes/mock、SDK、契约与 autoGrant 全字段链同步清理。存量来源/跨 owner 边须核实实际部署数据，不能以仓库无种子推断运行库无数据。

<a id="materializer"></a>
## 6. 角色级物化与共享推导

### 6.1 种子

该角色有效 MANUAL 行中取 scope_all=false、实例 ID 非空、depend_on=NULL 的行，条件不限。不按用户当前有效角色或运行时条件预删种子；物化处理角色授权事实，主体、条件与互斥仍由引擎运行时判定。

不展开父继承、不以 AUTHORITY_ROOT/类型级授权作种子。它们可能参与物理覆盖判断，但不是推导起点；本次未扩展该边界。

### 6.2 逻辑闭包

一致视图批量装载种子、图与操作定义，多角色重算共享图；通过既有 DomainService 供给数据，不绕过能力边界另查 Mapper。

```text
逻辑事实键 = (资源, canonical 操作, 条件变体)
种子入队，每个新增事实处理一次
源有效位满足边的触发操作 → 推出目标操作并沿用条件
同事实去重、不同条件保留
闭包稳定 → 规范化 → desired AUTO_DEP → 与 actual diff
```

effectiveBits=binaryBit|inheritMask 复用 OperationPermissionUtils。触发至多一个操作，NULL 触发表示任意有效操作；直接推导关系记录实际触发操作，不能混用不同类型的位空间。

无条件支配仅限同资源、同被证明操作：A:VIEW 无条件、A:EXPORT 带 C，只有 EXPORT 派生 B:READ 时，B 必须带 C。C1/C2 按 OR 并存，不能取其一或变 NULL。逻辑阶段可保留 NULL 与窄变体，用于完整直接来源解释，不枚举完整路径。

### 6.3 规范化输出门槛

覆盖压制/反链不是可直接增删的纯存储优化。现有互斥按 canonical 授权操作识别；UPDATE 覆盖 VIEW 时，只落 UPDATE 与同时落 VIEW/UPDATE 可能导致不同互斥结果。covers 只检查目标 canonical 位，不能保证传递/反对称：X 覆盖 Y、Y 覆盖 Z、X 不覆盖 Z 时，删 Y 可能丢 Z 的能力。

M2 用真实引擎反例确定规则：保留行须覆盖被删除行全部有效位且条件不更窄，并证明互斥/审计符合采用语义；无法证明则保留事实并明确互斥口径。不预设“原反链正确”，也不宣称“全不压缩等价”。逻辑闭包与物理压制分离，被压制中间节点仍继续推导。

### 6.4 diff、撤销与条件回收

desired 为存续依据；多来源不按路径计数。来源改变后完整重算，无来源的自动行同事务删除，独立 MANUAL 行不由物化删除。

INLINE 维持“一个 MANUAL 属主 + 多个系统派生引用”，用户不可显式共享。授予撤销/资源删除/类型级联/角色删除先收集候选 conditionId，完成自身删除与重算后，事务末尾按实际引用归零回收。不能提前回收，也不能因派生引用尚存而跳过后无人再处理。

### 6.5 来源图不持久化

解释时同一函数收集逻辑事实间的直接推导边与显式种子，构成共享 DAG。被压制中间节点无需物理授权 ID，根来源与条件变体不能丢失。写时不保存完整路径，不引入等价替身表。

取消 support 不取消授权 diff 审计、事务、锁或引用回收。完整历史推导回放不在解释承诺内，历史变更继续使用既有审计。

<a id="triggers"></a>
## 7. 完整触发面

| 事件 | 写事务内职责 |
|---|---|
| 显式授权授/撤/改条件 | 重算该角色并 diff |
| manifest / 已准入管理声明变化 | 重编译聚合边，定位角色重算 |
| 资源 DELETE / FULL 漂移删除 | 删除或降级声明/编译贡献，收缩全部受影响自动结果；不能仅删目标资源上的授权 |
| 类型删除级联 | 回收声明、边、自动授权与条件孤儿 |
| 角色删除 | 回收角色授权及条件孤儿，不重算已删角色 |
| 条件编辑/删除 | 维护引用与受影响结果，条件判定保持运行时语义 |
| 操作定义/类型所有权变更 | 按引用规则拒绝或重编译、重算 |

binaryBit 变更/操作删除存在有效授权引用时拒绝；inheritMask 变更重编译/重算，拒绝码随实施登记。非 manifest 入口影响声明/图时置 dirty。DISABLE/恢复及停用过滤由 M1 按真实查询行为明确，不以“删除已覆盖”替代生命周期验证。

资源新增不因 scope_all 自动派生；REJECTED 通过重发恢复，不设隐式 resolver。资源删除后恢复并重传，同 revision 不得被历史 SUCCESS 错误短路。

<a id="consistency"></a>
## 8. 并发与事务

MANUAL diff、编译影响、AUTO_DEP diff、INLINE 回收与变更标记在对应写事务内完成，中断整体回滚。缓存走既有 afterCommit，刷新窗口沿入口现契约，不宣称提交等于所有快照立即刷新。

树锁族内顺序 SYS_ORG → ABSTRACT_ROLE → SYS_MENU → RESOURCE_ENTITY 保持；新增编译与角色互斥统一全序，多角色 ID 升序，沿 afterCompletion 释放。混合类型批删反序随实现收敛，不加无依据的等待保险丝。

**M4 的必要反例**：授权读旧图 A→B，新种子未提交；编译删边时查不到该角色，不取其角色锁；授权随后提交旧图派生 B。只说“编译锁→已发现角色锁、授权仅角色锁”不能证明撤权一致性。

实施前确定一致视图与提交协议，如共享串行边界或可验证代次重试，形成全部入口矩阵；这些是待选实现，不是已定锁方案。无确定协议与交错验收不得进入物化。对账和解释不能补救正常撤销协议的缺失。

<a id="credential"></a>
## 9. 服务认证与多租户

复用 [service-authentication.md](service-authentication.md) 与契约总册 §24。manifest 强制绑定 tenant+service；资源同步已有凭证与遗留边界保持。精准 M2M 白名单不得扩大到管理/explain 或整个 /api/access/**。

资源同步与依赖发布各自按租户/服务处理，多租户协调分别取对应凭证与数据，失败不跨租户清理；不新建跨租户协议。

## 10. 正式契约与迁移

请求字段、错误码和响应结构在契约总册登记。071 登记 manifest/管理入口迁移；072 登记操作生命周期；073 登记解释。本文概念输入不是当前可调用 API。

manifest 不需要用户 API 快照/固定图授权行；管理 explain 需注册固定图及管理门禁。batch-sync/autoGrant 退役须前后端/SDK 锁步，严格 Jackson 下不能残留旧字段回传。运行数据迁移先核实，不能假设没有历史边。

<a id="explain"></a>
## 11. 按需来源解释

计划 POST /api/access/resource-dependency/explain 仅解释“为什么生成该角色的自动权限”。角色使用业务键，维持 DEPENDENCY:VIEW 类型级管理门禁，不开放服务凭证，不恢复已删除的通用用户排查端点族。

一致性视图读取种子、编译图、操作定义与 actual AUTO_DEP，同一推导/规范化函数输出 desired 的共享关系、显式来源、条件与覆盖说明，并对比实际存储。漂移必须标识，不能把“应生成”当成“已生效”。

完整路径按需展开，可分页/截断且标识；不得截断物化事实计算。窄条件、无条件来源及无物理授权行的中间节点均可解释。它不证明用户当前角色有效、条件成立、互斥通过或 Gateway 缓存刷新。

一致性读视图、展示限额/分页与正式响应由 M5 收敛；不新增来源表或历史推导事件库。

<a id="admin-ui"></a>
## 12. 管理台任务

角色授权页分组展示显式与自动权限，自动项只读，来源定位到显式授权。授/撤展示自动增减影响与仍被其他来源保留的结果；复用推导/规范化，不在前端另算图。预览入口、门禁与新鲜度由 M5 定义，保存以服务端事务重算为准。

当场提示“独立目标权限”及类型级/父继承不触发边界。依赖页显示服务、同步状态、失败原因、源/目标及影响角色，revision/编译来源放详情。MANIFEST 只读，不提供跨 owner override；同 owner 写入是否保留随 M3，不预留无门禁定义的可用按钮。

<a id="reconciliation"></a>
## 13. 对账

复用同一推导函数，检查 RESOLVED 声明与编译聚合、资源/声明失效残留、desired 与 actual 授权差异。不检查 support 存在性，不做路径回填或另写权限算法。

后台触发复用既有任务设施，周期/诊断随 073 确定，不新增队列框架。对账只发现异常，正常撤销由主事务保证；自动修复若需新写入口，须先定义其门禁/事务/完整触发行为。

## 14. 演进方向（非约束）

跨系统双边 export、类型级/继承种子、运行时上下文依赖、多操作组合、异步重建及注解扫描须有真实需求再立项，不为“统一入口”提前实现。

## 15. 实施载体

实施前细化沿 T-PERM-078；声明与独立接入沿 071；物化沿 072；来源解释、界面与对账沿 073。依赖/验收以任务卡与计划为准，不建立平行实现序列。

<a id="implementation-decisions"></a>
## 16. 实施前待决项

方向已采纳，下表由 T-PERM-078 收敛并回写本稿/契约/任务后，通过 071 前置门禁。不得重新把“是否采用简化方案”列为待决。

| 编号 | 待决定内容 | 必须走通的例子与后果 | 承接实现 |
|---|---|---|---|
| M1 | 独立 FULL 新旧发布、空资源集、停用/恢复、动态发布调用方 | 新 {A,B} 后旧 {A} 不误回退；末项删除、恢复、部分失败重试结果确定；SDK 不伪造版本 | 071；失效收缩与恢复挂接由 072 完成 |
| M2 | 条件变体、覆盖压制/压缩的最终规范化 | VIEW 无条件/EXPORT 带 C 不串义；不同条件 OR；非传递/相互覆盖；PERM_MUTEX 单查/批查/LIST 适用差分 | 072 |
| M3 | 同 owner 管理声明保留还是关闭；存量迁移 | 建边不能绕过目标授权权能；跨 owner/内部目标不支持；MANIFEST 拒改删 | 071、073 |
| M4 | 编译与新增授权并发提交、全部入口锁序 | 新种子未提交时删边不能留下旧图授权；删资源/角色/改操作交错同验 | 071、072 |
| M5 | explain 一致性读、预览接口与限额/分页 | expected/actual 区分，窄来源/被压制节点可查；预览失败不假报保存结果，显示截断不影响真实授权 | 073 |

scope_all/父继承边界保持，API 派生与动态 SQL 仍暂缓。小图仅证明复杂度与正向推导可行性，实现仍须真实 SQL、引擎、并发与端到端验证。
