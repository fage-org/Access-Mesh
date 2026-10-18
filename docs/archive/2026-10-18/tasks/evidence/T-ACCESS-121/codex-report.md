本轮确认 **7 项正式缺陷：P1 3 项、P2 4 项**。评审对象为完整后端现状，基线始终为 `8733a51c24adfe64b1ff4cb9a75594a1b324a3d6`；未修改文件或 Git 状态，未运行构建、测试或启动子代理。

common 最小统一方案可以实施，但**抽取锁组件不能替代下面的业务修复**。

以下位置简写：

- `J/`：`access-service/src/main/java/cn/ac/fage/accessmesh/access/`
- `M/`：`access-service/src/main/resources/mapper/`

## 正式发现

### [P1] 旧实体回写会恢复已轮换凭据、已撤销状态，甚至复活软删除记录

**位置：**

| 文件与行号 | 受影响入口 |
|---|---|
| `J/auth/service/impl/Oauth2ClientAppServiceImpl.java:141,205` | `updateClient` |
| `J/user/service/impl/UserManageAppServiceImpl.java:289,330,338` | 外部主体 `updateUser` |
| `J/user/service/impl/AbstractUserSyncAppServiceImpl.java:331,343` | 主体同步更新 |
| `J/rule/service/impl/ConditionAppServiceImpl.java:181,223` | `updateCondition` |
| `J/rule/service/impl/ConflictRuleAppServiceImpl.java:398,424,429` | `updateConflictRule` |
| `J/platform/service/impl/JobAppServiceImpl.java:259,283,344,352` | `updateJob`、`toggleJobStatus` |
| `J/platform/service/impl/DictAppServiceImpl.java:310,333` | `updateDictData` |
| `J/platform/service/impl/SystemConfigAppServiceImpl.java:84,105` | 已存在配置更新 |
| `J/domain/service/impl/BizDomainAppServiceImpl.java:230,235` | 业务域更新 |
| `J/domain/service/impl/DomainConfigAppServiceImpl.java:107,112,218` | 配置保存与删除竞争 |
| `J/type/service/impl/TypeDefinitionAppServiceImpl.java:482,568,570` | 未受共同锁保护的非 `resource_type` 更新 |
| `J/platform/service/impl/NoticeAppServiceImpl.java:145` | 未提供 `noticeType` 时仍回写旧值 |

**问题：** 多个更新入口把普通查询得到的完整对象传给 `BaseMapper.update`，或把旧对象中未请求修改的字段复制进 patch。数据库 UPDATE 的行锁只串行化最终写语句，不能保护此前读出的值。

**触发条件：** 以 OAuth2 客户端为例：

1. T1 仅修改客户端名称，读到旧密钥哈希 H1。
2. T2 轮换密钥为 H2，提交成功。
3. T1 执行 `update(existing)`，把 H1 一并写回，两个请求均返回成功。

删除竞争同样成立：T1 读到 `delete_flag=0`，T2 软删除并提交，T1 随后将旧对象的 `delete_flag=0` 写回。

**影响：** 旧客户端密钥重新有效；主体、条件或任务的停用被撤销；安全规则被旧值覆盖；已删除对象重新出现。条件仅改名也可能撤回另一请求刚收紧的规则。

**证据：** 已核对实际 MyBatis-Flex **1.11.8** JAR 字节码：`BaseMapper.update` 默认忽略 null，普通实体由 `TableInfo.obtainUpdateColumns` 收集非空列，并非仅更新发生 setter 调用的列。当前生产配置只声明租户列，相关实体的 `deleteFlag` 没有逻辑删除映射，因此不能假定 ORM 自动阻止这类复活。

`ConflictRuleAppServiceImpl` 和公告入口也说明：**使用 `UpdateEntity` 后继续复制旧字段，仍然存在相同问题。**

**建议：** 统一采用“请求实际触达列”的更新方式，并在 SQL 中显式限定 `tenant_id/id/delete_flag=0`、检查影响行数。条件规则、互斥规则及事实与投影联动涉及跨字段不变量，应在对应 DomainService 中通过同事务行锁或 CAS 完成读取、验证和写入。上述调用点应一起处理；单条记录更新优先使用 PG 机制。

