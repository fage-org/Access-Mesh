---
doc_type: problems
title: 待解决问题清单
counter: Q-017           # 已分配最大问题号；分配后冻结，不复用不重排
last_updated: 2026-09-19（Q-017 登记）
---

# 待解决问题清单（pending problems）

**定位**：登记**已确认存在、但暂不足以立任务/计划**的问题——方案未定、范围未明，或用户明示暂不解决。本文件是 design/plan/task 三层（`design-plan-task-lifecycle` skill）的**前置队列**：问题在此排队，一旦可执行（方案清晰/用户拍板启动）即转任务/计划并回填关联；不在本文件长滞。

**边界**：定案结论（含「不解决」拍板）唯一载体是 `docs/design/decision-registry.md`，本文件不复制定案正文；问题转出后方案细节唯一详细来源是任务卡，本文件只保留索引行。

## 未收敛问题

## Q-017 user/index.vue 死解构 + 组织点击双请求（useUserManage 双实例各发一次 /user/page）

- **状态**：open
- **登记**：2026-09-19（T-FE-047 claude 外评存量观察，用户拍板登记 Q）
- **来源**：T-FE-047 claude 外评（存量观察①）
- **关联**：T-FE-050、T-FE-051（两卡均将触达 user/index.vue，按计划「user/index.vue 由 047/050/051 串行」协调口径顺带收敛）

**现象与证据**：`frontend/src/views/system/user/index.vue:64-78` 从 `useUserManage()` 解构 12 项实际只用 3 项（`pagination`/`loadTable`/`selectedOrgId`，其余 `handleDelete`/`handleCreate`/`handleUpdate`/`onSearch` 等 10 项为死引用）；且 index.vue 与 MemberTab.vue **各自实例化** `useUserManage()`（每调用一次全部 state 新建），每次点选组织：index.vue `onOrgChange` 调自家 `loadTable()` + MemberTab `watch(orgId)` 调自家 `onSearch()`——共发**两次** `/user/page`，其中 index.vue 实例的响应无人消费（其 tableData 从不渲染，MemberTab 渲染自家实例）。

**影响**：每次组织切换多一次无消费请求（幂等只读、纯浪费无数据错）+ 10 项死引用误导后来者；另 watch `immediate: true` 使 MemberTab 挂载即首载一次（index.vue 无初始加载，行为不对称）。

**设想方向（未定案）**：index.vue 收敛为只消费 MemberTab 已有链路（去掉自家 useUserManage 实例，`selectedOrgId` 等状态就地化）或状态上提共享单实例——具体形态随 T-FE-050（同文件递归 bug 修复）/T-FE-051（用户页 loadTable 接线试点）触达时定。

## Q-016 logOut 本地清理被服务端注销 await 推迟（后端黑洞挂 ≤10s + 窗口内旧清理链清新登录竞态）

- **状态**：open
- **登记**：2026-09-19（T-FE-045 claude 外评发现，用户拍板登记不修）
- **来源**：T-FE-045 claude 外评（P3）
- **关联**：T-FE-045、T-FE-054（token 过期提示相邻面，可并入该卡触达）

**现象与证据**：`frontend/src/store/modules/user.ts` logOut() 先 `await logout(...)`（显式携 token 注销）再执行本地清理链——axios 默认超时 10s（`utils/http/index.ts:19`），后端不可达且连接黑洞时用户点「退出登录」后界面最长挂 10s 无反馈（顶栏登出按钮无 loading 态，重复点击被 `logoutInFlight` 短路）；极端竞态：窗口内完成一次新登录（`setToken` 写新令牌），挂起的旧清理链恢复后 `removeToken()` 清掉新会话凭据并跳回 /login（可重登恢复，无数据损坏）。服务端健康路径仅延迟几百毫秒，无感。

**影响**：低频组合（后端黑洞 + 点退出）的体验问题 + 新登录凭据被旧登出链清除的竞态（无数据丢失）；与「本地清理不可被服务端失败绑架」定案在直观上相悖（最终仍清理，只是延迟）。

**设想方向（未定案）**：注销 POST 改 fire-and-forget（捕获令牌后不 await、清理立即执行，同时消除挂起与竞态；代价=「服务端注销优先」从「等注销完成」变为「请求已发出即清理」，防抖窗口缩至同步段）；或令牌代际守卫（保留 await，清理前比对 `getToken()` 已变则跳过清理）+ 顶栏退出 loading 反馈。

## Q-015 设计文档两处白名单「整族 /api/access/auth/**」陈旧口径（T-ACCESS-042 收窄漏改存量）

- **状态**：open
- **登记**：2026-09-19（T-GW-009 双轨评审发现，按登记默认处置——两册不在任务 design_refs，不顺带修超边界）
- **来源**：T-GW-009 双轨评审（文档轨 P3-3/P3-4）
- **关联**：T-GW-009（本批已同步修 gateway.md/application.yml/GatewayProperties 同款句）

