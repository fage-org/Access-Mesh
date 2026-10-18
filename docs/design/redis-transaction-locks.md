---
doc_type: design
title: 公共 Redis 事务锁与后端并发接入方案
status: adopted
domain: common
last_reviewed: 2026-10-18
---

# 公共 Redis 事务锁与后端并发接入方案

> 当前实现已完成，[实施计划](../archive/2026-10-18/backend-concurrency-hardening.md)与[验收记录](../archive/2026-10-18/tasks/evidence/backend-concurrency/verification.md)保存范围和证据。既有树锁、重置锁、数据库租约与接受边界保持。

## 目标与现状

`common.lock.RedisTransactionLocks` 承担原 access-service helper 的获取结果、事务结束释放与异常/中断清理。业务侧继续决定锁对象、竞争处理和需要保护的读取。提取公共类本身不会修复未参与互斥的写入口、锁前旧快照、MyBatis 一级缓存及整行旧值回写。

现有使用者为 TreeWriteLockSupport（等待）和 PasswordResetLockSupport（非等待）；现行键空间和错误码保留。公共模块 Redis/Redisson 依赖保持 optional，不新增自动配置注册，不让 SDK、Gateway 或无 Redis 宿主被迫装配客户端。

## 最小公共 API

位置：common/src/main/java/cn/ac/fage/accessmesh/common/lock/RedisTransactionLocks.java。

```java
public static void lock(RedissonClient client, String key);
public static boolean tryLock(RedissonClient client, String key);
```

两个入口共用私有获取/释放实现，避免 boolean 参数出现在调用处。它们只面向同步 Spring 事务：获取前 actual transaction 与 transaction synchronization 均须有效；业务键在调用前确定，不能拼接未经规整的原始输入。TreeWriteLockSupport/PasswordResetLockSupport 保留为业务门面，common 不引用 access-service 或 perm-common，不定义业务错误码。

- lock 等待竞争锁；tryLock 仅在竞争失败时返回 false，Redis 故障不能伪装成业务冲突或本地无锁继续。
- 不传固定 leaseTime，复用 watchdog。中断前置拒绝；获取过程中中断则等待异步结果确定，若成功先尝试释放，再以保留中断标记的技术异常退出。
- 成功获取后注册 afterCompletion，提交、回滚或未知结果均尝试解锁；注册失败立即清理。解锁等待异步结果，失败记日志，不改写已确定的事务结果。
- 同一同步事务重入沿用现有配平机制。不得把接口用于跨线程持有/释放、Reactive 事务或持锁后在独立 REQUIRES_NEW 事务中重入同 key；这些不是本稿拟支持的能力。
- 不提供 try-with-resources 方法体释放、注解/AOP/SpEL、自动重试、补偿队列、配置化锁目录或多锁自动排序；当前没有调用方需要这些能力。

## 业务接入规范

必须先识别共同不变量，再枚举所有写入口与竞争入口。锁从决定写入结果的第一次读取之前开始；必须预读以确定锁对象时，只能把不可变身份用作定位，锁内重新获取业务事实，且该查询必须实际访问数据库。READ_COMMITTED 不会清除 MyBatis 事务内一级缓存；是否使用 flushCache 查询或限定配置须以可失败的真实事务测试验证，不能仅把同一 mapper 方法再调用一次。

更新只写本次请求触达的字段；需要检查多字段组合或维护关联投影时，还须在共同串行边界内读取最新组合。不要用锁掩盖全实体旧值回写。

多锁遵循现有业务顺序：SYS_ORG → ABSTRACT_ROLE → SYS_MENU → RESOURCE_ENTITY。该顺序不是枚举数字或 key 字符串排序。新增 Redis 与 PG 锁的组合必须画出竞争路径，不能在先持 PG 锁后等 Redis 的入口与反向入口之间形成环。

批量操作不得逐项无限扩展锁数量；先论证聚合边界，再决定是否需要单聚合锁。缓存失效继续 afterCommit；公共锁不替代缓存一致性协议。

## 保留的保障与使用选择

