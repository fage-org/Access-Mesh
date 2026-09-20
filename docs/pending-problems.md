---
doc_type: problems
title: 待解决问题清单
counter: Q-021           # 已分配最大问题号；分配后冻结，不复用不重排
last_updated: 2026-09-20（Q-016、Q-020 随 T-FE-054 收敛）
---

# 待解决问题清单（pending problems）

**定位**：登记**已确认存在、但暂不足以立任务/计划**的问题——方案未定、范围未明，或用户明示暂不解决。本文件是 design/plan/task 三层（`design-plan-task-lifecycle` skill）的**前置队列**：问题在此排队，一旦可执行（方案清晰/用户拍板启动）即转任务/计划并回填关联；不在本文件长滞。

**边界**：定案结论（含「不解决」拍板）唯一载体是 `docs/design/decision-registry.md`，本文件不复制定案正文；问题转出后方案细节唯一详细来源是任务卡，本文件只保留索引行。

## 未收敛问题

## Q-021 后端菜单种子 icon 多数未注册离线图标表——侧栏菜单图标渲染为空（ep/* 斜杠形态 × IconifyIconOffline storage 查找）

- **状态**：open
- **登记**：2026-09-19（T-FE-049 claude 外评存量观察①，主代理代码级核实机制链成立后登记）
- **来源**：T-FE-049 claude 外评
- **关联**：—（本次仅顺带注册占位项两态 2 枚 ep/warning-filled、ep/menu；侧栏种子面未触达）

**现象与证据**：`BootstrapGraphDefinition.java` 菜单种子 icon 全为 `ep/xxx` 斜杠形态（coins/connection/document/files/history/key/office-building/setting/share + home-filled）；侧栏 icon 经 useRenderIcon 无冒号即走 IconifyIconOffline（storage 查找），而 `frontend/src/components/ReIcon/src/offlineIcon.ts` 仅注册 5 枚（ep/home-filled、ep/warning-filled、ep/menu、ri/search-line、ri/information-line）——除 home-filled 外 8 个种子图标按机制渲染为空（@iconify/vue offline Icon 未命中 storage 渲染空；未做浏览器侧运行时验证，机制链完整）。

**影响**：侧栏菜单项有标题无图标（纯视觉缺失，无功能/权限影响）；存量面非 T-FE-049 引入（本次两态占位项触达的 2 枚已顺带注册生效）。

**设想方向（未定案）**：种子 icon 全量注册进 offlineIcon.ts（前端单侧）或后端种子收敛到已注册集合/在线形态；处置前建议先浏览器侧运行时验证存量实际形态（机制推断 vs 实际渲染）。

## Q-019 父组织名解析依赖当前已加载树（父节点被过滤/权限排除时回退「未知」）

- **状态**：open
- **登记**：2026-09-19（T-FE-050 claude 外评存量观察②，用户拍板登记）
- **来源**：T-FE-050 claude 外评
- **关联**：T-FE-050（发现载体；orgTree.ts 提取后解析入口集中）

**现象与证据**：user/index.vue 两处父名解析（信息卡模板 + openOrgForm）均从 `orgTreePanelRef.orgTree` 递归查找（utils/orgTree.ts），树面板带 `:org-type-filter="[1]"`（只显 orgType=1 普通组织）；后端 OrgResp 只有 parentOrgId 无 parentOrgName——父节点被类型过滤或权限范围排除在已加载树外时显示「未知」。

**影响**：显示回退「未知」无数据错、无写路径副作用；触发面=父节点被过滤/排除的边缘数据形态（如 orgType≠1 的父节点）。

**设想方向（未定案）**：后端响应体附带 parentOrgName（契约变更）或前端回退策略统一（回退展示与触发边界明确化）；T-FE-051 composable 化/树改造时可能自然重估。

## Q-018 后端 /org/update 通道可置空组织名（OrgUpdateReq.orgName 无非空校验）

- **状态**：open
- **登记**：2026-09-19（T-FE-050 双轨评审代码轨 P3-2，用户拍板登记）
- **来源**：T-FE-050 双轨评审
- **关联**：—

