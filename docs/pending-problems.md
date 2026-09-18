---
doc_type: problems
title: 待解决问题清单
counter: Q-013           # 已分配最大问题号；分配后冻结，不复用不重排
last_updated: 2026-09-17（Q-007 收敛；Q-013 登记）
---

# 待解决问题清单（pending problems）

**定位**：登记**已确认存在、但暂不足以立任务/计划**的问题——方案未定、范围未明，或用户明示暂不解决。本文件是 design/plan/task 三层（`design-plan-task-lifecycle` skill）的**前置队列**：问题在此排队，一旦可执行（方案清晰/用户拍板启动）即转任务/计划并回填关联；不在本文件长滞。

**边界**：定案结论（含「不解决」拍板）唯一载体是 `docs/design/decision-registry.md`，本文件不复制定案正文；问题转出后方案细节唯一详细来源是任务卡，本文件只保留索引行。

## 未收敛问题

## Q-013 TaskExecutionLeaseConcurrencyTest 剩余两个裸 sleep(1200) 方法未改有界轮询

- **状态**：open
- **登记**：2026-09-17（T-PERM-068 收口全量首跑失败调查发现）
- **来源**：[T-PERM-068](archive/2026-09-17/tasks/T-PERM-068.md) 非目标/遗留节
- **关联**：—

**现象与证据**：`takeoverReexecutesWithSameIdempotencyKey` 与 `takeoverAfterExpiryPreventsOldHolderFromOverwriting` 仍以 `sleep(1200)` 压 1s 短租约（200ms 余量）表达过期时序；T-PERM-068 收口当日全量首跑与隔离复跑共 3 次失败（两方法轮换：awaitTerminal 15s 超时 / tryClaim 返回 null 且接管扫描未命中），稳定态与基线（stash 对照）各 4/4 绿——负载敏感抖动。同文件 claimBlockedOverMaxAttempts 已于 2026-09-16 改 5s 有界轮询（registry 同日行，L290 注释点名该形态不可靠），两方法为漏改残留。

**影响**：高负载窗口（-T 1C 并行、机器热身期）下全量回归随机假失败一轮（本日实证），浪费隔离定性时间；违反「时序用例禁裸 sleep 余量」纪律（AGENTS 测试纪律）。

**设想方向（未定案）**：对齐 2026-09-16 先例改 5s 有界轮询（轮询至租约可接管/终态达成，deadline 兜底）——轻量清扫批次顺手收口即可，无需独立计划。

## Q-008 SERVICE/API 固定图种子行维持 MANAGED，是否声明内部来源收紧

- **状态**：open
- **登记**：2026-09-13（历史登记收编——原 2026-09-05 T-PERM-052 已知边界「另行评估」）
- **来源**：T-PERM-052 已知边界（登记，另行评估）
- **关联**：—

**现象与证据**：USER/ORG/MENU/ROLE 四类型种子已声明 SYNC+access-service（T-PERM-052 内部来源统一），SERVICE/API 固定图种子行维持 MANAGED——管理面手工 CRUD 现状允许，靠启动固定图校验保护。

**影响**：SERVICE/API 类型行可经管理面手工增删改，与内部事实链路类型的收紧口径不一致；现状靠 bootstrap 固定图校验兜底、无已知破坏。

**设想方向（未定案）**：声明内部来源收紧（对齐四类型）或维持 MANAGED 依赖固定图校验——另行评估。

## 已收敛（终态索引，一行一条；详情在关联任务卡/decision-registry）

