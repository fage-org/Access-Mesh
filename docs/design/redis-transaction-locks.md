---
doc_type: design
title: 公共 Redis 事务锁与后端并发接入方案
status: draft
domain: common
last_reviewed: 2026-10-18
---

# 公共 Redis 事务锁与后端并发接入方案

> 方案草案。现行树锁、重置锁、数据库租约与锁序仍以已有权威设计为准；本稿不表示迁移或问题修复已经实施。

## 目标与现状

将 access-service 内的 RedisTransactionLockSupport 提取到 common，统一获取结果、事务结束释放与异常/中断清理。业务侧继续决定锁对象、竞争处理和需要保护的读取。提取公共类本身不会修复未参与互斥的写入口、锁前旧快照、MyBatis 一级缓存及整行旧值回写。

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

先提取现有公共实现并迁移树写/重置门面，既有真实 Redis 中断迟到成功、事务提交/回滚、重入、身份键隔离证据保持。随后按排查结论逐项修复业务覆盖，新增测试必须在旧实现下稳定失败；使用闩锁/提交闸门安排交错，不依靠裸 sleep。数据库行为使用既有 ItInfra，单元模拟不能代替持久化结果。

common API 变化后先 install 刷新上游 SNAPSHOT；各批先定向验证，实施收口执行根 reactor 的 mvn test -T 1C（包含 E2E/heavy），输出整文件保存且运行时不得改源码。实现完成再按代码轨、文档轨独立本地评审；不会自动追加外部评审。

## 边界

Redis 互斥不是数据库提交或外部副作用的强一致证明。原有树锁续期丢失边界、重置提交后交付窗口保持在原适用范围内；新增业务不能自动继承未论证的例外。更强的故障隔离应使用对应数据库约束/条件更新/fencing，不通过任意延长 TTL 声称解决。


## 排查来源与优先范围

本稿由 [T-ACCESS-121](../archive/2026-10-18/tasks/T-ACCESS-121.md) 产出；[原始报告与逐项核实](../archive/2026-10-18/tasks/evidence/T-ACCESS-121/verification.md)基于 8733a51c2 的后端现状。问题仍未修复，实施状态以任务看板与待解决问题为准。

| 优先级 | 问题入口 | 建议处置 |
|---|---|---|
| P1 | [Q-083 旧实体回写](../pending-problems.md#q-083) | 请求触达列 + 有效行条件；跨字段与投影放入共同事务守卫 |
| P1 | [Q-084 类型锁后旧缓存](../pending-problems.md#q-084) | 锁内首次解析或明确 fresh Mapper；不把清理 MyBatis 缓存塞进 common 锁 |
| P1 | [Q-085 岗位旧 parent](../pending-problems.md#q-085) | SYS_ORG 锁内批量读取结构，再构造 relation |
| P2 | [Q-086 主体删除与绑定](../pending-problems.md#q-086) | 领域共享主体生命周期协议，覆盖全部新增关系与清理路径 |
| P2 | [Q-087 主体 FULL 旧预载](../pending-problems.md#q-087) | 与主体生命周期同批设计；覆盖 single/FULL/漂移清理，保留版本 CAS |
| P2 | [Q-088 字典父子生命周期](../pending-problems.md#q-088) | 删除检查、创建、改绑共用父类型行锁，批量固定顺序 |
| P2 | [Q-089 Gateway 代际清理](../pending-problems.md#q-089) | 保证清理与 token 校验间不发生代际回退，使用本地同步 |

建议先修复 P1，再闭合主体与字典生命周期及 Gateway 清理。公共 helper 提取可以作为独立机械改动，但不能凭提取完成把上述条目关闭。较大范围的业务守卫实施应先明确调用点全集和各项验收，不恢复其他暂缓事项。