---

### [P1] 类型入口的锁后复读仍命中 MyBatis 一级缓存，能够破坏所有者与授权根一致性

**位置：** `J/type/service/impl/TypeDefinitionAppServiceImpl.java:482,492,553,570,582`；`J/type/service/impl/OperationAppServiceImpl.java:133,142,143`；`J/engine/core/TypeResolutionServiceImpl.java:92`；`M/type/TypeDefinitionMapper.xml:54`。

**问题：** `updateType` 在取 Redis 锁前查询类型，锁后再次执行同一 Mapper 查询，但查询没有清理一级缓存。`createOperation` 在 TYPE_VALUE 缓存 miss 时，也会在锁前、锁后调用同一个类型查询。

**触发条件：**

1. 类型当前所有者是 A，授权根也属于 A。
2. T1 仅修改类型名称，锁前读取并缓存 `extra.grantOriginRole=A`。
3. T2 取得 RESOURCE_ENTITY 锁，将所有者迁移到 B，同时迁移授权根，提交。
4. T1 取得锁；所谓“锁内重读”返回缓存中的 A。
5. T1 写回旧 extra；请求没有携带新指针，因此不执行授权根迁移。

最终数据库成为：**类型指针是 A，授权根仍属于 B。**

另一个可实现交错是：冷缓存的 `createOperation` 在锁前解析类型，类型随后被删除；锁后查询仍返回旧类型，继续创建属于已删类型的操作定义。

**影响：** 所有者声明与授权事实长期分叉，后续新增操作、首授和所有者迁移出现错误。Redis 锁虽然串行，业务读取仍不新鲜。

**证据：** 本机 MyBatis **3.5.19** 源码中，`Configuration` 默认 `LocalCacheScope.SESSION`，`BaseExecutor.query` 优先返回本地缓存。仓库没有改为 STATEMENT；实际 FlexConfiguration 也没有覆盖该默认值。仓库已在 `ServiceConfigMapper.selectConfigGeneration` 使用 `flushCache="true"` 处理相同机制。

**建议：** 复用现有锁，增加明确的数据库新鲜读取入口，使用 `flushCache="true"`；仅设置 `useCache=false` 不足以关闭一级缓存。`createOperation` 应把类型首次解析移入锁内。类型批删中同模式的复读也应由这个入口统一覆盖。不要让 common 锁组件依赖 MyBatis 来隐式清缓存。

此例没有角色删除，不属于类型所有者删除已接受的 best-effort 窗口。

---

### [P1] 岗位成员操作使用锁前 parent，移除成功后仍可能保留岗位权限

**位置：** `J/org/service/impl/UserOrgWriteAppServiceImpl.java:77,108,143,180,189,218`；`J/org/service/impl/OrgWriteAppServiceImpl.java:169`；`J/role/service/domain/impl/UserRoleProjectionWriter.java:101,293,333`。

**问题：** assign/remove 虽然取得 SYS_ORG 锁，却继续使用锁前读取的岗位 `parentId` 构造 `relation_id`。

**触发条件：**

1. 岗位 P 属于组织 A，用户 U 持有关系 `(U,P,A)`。
2. T1 移除 U 的岗位成员，先读到 `P.parent=A`，尚未取得 SYS_ORG 锁。
3. T2 将 P 移到同树组织 B，迁移关系为 `(U,P,B)`，提交。
4. T1 取得锁，删除 `sys_user_org(U,P)`，但仍按旧 A 查找、撤回投影。
5. `(U,P,A)` 已不存在，解绑返回 null，T1 正常提交。

**影响：** 管理面显示成员已移除，`user_role(U,P,B)` 却仍有效，岗位权限继续存在。assign 的对应交错会新增错误的关联组织上下文。

**证据：** `migratePositionRelation` 确实会更新 `relation_id`；解绑精确匹配旧 relation。`SubjectDomainServiceImpl.java:396` 起解析有效角色时会消费残留关系的 `targetId`。当前“组织存在性可在锁前读取”的安排，不能覆盖后来用于写投影的 parent。