**现象与证据**：`OrgCreateReq.orgName` 有 `@NotBlank`，`OrgUpdateReq.orgName` 无任何校验注解（javadoc 自称「可选」），`OrgWriteAppServiceImpl:180` update 分支只判 `req.orgName() != null` 即 `setName(...)`——直连 POST /api/access/org/update 传 `orgName=""` 可将组织名写成空串入库。前端 UI 通道封死（OrgForm required + min 2）。

**影响**：入参校验缺口（「组织名非空」约定 update 通道未强制）；空名组织显示面为空串（树节点/信息卡），前端 findParentOrgName 对空名跳过（新旧实现等价）；无越权面。

**关联现象（T-FE-050 claude 外评存量观察①，用户拍板注记）**：空名数据态下 OrgForm.parentOrgDisplay 三段 fallback（OrgForm.vue:106-114）把空串父名渲染为「根组织」，与信息卡空白显示不一致——本 Q 修复（update 通道拒空串）后该数据态不可再造，渲染差异自然消失，无需独立动作。

**设想方向（未定案）**：update 通道补「null 跳过、空串拒绝」校验——注意直接加 `@NotBlank` 会连 null（=不更新语义）一起拒（Hibernate Validator 对 null 也判 invalid），须选 null 视为合法的约束（如 `@Pattern` 非空白）或服务层显式空串拒绝；配直连 API 回归锁。

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
| Q-016 | logOut 本地清理被服务端注销 await 推迟 + T-FE-048 两变体（同会话并发无 single-flight、跨会话旧响应覆盖） | closed（T-FE-054 done：2026-09-20 AskUserQuestion 四问拍板「注销改 fire-and-forget」——四子项全收口：①注销请求发出即不等（黑洞挂 ≤10s 消除）②本地清理同步段完成、注销完成后不再补清理（新登录凭据竞态消除，回归锁含红跑态 try/finally 收尾防污染）③refreshSessionCapability 入口内共享在途 Promise single-flight（指纹=accessToken，同会话并发只发一次 user-menu）④refreshUserMenu 回写（Pinia+userKey）与侧栏重建前代际守卫（旧会话响应〔成功/失败〕不污染新会话）。原登记行方向 B〔令牌代际守卫保留 await〕随拍板弃用；定案见 registry 2026-09-20 行） | [T-FE-054](archive/2026-09-20/tasks/T-FE-054.md) | 2026-09-20 |
| Q-020 | /menu-retry 页会话过期后「重新检查菜单」按陈旧状态提示（本地凭证已无时不发请求） | closed（T-FE-054 done：2026-09-20 拍板「随本卡收口」——initRouter 开头无凭证分支：统一提示「会话已过期」+ logOut 跳登录 + 抛 SessionExpiredError，menu-retry retry() catch 后不再按陈旧 menuLoadFailed 弹失真业务提示。判定挂在会话能力初始化统一入口（initRouter），非 menu-retry 单点判 token——2026-09-19「不做单入口判空」口径的落地形态；定案见 registry 同日行） | [T-FE-054](archive/2026-09-20/tasks/T-FE-054.md) | 2026-09-20 |
| Q-017 | user/index.vue 死解构 + 组织点击双请求（useUserManage 双实例各发一次 /user/page） | closed（T-FE-051 done：2026-09-19 AskUserQuestion 拍板「就地化」——index.vue 删除整个 useUserManage 实例（未消费解构整体清零），selectedOrgId 就地化本地 ref，onOrgChange 不再调 loadTable，成员表加载由 MemberTab watch(orgId)→onSearch 链路独占（点组织单请求、挂载即首载一次）；重复点击同一节点行为不变（watch 值不变不触发，原 index 实例刷新本就无人消费）。同卡两项拍板之二：handleToggleStatus 移入 hook 透后端 error.message。定案见 registry 同日行） | [T-FE-051](archive/2026-09-20/tasks/T-FE-051.md) | 2026-09-19 |
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