| 场景 | 建议机制 | 原因 |
|---|---|---|
| 现有树写/授权物化/发布 | 原树锁门面委托 common | 复用已定案共同锁、锁序及键空间 |
| 三个密码重置入口 | 原非等待门面委托 common | 保持目标账号互斥和10015，不改变提交后交付窗口 |
| 单行状态转换/唯一业务键 | 条件 UPDATE、唯一约束或 UPSERT | 数据库能原子表达时保留持久保障 |
| 删除父记录与新增子记录 | 共同行锁或经确认的聚合 Redis 锁 | 两端都须参与，单改某个入口无效 |
| 普通编辑覆盖未触达字段 | 最小列写；关联组合必要时加共同锁 | 加锁不是请求字段语义的替代品 |
| 任务执行/接管 | PG 租约与 attempt fencing | Redis mutex 不能替代旧执行者隔离和执行幂等 |
| 服务凭证签发/退役 | 现有服务行锁 | 保护与数据库事务绑定，已覆盖双方 |
| Gateway 与 SDK 进程内状态 | 原本地同步/代际机制 | 不扩大 Redis 依赖，不阻塞 Reactive 调用线程 |

## 验证与迁移顺序

现有公共实现与树写/重置门面迁移已完成，真实 Redis 中断迟到成功、事务提交/回滚、重入和身份键隔离证据保持。后续扩展同样应逐项验证业务覆盖，新增测试必须在旧实现下稳定失败；使用闩锁/提交闸门安排交错，不依靠裸 sleep。数据库行为使用既有 ItInfra，单元模拟不能代替持久化结果。

common API 变化后先 install 刷新上游 SNAPSHOT；各批先定向验证，实施收口执行根 reactor 的 mvn test -T 1C（包含 E2E/heavy），输出整文件保存且运行时不得改源码。实现完成再按代码轨、文档轨独立本地评审；不会自动追加外部评审。

## 边界

Redis 互斥不是数据库提交或外部副作用的强一致证明。原有树锁续期丢失边界、重置提交后交付窗口保持在原适用范围内；新增业务不能自动继承未论证的例外。更强的故障隔离应使用对应数据库约束/条件更新/fencing，不通过任意延长 TTL 声称解决。


## 排查来源与处置

本方案由 [T-ACCESS-121](../archive/2026-10-18/tasks/T-ACCESS-121.md) 产出；[原始报告与逐项核实](../archive/2026-10-18/tasks/evidence/T-ACCESS-121/verification.md)基于 8733a51c2 的后端现状。排查项已由关联任务闭合，修复与验收见上述记录。