**建议：** assign/remove 在 SYS_ORG 锁内重新取得岗位的最新结构信息，再构造绑定键；批量 assign 保持批量读取。若保留锁前门禁预查，锁内必须使用能绕过一级缓存的新鲜查询。无需增加另一把 Redis 锁。

---

### [P2] 主体删除与成员绑定没有共同边界，清理后仍可补入孤儿关联

**位置：** `J/user/service/impl/UserWriteAppServiceImpl.java:289,304,328,334`；`J/org/service/impl/UserOrgWriteAppServiceImpl.java:108,136,150`；`J/user/service/domain/impl/BatchAdminUserProjectionWriter.java:68`。同类竞争面还包括 `UserManageAppServiceImpl.deleteUsers/assignRole/assignRolesBatch`、外部主体删除与 `UserRoleSyncAppServiceImpl` 的 BIND。

**问题：** 原生用户删除使用管理员连续性 PG 锁，组织成员绑定使用 SYS_ORG 锁；双方没有共同的主体生命周期守卫。外部主体删除与角色绑定也存在类似覆盖缺口。

**触发条件：**

1. T1 为 U 添加新组织成员，插入尚未提交的成员行，解析出 U 的有效主体 ID。
2. T2 删除 U，看不到 T1 未提交的新关系，完成已有成员、用户及角色关系清理并提交。
3. T1 使用先前解析的主体 ID 插入角色投影并提交。

**影响：** 已删除用户留下有效 `sys_user_org`、`user_role` 孤儿事实。归属守卫按成员记录判断时，还可能受到这些记录干扰。

**证据：** 删除入口没有取得 SYS_ORG 锁；绑定入口没有锁定、复核主体生命周期。项目明确采用无物理外键设计，现有唯一索引只能防重复，不能保证引用对象仍有效。

**建议：** 在共享主体领域入口建立“锁定有效主体后新增关系／先锁主体再清理”的共同协议，覆盖原生、管理和 sync 写者。原生删除若加入 SYS_ORG 锁，应在管理员连续性锁之前获取，避免引入新的反序。外部主体可优先采用按 ID 排序的 PG 行锁，不必扩成全租户 Redis 锁。

---

### [P2] 主体 full-sync 在版本互斥前预载，新版本可能因旧预载整批失败

**位置：** `J/user/service/impl/AbstractUserSyncAppServiceImpl.java:196,220,233,315,326`；`M/sync/SyncMetadataMapper.xml:116`；`docs/design/schema/access-service.sql:719`。

**问题：** full-sync 先批量读取主体，再逐项调用原子版本比较。版本行的串行化不会刷新之前保存的 `existingByExternalId`。

**触发条件：**

1. U 原本不存在。T1 的 FULL v2 预载结果为空。
2. T2 的单条 UPSERT v1 创建 U 并提交。
3. T1 通过版本比较，v2 正确胜出。
4. T1 仍按预载的“不存在”执行 INSERT，撞上 `uk_abstract_user`。

**影响：** 合法的新版本请求返回技术失败，整个 full-sync 事务回滚，其他主体也需重新同步。这里回滚本身有效，问题是版本仲裁与实际应用使用不同时间的事实。

**证据：** full-sync 没有共同串行锁，目标应用明确消费预载对象。角色和成员 sync 已把预载纳入 ABSTRACT_ROLE 锁窗口；该保护不能推及主体同步。

**建议：** 在预载之前建立主体同步所需的共同边界，覆盖 single、FULL 和漂移清理；或调整持久化流程，使版本胜出后的目标应用读取当前事实。保留已有版本 CAS，并与上一项的主体生命周期守卫统一设计。

---

### [P2] 字典类型删除与数据创建不互斥，可留下并挂接旧数据

**位置：** `J/platform/service/impl/DictAppServiceImpl.java:139,150,265,280,315,333`；`M/platform/SysDictTypeMapper.xml:6,39`；`docs/design/schema/access-service.sql:353`。