| Q-ID | 标题 | 收敛形态 | 关联 | 收敛日期 |
|---|---|---|---|---|
| Q-007 | sync 通道跨类型父子边是否收紧为同类型父边 | closed（T-PERM-068 done：三定案全落地——①sync/full-sync 显式异类型父边 NON_RETRYABLE/PARENT_TYPE_MISMATCH（先于父解析与版本写入）+ 缺省回填同类型（契约 §19.2 原意兑现，半传静默解挂漂移同步修复）；②管理面 create/batch-create 对齐 move 20053 + 单条裸 parentId 补存在性/类型校验；③判定面闭包止步与 remove 跨类型级联守卫保留作 DB 直写脏数据防线。10 回归锁旧实现下实证失败；全量含 E2E 1716 项 0 失败。定案见 registry 2026-09-17 行） | [T-PERM-068](archive/2026-09-17/tasks/T-PERM-068.md) | 2026-09-17 |
| Q-006 | ORG_VISIBILITY 缓存 key 改名后的滚动发布双命名空间失效 | closed（T-ACCESS-048 done：ORG_VISIBILITY_LEGACY evict-only 别名 + flush 同批双 evictAll + 未知覆盖键启动 WARN + 三处回归锁；部署镜像日志实证 legacy evict 生效；定案见 registry 2026-09-16 处置行，机制入 dual-layer-cache-framework skill 双副本。滚动发布过渡窗口结束后删除别名与第二次 evictAll 即回退面） | [T-ACCESS-048](archive/2026-09-16/tasks/T-ACCESS-048.md) | 2026-09-16 |
| Q-009 | 存量跨能力 mapper 直读收敛（19 类 30 边冻结白名单的后续消化） | closed（T-ACCESS-043~046 done：四批全量收敛 30 边至零、白名单退役为零容忍绝对禁断、负向自证改测试源集夹具；全量回归含 E2E 绿 + 双轨评审；定案与硬契约见 registry 2026-09-15 两行） | [capability-mapper-convergence-plan](archive/2026-09-15/capability-mapper-convergence-plan.md)（T-ACCESS-043~046，已归档） | 2026-09-15 |
| Q-001 | URL 路径风格统一（admin 裸路径 vs perm 前缀路径） | closed（T-ACCESS-042 done：全链路单命名空间 /api/access/**——外部=服务路径、无 Gateway StripPrefix、无 admin/perm 家族段；登录族并入 /api/access/auth/**；user-role/list 双轨碰撞管理轨改名 view；一次性切换零兼容。定案与实施期裁决见 registry 2026-09-15 行） | [T-ACCESS-042](archive/2026-09-16/tasks/T-ACCESS-042.md) | 2026-09-15 |
| Q-002 | USER 写入口自身豁免的范围限定 | closed（T-PERM-067 done：收窄为档案字段——档案字段豁免保留、启停/删除不豁免（admin 轨 /user/update 自禁对齐 CANNOT_DISABLE_SELF 硬禁 + perm 轨死分支语义统一）、/user/reset-password 定位自助改密通道；定案见 registry 2026-09-14 行；盘点修正=可达暴露面全在 admin 轨） | [T-PERM-067](archive/2026-09-14/tasks/T-PERM-067.md) | 2026-09-14 |
| Q-003 | operationCodeKey 族大小写口径不一致（授权域归一 vs 查询域裸拼） | closed（T-PERM-066 done：raw 严格化——入站 DTO @Pattern 大写 400/90001 + 定义侧锁死 + 授权域归一退役两域统一 raw；定案见 registry 2026-09-14 行，契约总册 §2.5 集中注记） | [T-PERM-066](archive/2026-09-14/tasks/T-PERM-066.md) | 2026-09-14 |
| Q-004 | BusinessKeys / SyncKeyCodec 命名偏离 XxxUtil 规范 | closed（2026-09-14 轻量清扫批次：`BusinessKeys`→`BusinessKeyUtil`、`SyncKeyCodec`→`SyncKeyCodecUtil`，按 project-rules §6.2「去掉末尾 s」规则机械改名；代码+测试+XML 注释+skills 双副本+AGENTS+活设计文档（34+17 文件）同批替换，decision-registry 带日期历史行不改写；golden 锁测试随类更名 `BusinessKeyUtilParityTest`） | [2026-09-14 批次四](archive/2026-09-14/README.md) | 2026-09-14 |
| Q-011 | 资源依赖页（3.4）页面级真实联调与 mock 退役缺口 | closed（T-FE-044 done 且验收覆盖：Gateway +6 端点 + mock 退役 + 六场景冒烟；联调并修复 api 路径 /perm 前缀缺陷——登记时「api 层已按契约对齐」断言的路径部分被证伪，URL 契约锁 6 用例钉住） | [T-FE-044](archive/2026-09-14/tasks/T-FE-044.md) | 2026-09-14 |
| Q-012 | mock/refreshToken.ts 模板死文件（拦截虚构端点、零调用） | closed（随 Q-011 并入 T-FE-044 顺带删除） | [T-FE-044](archive/2026-09-14/tasks/T-FE-044.md) | 2026-09-14 |
| Q-010 | SystemConfigMapper.selectByTenantId 零消费死方法 | closed（随 2026-09-14 轻量清扫批次顺带删除：接口方法 + XML 语句；全仓零调用 T-ACCESS-037 已双轨核实，删除后 SystemConfigAppServiceImplTest 10/10 绿；无任务卡载体，登记口径即顺带删） | —（2026-09-14 归档清扫批次，见 archive/2026-09-14/README.md 批次二） | 2026-09-14 |
| Q-005 | 权限视图/排查页删除后新形态重做 | closed（重复——任务层已有安排载体：T-FE-043 随卡归档「新形态另立任务」+ T-PERM-059 定案③「另立任务」；2026-09-13 用户裁定：已有任务载体的事项不登记问题清单，重做启动时从看板计数器取号） | T-PERM-059（done）、T-FE-043（cancelled，已归档） | 2026-09-13 |