**现象与证据**：T-ACCESS-042（2026-09-15）将 Gateway 白名单从整族 `/api/access/auth/**` 收窄为会话入口族精确清单后，两册活设计文档仍以整族形态描述白名单：`docs/design/architecture.md:171`「登录接口 /api/access/auth/** 在白名单中，请求透传到 access-service」；`docs/design/access-service-architecture.md:212`（OAuth2 透传段）「/api/access/auth/** 已由白名单覆盖（userinfo 无需重复配置）」——缺「运行时鉴权六端点除外」限定。T-GW-009 批次已将 gateway.md（核心链路/匿名白名单/OAuth2 段三处）、application.yml 注释、GatewayProperties javadoc 同款句精确化，本两册未触达。**清扫面补充（2026-09-19 claude 外评）**：access-service-architecture.md:212 同行末句「直连开放路径不校验（密钥拦截器豁免 oauth2/**）」属同族密钥豁免枚举半句，清扫时与整族句一并加限定/去枚举化（SecurityWebMvcConfig 类级 javadoc 的枚举形态已随 T-GW-009 外评处置去枚举化，不在此列）；另 `gateway/.../SignatureEnrichFilter.java:31/192` 注释称「HeaderEnrichFilter 之后、InternalSecretFilter 之前」与实际执行序不符（order=-35 晚于 InternalSecret(-40)，无运行时影响）——同批并入清扫（用户拍板 2026-09-19）。

**影响**：口径性漂移，无运行时缺陷——读者按整族形态理解会误判运行时鉴权六端点免鉴权（实际走会话/权限校验）。

**设想方向（未定案）**：轻量清扫批次顺带加限定语（各一行，无语义变化）；或随下次触达两册的任务带上。

## Q-014 会话/网关测试三处同族裸 sleep(1200)（时间轴构造形态）

- **状态**：open
- **登记**：2026-09-18（T-ACCESS-051 双轨评审发现，用户拍板登记）
- **来源**：[T-ACCESS-051](archive/2026-09-18/tasks/T-ACCESS-051.md) 双轨评审上报项
- **关联**：—

**现象与证据**：`PlatformSessionIdleTimeoutTest:156` 以 sleep(1200) 构造 3 次「间隔 1.2s 的活跃」断言续命 200（active-timeout=2s；sa-token 整秒除法口径下翻转阈值实测 ≈4s——剩余 <= -2 才判冻结，见该用例 L141 注释）；`PlatformSessionAbsoluteTimeoutTest:176~184` 以 4 处 sleep(1200) 拼 4s 绝对超时时间轴（t≈3.6s 活跃断言 200 须落在 4s 窗口内，累计拉伸预算仅 ≈400ms——与 Q-013 同量级）；`gateway/src/test/.../AuthTokenFilterTest:292~300` 以 4 次调用、3 次 sleep(1200) 构造续期间隔断言（网关冻结阈值按 3.5s 设计）。与 Q-013 同族（裸 sleep 表达时序，testing-standards rule §10.3）；历史全量回归未实证击穿。

**影响**：-T 1C 极端负载下 sleep 间隔被拉长——idle/续期两处余量较宽（需拉长至 ≈4s/3.5s 冻结阈值才翻转）；绝对超时一处累计预算 ≈400ms 是最可能先假失败的位置（t≈3.6s 断言拿到 401）；隔离定性成本重演，与 Q-013 同类。

**设想方向（未定案）**：改确定性时间轴表达（候选：会话 TTL/续期时间操控注入——涉及 sa-token 会话时间操控方式选型，非顺手量级，待轻量批次立项定性）。

## 已收敛（终态索引，一行一条；详情在关联任务卡/decision-registry）

| Q-ID | 标题 | 收敛形态 | 关联 | 收敛日期 |
|---|---|---|---|---|
| Q-013 | TaskExecutionLeaseConcurrencyTest 剩余两个裸 sleep(1200) 方法未改有界轮询 | closed（T-ACCESS-051 done：takeoverAfterExpiryPreventsOldHolderFromOverwriting 改 5s 有界轮询至 tryClaim 接管成功、takeoverReexecutesWithSameIdempotencyKey 改每轮扫描+终态检查（断言语义均不变），终态条件抽 isTerminal 与 awaitTerminal 共用；双轨评审零 P0-P2；定向容器轨 10/10 绿 + 收口全量含 E2E 1724 项 0 失败。纪律出处 registry 2026-09-06/2026-09-16 行；评审上报三处同族裸 sleep 登记 Q-014） | [T-ACCESS-051](archive/2026-09-18/tasks/T-ACCESS-051.md) | 2026-09-18 |
| Q-008 | SERVICE/API 固定图种子行维持 MANAGED，是否声明内部来源收紧 | closed（T-PERM-069 done：2026-09-18 用户拍板「仅 API 收紧」——①API 种子声明 SYNC+access-service，唯一事实入口=service-config/sync 接口声明通道+bootstrap 固定图，管理面资源 CRUD 20055（回归锁旧种子下实证失败）；②SERVICE 维持 MANAGED（新行唯一通道=管理面手工建行做按服务实例级授权，收紧即零 writer 死局，重启评估须以 service-config 联动建行配套为前置）；存量 dev 库 86 行 MANUAL 全为固定图零野行、订正语句登记 runbook；双轨评审全处置、全量含 E2E 1721 项 0 失败。定案见 registry 2026-09-18 行） | [T-PERM-069](archive/2026-09-18/tasks/T-PERM-069.md) | 2026-09-18 |
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