**问题：** 删除类型的“无数据”检查，与创建、改绑字典数据的父类型检查都是普通查询，没有共同锁。

**触发条件：**

1. T1 删除字典类型 K，检查到没有数据。
2. T2 读取到 K 有效，插入字典数据并提交。
3. T1 软删除 K，提交成功。

**影响：** 有效字典数据指向已删类型，正常列表无法访问；重新创建同编码 K 后，由于数据按 `dict_type` 编码关联，旧数据会附着到新类型。

**证据：** `sys_dict_data` 保存类型编码而非类型主键，没有引用约束；类型唯一索引允许软删除后重建。当前删除守卫未在提交前重新检查。

**建议：** 复用业务域配置的成熟模式：删除检查、创建数据、改绑数据都先锁定有效字典类型行，并持锁至提交。批量按固定顺序锁定。这里适合 PG 行锁。

---

### [P2] Gateway 清理失效标记时存在代际回退，已作废回源可以重新通过

**位置：** `gateway/.../cache/InterfaceSnapshotCacheInvalidator.java:202,212`；`gateway/.../cache/InvalidationMarker.java:91,124`；`gateway/.../filter/PermissionFilter.java:380,424`。

**问题：** `liveKeys()` 在进入 marker 的同步区之前生成；随后依据这个旧集合删除代际。删除后，`generationOf` 又把缺失值解释为 0，产生代际回退。

**触发条件：** 此项是线程交错，不涉及数据库写事务：

1. 清理线程 C 取得空的 liveKeys，暂挂。
2. 回源 R 注册在途 key，取得 generation=0 的 token。
3. 已送达的用户失效事件将该 key 标记为 generation=1。
4. C 用旧空集合清掉 generation=1。
5. R 完成，缺失代际被解释为 0，旧 token 再次通过并回填快照。

**影响：** 一次已经成功送达、已经标记的撤权失效被绕过，旧权限快照重新进入缓存。影响仍受快照 TTL 限制，不是无限期陈旧。

**证据：** `retainLiveKeys` 会删除 `keyGeneration`，但不提升 `globalEpoch`。当前 Gateway 规范明确要求已失效的在途结果丢弃或失败关闭；本项不同于已接受的广播丢失或跟踪索引过期。

**建议：** 在与 beginLoad/mark/commit 相同的同步边界中获取实时存活集合并清理，或在清理代际时保守作废旧 token，保证代际不会回退而复活。继续使用本地协调机制，无需 Redis 锁。

## 锁覆盖矩阵与复查结果

第二轮从 mapper DML、注解 SQL、BaseMapper 写者和关键表约束反查，得到以下覆盖：