| 排查级别 | 问题入口 | 当前处理 |
|---|---|---|
| P1 | [Q-083 旧实体回写](../pending-problems.md#q-083) | 请求触达列 + 有效行条件；跨字段与投影放入共同事务守卫 |
| P1 | [Q-084 类型锁后旧缓存](../pending-problems.md#q-084) | 锁内首次解析或明确 fresh Mapper；不把清理 MyBatis 缓存塞进 common 锁 |
| P1 | [Q-085 岗位旧 parent](../pending-problems.md#q-085) | SYS_ORG 锁内批量读取结构，再构造 relation |
| P2 | [Q-086 主体删除与绑定](../pending-problems.md#q-086) | 领域共享主体生命周期协议，覆盖全部新增关系与清理路径 |
| P2 | [Q-087 主体 FULL 旧预载](../pending-problems.md#q-087) | 与主体生命周期同批设计；覆盖 single/FULL/漂移清理，保留版本 CAS |
| P2 | [Q-088 字典父子生命周期](../pending-problems.md#q-088) | 删除检查、创建、改绑共用父类型行锁，批量固定顺序 |
| P2 | [Q-089 Gateway 代际清理](../pending-problems.md#q-089) | 保证清理与 token 校验间不发生代际回退，使用本地同步 |

这些入口已按下述共同协议接入，验收完成与问题关闭由关联任务及验证记录判定；不凭 helper 提取完成关闭缺陷，也不恢复其他暂缓事项。


<a id="write-guards"></a>
## 写入事实与生命周期的共同边界

### 请求字段与有效行

OAuth2 客户端、任务、条件、互斥规则、已有系统配置、域配置、字典项、公告以及类型编辑，在更新前通过 Mapper 的有效行锁查询取得当前事实，持锁至事务结束。查询显式带租户、身份与 delete_flag=0，flushCache=true 保证实际执行；原查询入口保留给只读调用。行锁保护读取后至更新期间的有效性，不能被并发软删除穿过；读到不存在沿用已有业务错误。业务域编辑复用既有域行锁。

通常仅写请求触达列和必要审计字段；显式清空通过 UpdateEntity 记录 NULL。互斥规则的归一字段、条件组合校验及类型的服务端授权根指针使用锁内最新状态，不能仅改 patch 就省掉组合校验的互斥。主体及投影的部分更新在下述共同主体锁内完成，避免事实与投影各自拼接不同时间的值。

### 类型与岗位的读取时机

类型的 selectValidById/selectValidByIds/selectByTypeKeyAndCode 明确清理 MyBatis 会话缓存；类型编辑在所需树锁之后再锁定有效类型行。操作 create/update 在 RESOURCE_ENTITY 锁内直接读取类型身份，写入口不使用 TYPE_VALUE 缓存确定目标；只读查询仍可使用原解析缓存。所有者指针与授权根变更继续由原事务共同维护。

岗位成员 assign/remove 在 SYS_ORG 锁内读取最新结构，再构造 relation。批量分配仍批量读取，不引入循环查询。锁前对象不能作为锁内投影写入的 parent 来源。

<a id="subject-lifecycle-lock"></a>
### 主体生命周期与同步

`SubjectDomainService.lockUserWrites` 是共同领域入口，`LocalProjectionDomainService` 仅提供既有跨能力门面的委托。实际 SQL 单源为 `AbstractUserMapper.lockUserWrites`，沿用原管理员连续性锁的 PostgreSQL 事务咨询键 `(106, hashtext(tenantId))`；原 SysUserMapper 注解 SQL 入口删除。查询经 Mapper 执行并清理会话缓存，不能改为不参与 Flex 事务的裸 JdbcTemplate。事务外调用拒绝。

同一租户的原生主体创建/更新/启停/删除、管理主体创建/更新/删除、用户组织 assign/remove、管理角色成员分配/撤回、主体 single/FULL 同步和 user-role single/FULL 同步共用此边界；已有管理员连续性 begin 委托该入口，资格判定与错误码不变。锁必须先于目标读取、FULL 预载及版本 CAS；FULL 与 single 不会在版本仲裁后继续应用旧预载。绑定与删除持有至事务结束，绑定不能在删除清理后依据旧主体身份补入新关联。初始固定图仍由原开通/初始化隔离机制保护。

复用租户级锁覆盖尚不存在的主体及跨主体 FULL 预载，不只锁当前已存在的 ID。代价是同租户主体写入串行，FULL 期间其他主体写入会等待；本次不增加锁粒度配置。未来细化必须保留不存在目标、全部关系写者和 FULL 漂移清理的共同边界，不能删掉版本 CAS。

联合锁序为 **SYS_ORG → ABSTRACT_ROLE → SYS_MENU → 主体事务锁 → RESOURCE_ENTITY**，只获取入口需要的部分。已有角色删除/sync 的角色树→主体事务锁→资源树顺序保持。原生创建带组织时先取 SYS_ORG，再取得主体锁，不得先写主体再等待组织锁；已持有 RESOURCE_ENTITY 的新入口不得首次反向请求主体锁。

### 字典父子生命周期

删除类型先按 ID 升序锁定有效父类型，再检查是否存在字典项；创建/改绑字典项也先锁定有效目标类型，检查与写入均在同一事务。字典项自身更新使用其行锁。两种方向的竞争分别收敛为类型不存在或类型仍有数据，沿用现有错误码；重建同编码不会附着旧孤儿数据。创建显式填写 schema 既有默认值 sort_order=0、is_default=false，避免显式 NULL 绕过数据库默认值。

### Gateway 清理代际

孤立清理删除 keyGeneration 时，在同一 marker 同步区内递增 globalEpoch，防止缺失代际被解释为0后复活旧 token。保守作废清理前的其他在途 token，沿既有同截止时间重试一次或503路径处理；后续新 token 正常工作。无代际被移除时不递增。保留 TTL、回源截止与本地协调，不接入 Redis 事务锁。