| 主要写入口 → 共同不变量 | 当前保障 | 竞争入口与结论 | 提交时机 |
|---|---|---|---|
| Org create/update/delete → 组织树及岗位投影 | SYS_ORG → ABSTRACT_ROLE → RESOURCE_ENTITY | 结构写串行；成员锁前 parent 存在第 3 项 | afterCompletion 解锁 |
| Menu create/update/delete → parent、深度、删父挂子 | SYS_MENU | 三入口共同参与 | afterCompletion |
| Role create/update/move/delete、角色 sync/FULL → 角色树 | ABSTRACT_ROLE；部分入口叠加连续性锁、RESOURCE_ENTITY | 已检查锁序与授权清理路径 | PG 提交／afterCompletion |
| 树配置 create/update/delete/setDefault、成员 assign/remove/setPrimary → 默认归属 | SYS_ORG | 用户删除未参加，见第 4 项 | afterCompletion |
| Resource CRUD、sync/FULL、manifest → 资源、发布、依赖与物化 | RESOURCE_ENTITY＋版本/发布状态 | 主要写链共同参与，保留 CAS | 同事务；锁至 afterCompletion |
| apply-grant-plan → MANUAL/AUTO_DEP/INLINE 一致性 | RESOURCE_ENTITY | 已追查编译、资源和角色删除调用链 | 同事务＋提交后失效 |
| 类型与操作写入 → 类型身份、操作位、授权根 | RESOURCE_ENTITY；role_type 删除使用 ABSTRACT_ROLE | 第 2 项使部分锁后读失效 | 同事务／afterCompletion |
| 原生用户 update/status/delete → 保底成员与投影 | PG 连续性咨询锁 | 字段 patch 已存在；成员绑定覆盖缺口见第 4 项 | PG 提交 |
| 三个密码重置入口 → 重叠请求拒绝、目标凭据 | Redis 非等待锁＋原有 PG 行锁 | 租户内重置与平台首管理员恢复共用键；平台账号隔离键 | afterCompletion |
| 平台账号管理 → 最后启用账号、凭据代次 | PG 管理咨询锁＋credential_version | 锁后清缓存复核；登录旧版本不能因此复活 | PG 提交 |
| 租户状态／修复 → 状态、session_epoch、发布令牌 | PG 租户行锁＋Redis Lua CAS | 已检查预阻断、提交、发布及旧发布拒绝 | 变更提交后发布 |
| 凭证签发与服务删除 → 凭证退役完整性 | 共同 service_config 行锁 | 删除另持 RESOURCE_ENTITY；显式字段更新 | 同事务提交 |
| DomainConfig save／BizDomain delete → 配置不悬挂 | PG 域行锁＋唯一约束 | 正常引用互斥保留；配置自身删除竞争归第 1 项 | PG 提交 |
| 公告 publish/revoke/read → 状态转换、幂等已读 | 条件 UPDATE、ON CONFLICT | 状态转换已原子化；未触达列覆盖归第 1 项 | PG 提交 |
| 文件上传／删除 → 记录与物理文件生命周期 | UUID、事务完成回调 | 上传回滚清理、删除提交后清理；无需统一锁 | 回滚后／提交后 |
| Job 配置、调度、执行 → 配置和执行尝试 | JVM 调度锁＋PG execution_key/owner/attempt CAS | 配置旧值覆盖见第 1 项；租约机制应保留 | 调度临界区／独立 SQL |
| common／Gateway／SDK／example → 实际共享状态 | 并发集合、局部状态、原子计数、代际机制 | Gateway 第 7 项；未发现需迁入 Redis 锁的其他明确调用点 | 各自机制 |

已覆盖审计、认证、用户、组织、菜单、角色、授权、资源、类型、规则、业务域、平台能力、sync、projection、bootstrap、任务设施，以及 common/Gateway/SDK/example 的相关共享状态。**未穷举所有交错，也未连接实际 Nacos、PG、Redis 验证运行配置、网络分区和暂停恢复。**

## 实证通过项与已知边界

- `RedisTransactionLockSupport` 已使用异步命令和原线程 `join` 确定获取结果；正常释放、获取中断后的清理、注册失败清理均保留原 owner 线程责任。技术异常没有被转换成 10015。
- 当前调用图未发现同一锁跨线程释放，或同线程跨 `REQUIRES_NEW` 事务重入的实际调用。现有同事务树锁重入按获取次数注册释放。
- 未重报 watchdog 网络分区／长暂停、READ_COMMITTED 前提、树锁无等待超时、重置提交后的密码交付窗口。
- MANAGED 条件删除与授权预检、角色互斥并发授予、user_type 引用删除、类型所有者删除的既有接受范围，分别按当前契约核对；未据此豁免本文的更新覆盖、类型缓存或主体删除问题。
- 任务执行的 owner＋attempt fencing、公告条件更新、验证码／OAuth2 一次性消费，不应换成普通 Redis 互斥。
- 测试仅引用 [T-ACCESS-120 验收证据](D:/codespace/Access-Mesh/docs/archive/2026-10-18/tasks/evidence/T-ACCESS-120/verification.md)：记录完整回归 **3004 项全部通过**。本轮没有重跑，也没有据此排除未覆盖竞态。

文档轨核对后，第 2、3、7 项分别与锁后新鲜读取、岗位关联投影、失效代际保障冲突；其余发现未找到与实际后果匹配的有效接受边界。

## common 最小统一方案

建议把现有 helper 的机械责任移到 common，公开两个方法即可：

```java
public final class RedisTransactionLocks {
    public static void lock(RedissonClient client, String key);
    public static boolean tryLock(RedissonClient client, String key);
}
```

具体边界：

- `lock` 等待竞争锁；`tryLock` 不排队竞争，但仍等待 Redis 命令结果。
- 调用前必须已有真实事务和同步回调；组件负责获取结果、中断清理、注册失败清理、afterCompletion 释放。
- 保持 watchdog、原线程 owner、现有技术异常和释放告警语义。
- 当前业务正确性继续要求 READ_COMMITTED；拿锁后的业务数据必须新鲜读取。
- 支持同线程、同事务的重入配平。跨线程、独立嵌套事务不作为支持场景；当前没有调用需求，不增加重入登记框架。
- 保留 common 中 Redis/Redisson 的 optional 依赖。采用普通工具类，无需自动装配、扫描或新增配置，SDK 和无 Redis 宿主不会因此创建锁组件或开启事务。
- 业务键继续由两个业务门面构造，10015 继续由 `PasswordResetLockSupport` 映射。common 不依赖 perm-common，不包含业务枚举。
- 树锁顺序继续是 **SYS_ORG → ABSTRACT_ROLE → SYS_MENU → RESOURCE_ENTITY**，不能按键字符串或树编号排序。

### 迁移清单

**适合直接迁移：**

| 门面 | 继承其公共实现的调用点 |
|---|---|
| `TreeWriteLockSupport` | RoleManage、AbstractRoleSync、UserRoleSync；OrgWrite、OrgTreeConfig、UserOrg、UserWrite 创建挂载；MenuWrite；ResourceManage、ResourceEntitySync、ServiceSync、ServiceConfig、PermissionManifest；TypeDefinition、Operation；ApiMappingWriteDomainService |
| `PasswordResetLockSupport` | 租户用户重置、平台首管理员恢复、平台账号重置 |

只需让两个门面调用 common，业务调用方继续使用原门面。已有 helper 本来就被两者共用；抽取的价值是跨模块复用与统一维护，**不会自动修复业务覆盖缺口**。

**应保留原机制：**

- 用户／平台管理员连续性咨询锁。
- 登录、授权码签发、租户生命周期、服务凭证退役、业务域引用的 PG 行锁。
- sync 版本 CAS、发布代次、config_generation 和唯一约束。
- 任务租约、attempt fencing、扫描器咨询锁及 JVM 调度锁。
- 公告状态 CAS、已读 upsert、验证码消费、失败计数 Lua。
- Gateway 本地代际协调、SDK 请求局部状态、文件事务回调。

字典和主体生命周期修复优先补领域内 PG 协议；无需为了公共抽象给全仓加 Redis 锁。

## 建议验收与实施顺序

优先处理：

1. OAuth2 密钥／删除复活及其他未触达列回写。
2. 类型锁后新鲜读取与授权根一致性。
3. 岗位移动和成员撤回交错。
4. 主体删除／绑定、主体 FULL／single、字典删除／创建、Gateway 标记清理交错。
5. 完成 common 机械迁移并保持原业务契约。

验收使用确定性闸门，分别固定本文交错；SQL、事务和约束用 ItInfra 的真实 PG/Redis，Gateway 使用 Future/latch。公共锁迁移复用既有中断、提交／回滚、竞争及键隔离用例，并检查同事务重入配平和无 Redis 宿主依赖隔离。测试缺口随上述缺陷修复补齐，不另建一套重复锁测试框架。

**过度设计可裁剪项：无。**

**存疑待用户决策项：无阻断性事项。** common 最小抽取可实施；整体实施范围需要包含上述业务修复，不能以组件迁移完成作为专项关闭依据。

**存量观察：无范围外事项。** 本文发现均属于此次全后端现状排查的正式范围。

## 总结

| P0 | P1 | P2 | P3 |
|---:|---:|---:|---:|
| 0 | 3 | 4 | 0 |

**结论：common 最小方案可实施；专项修复范围需修订，7 项缺陷均应纳入后续修复与验收。**