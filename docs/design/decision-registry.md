# 定案登记表（Decision Registry）

> 全仓定案的**唯一消费入口**：本表只存指针——日期 + 一句话口径 + 出处锚点 + 评审处置，定案正文仍在任务卡/设计文档，不在本表复制（模式同文档治理「入口引用不重复正文」）。会话内拍板、无仓库正文载体的定案，以本表为正文登记点（口径写全）。
>
> 初稿 2026-09-06（初始收编既有散点）；此后用户定案当轮追加，不跨轮补记。

## 写入协议

- 用户每次定案（含 AskUserQuestion 结论、会话内拍板）**当轮登记**新行，按日期顺序追加。
- 口径被新定案推翻：旧条目移入「已推翻」节并标注取代它的定案，**不删除**。
- 评审处置两档：
  - **再报直接撤回**：评审再报该口径即整条撤回，不进存疑队列、不占用用户决策时间；
  - **报了先核出处**：评审报相关问题时先对照出处锚点核实再定性。

## 消费协议

- 评审提示词（双轨子代理、codex）豁免段**整段注入本文件当前内容**，替代从记忆/任务卡拼装。
- 评审结论先对照本表：命中「再报直接撤回」即撤回；命中「报了先核出处」先核出处锚点再定性。
- 验收口径：连续两个任务的评审中，已登记定案零进入存疑队列。

## 定案条目

| 日期 | 定案口径 | 出处锚点 | 评审处置 |
|---|---|---|---|
| 2026-08-28 | 产品定位=开源通用 IAM；暂缓能力维持暂缓（自动授权写入口 20048 拒绝 true、动态数据权限端到端验证延后）；执行节奏=维护债→定位落地文档整改→简单页后端，复杂核心链路等用户时间充足再启动 | docs/design/README.md（定位节）、docs/tasks/README.md（后续节奏节） | 报了先核出处 |
| 2026-08-30 | 全局操作概念整体退役（T-PERM-049）：操作位空间按类型完全隔离 + DDL CHECK 焊死；includeGlobalFallback/全局回退/可空键轨全部移除，不得建议恢复 | docs/tasks/T-PERM-049.md、api-contract.md 头部退役注记、rebuild-runbook.md | 再报直接撤回 |
| 2026-08-30 | Locale：裸 toUpperCase/toLowerCase（土耳其 dotted-i）不修，normalizeCode 与全仓同款站点一律不动 | docs/tasks/T-PERM-034.md（P3 定案句）；全仓适用范围以本表为登记点 | 再报直接撤回 |
| 2026-08-30 | 无 resource_entity 投影的类型其实例级门禁=ID 空间错位：CONDITION/CONFLICT_RULE/DEPENDENCY 写门禁一律类型级（T-PERM-029/030/031） | api-contract.md（权限条件/冲突规则/资源依赖三节契约要点）、三张任务卡 | 再报直接撤回 |
| 2026-09-02 | BIGSERIAL 主键全仓 number 线格式（序列值物理不可能超 2^53）；字符串线格式仅位值/大数列 | docs/tasks/T-FE-016.md 验收（BIGSERIAL 条）、project-rules.md §7.4 | 再报直接撤回 |
| 2026-09-02 | 字段清空统一协议=xxxClear Boolean 标志四件套（标志 + UpdateEntity 强制写 null + 前端公式 + 契约条目），禁止「空串=清空」第二语义 | docs/tasks/T-FE-016.md 验收（extraClear 条）、api-contract.md §5.2/§5.3 extraClear 行 | 报了先核出处 |
| 2026-09-02 | 权限码=「资源:操作」结构：资源名须名词性实体、操作描述不做资源名；先复用目标实例权限检查再造新码 | api-contract.md（独立排查权限码否决句）、docs/tasks/T-PERM-033.md；名词性实体约束以本表为登记点 | 报了先核出处 |
| 2026-09-03 | TYPE_DEFINITION 反向变体：type-definition/list 门禁放宽为「类型级或任一实例级 VIEW」，实例投影登记 T-PERM-051 待做——评审勿报「实例级路径无自动产出」缺陷 | docs/tasks/T-PERM-051.md、api-contract.md 头部登记 | 再报直接撤回 |
| 2026-09-03 | 固定图（BootstrapGraphDefinition）冲突/边界问题一律登记 access-service-architecture §14.2 待议清单，攒批讨论，不零散改口径 | docs/design/access-service-architecture.md §14.2 | 报了先核出处 |
| 2026-09-04 | 四棵树统一写锁=Redisson（advisory 路径退役），unlock 挂 afterCompletion | docs/tasks/T-PERM-044.md | 报了先核出处 |
| 2026-09-05 | 资源类型级所有权（T-PERM-052）：每 resource_type 单一所有权（extra.managedMode MANAGED/SYNC + syncSourceService）；USER/ORG/MENU/ROLE 种子声明 SYNC+access-service；syncTypes 资源维度退役（subject/role/source 三维白名单保留）；评审勿再建议行级方案 | AGENTS.md 权限中心实现提醒节、docs/tasks/T-PERM-052.md、access-service-architecture.md | 再报直接撤回 |
| 2026-09-06 | 本地双轨评审与 codex 外评两轨分离：codex 仅用户显式触发，本地收口不自动串联，续跑由用户拍板 | dual-track-local-review / codex-external-review 两 skill 触发纪律节 | 报了先核出处 |
| 2026-09-06 | 决策提问协议：任何提问决策场景必须举例说明 | .claude/rules/decision-question-protocol.md | 报了先核出处 |
| 2026-09-06 | 树写锁不加 tryLock/lock_timeout 等待保险丝（会误杀排在 full-sync 后的合法长等待，与不配 statement_timeout 同向；恢复=重启持锁实例或 lease TTL 30s 兜底） | 本表（会话定案；机制背景见 docs/tasks/T-PERM-044.md） | 报了先核出处 |
| 2026-09-06 | TaskExecutionLeaseConcurrencyTest 全量负载时序抖动定案不修：多方法轮流红 + 隔离运行恒绿即定性为已知抖动，不调查本任务改动 | 本表（会话定案；历史实证散见各任务卡） | 报了先核出处 |
| 2026-09-06 | 瞬时状态不入记忆（仓库为载体）；待拍板问题当场按决策提问协议直问，不驻留记忆攒议程 | 本表（会话定规） | 报了先核出处 |
| 2026-09-06 | 工作流审计沉淀定案：评审收口已随两轨分离 skill 治理落地；定案登记表=本文件；回归 SOP 与锁协议收编 rule/skill 不立项（能力内容留在评审/回归相关记忆） | 本表（会话定案） | 报了先核出处 |
| 2026-09-06 | 权限排查页（permission-query）暂停待重做（T-FE-043 登记）：页面问题与功能记录后整体重做，后续任务对该页只做编译一致最小改动，不投入页面改造；评审勿报该页 UI/交互缺陷，改登记 T-FE-043 | docs/tasks/T-FE-043.md、api-contract.md §6.7 排查页暂停注记 | 再报直接撤回 |
| 2026-09-06 | AGENTS.md 只承载长期稳定事实与路由（外部评审核实后用户拍板）：任务进度/阶段枚举移出、指针化到 `docs/tasks/README.md`（禁以本文件历史任务号推断状态）；所有权段只留当前模型+错误码+防复活护栏，演进史交本表与架构文档；核心编码规范裁到高频硬约束（细则单一权威 project-rules）；新增 Agent 工作协议 5 步 | AGENTS.md | 报了先核出处 |
| 2026-09-06 | 统一响应壳全量更名 PermResult→R（工厂 success/error→ok/fail、Advice→RResponseAdvice、前端 TS 信封类型同步 R<T>）；线格式字段与取值不变（RWireShapeTest 锁）；引擎类 `dto/query/PermResult` 是权限查询内部结果、与响应壳是两个物，禁止合并或混淆 | common/model/R.java、project-rules.md §1.1、docs/design/frontend/service-interface-mapping.md | 报了先核出处 |
| 2026-09-06 | Agent 指令面收敛：删除 `.github/copilot-instructions.md`（第 4 份浓缩副本，漂移实证 3 处），规范入口=AGENTS.md + project-rules.md + skills/rules；勿再为 GitHub Copilot 重建独立指令副本（如需注入面用薄指针文件指向 AGENTS.md） | accessmesh-patterns SKILL.md（权威列表行）、本表 | 报了先核出处 |
| 2026-09-06 | 分页信封统一（T-ADMIN-027 登记）：全仓唯一分页方言=扁平五字段 `{items,total,pageNum,pageSize,hasNext}`，统一类更名 `PaginatedResp`→`PageResp`（线格式零变化）；admin 嵌套方言 `PaginatedResult` 退役删除；5 处 `R<List>` 裸数组收编 `ItemsResp`；分页字段不进 R（信封管传输/载荷管分页正交）；类名 `Page` 因与 MyBatis-Flex Page（19 文件在用）冲突被否 | docs/tasks/T-ADMIN-027.md、api-contract.md §3.3、project-rules.md §1.3 | 报了先核出处 |
| 2026-09-06 | 分页/列表信封双份重复类收编单源（T-ADMIN-027 执行定案）：`access.permission.dto.resp` 内部 `PaginatedResp`/`ItemsResp` 副本删除，全仓（admin/permission 域、SDK）统一 import perm-common `perm.common.dto.resp.PageResp/ItemsResp`（执行扩面：裸数组收编由建卡 5 处增至 6 处，含 FQ 写法漏盘的 `/notice/my-notices`）——后续演进只改一处，勿再建议恢复域内副本 | perm-sdk/perm-common dto/resp、project-rules.md §1.3 类名指引 | 报了先核出处 |
| 2026-09-06 | admin 分页端点参数面统一（T-ADMIN-026 设计定案：顺带对齐先例）：分页请求 DTO 不再允许裸 Integer 形态——FilePageReq/JobLogPageReq 补 `@Min(1)/@Max(100)` + 默认 getter（1/20），login-log/page 补 `@Valid`；漏传走默认、非法值 400，对齐 PageReq/Oauth2ClientPageReq 先例 | docs/tasks/T-ADMIN-026.md、admin-service-api-contract.md §4.7.3/§1.3 | 报了先核出处 |
| 2026-09-06 | 容器测试轨道基建与并行口径（T-ACCESS-030）：容器组单例基建 ItInfra + fork 级进程并行——sa-token 的 SaManager 是 JVM 级静态单例，线程级类并发被两轮实证否决，勿再建议 JUnit 线程并行；fork 标记 = user.home 文件锁槽位 1..4（surefire ${surefire.forkNumber} 在 systemPropertyVariables 插值空串、java.io.tmpdir 每 fork 不同，均不可用）；Redis 测试容器开 --databases 64 按槽位独占 16 索引段（利用 Redis 多 database，勿建议恢复共享段/奇偶两段式）；forkCount=${it.forkCount} 默认 2，-Dit.forkCount=1 串行逃生门 | docs/tasks/T-ACCESS-030.md、access-service/pom.xml、AGENTS.md 常用命令区、access-service/src/test/java/cn/ac/fage/accessmesh/access/it/ItInfra.java | 报了先核出处 |
| 2026-09-06 | E2E 独立模块分轨与 -T 模块并行恢复（T-ACCESS-031，用户决策 B+结构性拆分）：两条跨服务 E2E 验收 IT 迁独立 `e2e` 模块（reactor 末位），gateway 解除对 access-service/example-service 的 test-scope 依赖与容器轨死配置（勿再建议把跨服务 E2E 类放回服务模块——reactor 依赖边会使 mvn -T 对重模块完全失效，-T 未拆分实测 525s≈串行 522s）；日常全仓 `mvn test -T 1C -DskipE2E=true`、收口 `mvn test -T 1C`（E2E 必跑，ci.yml 单测 job 两开关都带）；e2e 模块须随 reactor 构建（-pl e2e 单跑会解析三服务 repackaged jar 必失败）；e2e（access-service 同款）surefire 的 `api.version=1.44` 钉版是 Docker 29+ 与 docker-java 3.3.6 兼容必需（缺它 @Testcontainers 静默跳过）；时序用例禁裸 sleep 余量（-T 负载已击穿两例，改轮询/提交闸门确定性机制） | docs/tasks/T-ACCESS-031.md、e2e/pom.xml、gateway/pom.xml、AGENTS.md 常用命令区与测试运行纪律、.github/workflows/ci.yml | 报了先核出处 |
| 2026-09-07 | 后端业务键统一入口=perm-common BusinessKeys（19 方法族，A+B 全收：跨类契约键+单文件内部键；格式 BusinessKeysParityTest golden 锁）；防回归仅 golden 锁，不加源码扫描守卫——评审勿再建议裸拼扫描守卫测试 | docs/design/permission-center/implementation.md §8、perm-sdk/perm-common util/BusinessKeys.java、AGENTS.md 核心编码规范行 | 再报直接撤回 |
| 2026-09-07 | BusinessKeys/SyncKeyCodec 名词命名偏离 project-rules §6.2「XxxUtil」：用户已知、计划后续 IDE 统一改名；改名落地前评审勿再报命名不符，规则例外句暂不写 | 本表（会话定案；类见 perm-common util/BusinessKeys.java、access-service permission/util/SyncKeyCodec.java） | 再报直接撤回 |
| 2026-09-07 | operationCodeKey 族不做大小写归一：授权域（先 toUpperCase 再拼）与查询/解析域（裸拼）语义不一致为已登记事实，保持现状待统一；统一方向（raw 严格化/归一宽松化+DTO Pattern）属行为变更需单独立项 | docs/design/permission-center/implementation.md §8.2 | 报了先核出处 |
| 2026-09-07 | T-PERM-051 四项执行定案（同批 AskUserQuestion）：①复合键长度溢出（type_key/type_code 各 ≤64 最坏 129 > 旧 code 列宽 128）处置=加宽 resource_entity.code 至 VARCHAR(256)，不加创建入口限长；②类型软删对「投影行下授权行」处置=级联软删（deleteResources 同款：markRoles/markServiceCodes + 投影行/授权行同事务软删），不走删除保护；③「TYPE_DEFINITION 加入保留清单」按机制现状落地=种子声明 SYNC+access-service（清单机制已被 T-PERM-052 收编，不复活字面清单）；④存量投影回填=bootstrap 启动自愈（对齐 T-ADMIN-025 ensureAdminFileFolder 先例）+ runbook FAQ 订正语句兜底，非纯手工 SQL | docs/tasks/T-PERM-051.md 实现记录、docs/design/schema/access-service.sql（列宽注释+种子声明）、access-service-rebuild-runbook.md §1/§3 | 报了先核出处 |
| 2026-09-08 | BusinessKeys 竖线族补收（codex 复评 P2-1 用户拍板全量收敛）：2026-09-07 首轮收敛遗留的 12 文件约 31 处 `\|` 裸拼点全量收编——八族进 BusinessKeys、sync_key 两族进 SyncKeyCodec；SignatureVerifier 签名载荷维持出界（基础设施键）；golden 锁扩至 30 用例 | implementation.md §8.1 范围补收句、perm-common util/BusinessKeys.java、access-service permission/util/SyncKeyCodec.java | 报了先核出处 |
| 2026-09-08 | Cookie 承载通道双向关闭（codex 外评 P1 修法二选一，用户拍板关闭而非补 CSRF 防护）：令牌仅经 Authorization 头显式传递（前端恒发 Bearer）——access-service `is-read-cookie: false`（sa-token 1.38.0 默认 true，不显式关闭登录响应会种无 SameSite/HttpOnly 标记的 Authorization Cookie）、Gateway `AuthTokenFilter` 删 Cookie 备选只认 Bearer 头 + yml 同步 false；双端 `saTokenConfigMatchesAuthority` 断言 + `cookieOnlyToken_rejected401` 回归锁钉住。评审勿再报「Gateway 存在 Authorization Cookie 认证通道构成 CSRF 面」——修法即关闭 | security-standards.md §3 本仓事实注记、gateway/access-service application.yml sa-token 块、AuthTokenFilter.java extractToken | 再报直接撤回 |
| 2026-09-08 | Feign Header 透传规范按现状对齐（codex 外评 P2，用户拍板仅修文档、不实现拦截器）：§14.2 改为「现状注记 + 目标态契约」双层表述（当前唯一装配=FeignInternalSyncInterceptor 仅注入 X-Internal-Secret/X-Service-Code，X-Tenant-Id 调用方注入，Authorization/链路追踪头未统一透传；日志关联依赖 Gateway X-Request-Id 头链），目标态链路头统一为 X-Request-Id（与 MDC traceId 同源）；实现统一拦截器属新功能需单独立项，仓内无活跃 Feign 消费方 | project-rules.md §4.3 现状行 + §14.2 | 报了先核出处 |
| 2026-09-09 | 全局域 CLASSIFY 声明生效（T-PERM-046 执行期用户三选一定案，选「让 CLASSIFY 生效」而非拒绝保存/容忍死配置）：全局域实际范围=有 CLASSIFY 声明按声明（声明即收窄覆盖动态补集，可为空集）、无声明退动态补集（未被非全局域认领的类型）；GLOBAL_PLUS 隐式段同源对齐（全局域有声明时=声明集，无声明/无全局域=未被认领补集，既有语义不变）；反查 findDomainIdsByTypeCodes 次序不变（非全局域认领优先、全局域兜底）。此前「全局范围维持补集动态计算」的任务卡口径随本定案作废；对存量部署零行为差异（全局域此前物理不可创建） | api-contract.md §5.1/§5.6、DomainClassifyServiceImpl（effectiveTypeCodes/matchesTypeCode）、schema access-service.sql biz_domain/domain_config 表注释、docs/tasks/T-PERM-046.md | 报了先核出处 |
| 2026-09-09 | T-PERM-046 执行口径两则：①错误码排号 20057 DOMAIN_GLOBAL_EXISTS（全局域已存在）/ 20058 DOMAIN_CONFIG_CONCURRENT_CONFLICT（并发保存提示重试），perm 段顺延 T-PERM-052 的 20055/20056 之后；②remove/save 并发窗口机制按任务卡首选落地=FOR UPDATE 域行锁（mapper 同事务连接，remove 用 selectValidByIdsForUpdate、save 域解析用 selectByCodeForUpdate），不引入 Redisson（T-PERM-044 选 Redisson 是树场景递归 CTE 校验锁不住行集合，本场景精确域行集合 FOR UPDATE 更优且无 Redis fail-closed 依赖）——评审勿再建议本场景换分布式锁 | docs/tasks/T-PERM-046.md、PermissionErrorCode（20057/20058）、BizDomainMapper（selectByCodeForUpdate/selectValidByIdsForUpdate） | 再报直接撤回 |
| 2026-09-09 | codex luna max 外评 T-PERM-046（commit 869b01a3c，read-only，session 01a083b0）：零 P0-P2、P3×2 均已处置——①getClassifiedTypeCodes 接口方法 Javadoc 旧口径残留当场修正（实现已按声明优先）；②业务域弹窗打开竞态（create 态 await 全局域预查期间点其他弹窗入口→慢网叠弹窗，表单独立提交不串数据）用户定案序列号守卫修法（openBizDomainForm 模块级序号，过期打开丢弃），弃「弹窗先开+异步填充」方案（props 需响应式改造且开关状态打开后突变）。评审勿再报「全局域预查致弹窗叠层」——已修 | frontend/src/views/system/biz-domain/index.vue openBizDomainForm、DomainClassifyService.java | 再报直接撤回 |
| 2026-09-09 | T-PERM-050 三项执行定案：①resource_type 删除=同事务级联软删该类型全部有效操作定义行（对称于创建联动预置 CRUD 四操作位），不走删除保护；②该类型下有效授权行（正常流仅剩 scope_all 类型级行）并入级联 + markRoles + OPERATION_PERMISSIONS_BY_TYPE per-type 失效（T-PERM-047 终态复用）；级联面限定 type_key=resource_type（type_value 仅 tenant+type_key 内唯一，同值不得跨族误伤）；③user_type/role_type 删除零检查面拆分登记 T-PERM-056 | docs/tasks/T-PERM-050.md 当前口径、api-contract.md §5.1、schema access-service.sql 三表注释 | 报了先核出处 |
| 2026-09-09 | T-PERM-056 user_type/role_type 删除处置定案（用户二选一拍板）：**删除保护**（类型下存在有效 abstract_user/abstract_role 行时整批拒绝 20056，管理员先清数据再删类型），弃级联——用户/角色是业务主体数据，对标 resource_entity 面=守卫先例（而非操作位/投影=级联先例），级联会连带 user_role 关系/授权/投影/缓存广播把一次删类型放大成静默蒸发一批登录主体。并发口径：role_type 面共持 ABSTRACT_ROLE 树写锁（自定义 role_type 角色行唯一创建入口=角色同步通道 sync/full-sync 且持同锁，管理面 updateRole/moveRole 亦可写已存在行但均持同锁、createRole 经 RoleType 枚举校验只接受种子类型，锁闭合完整）；user_type 面用户写入口无锁可复用，best-effort（对齐 T-PERM-050 级联并发先例）+ typeValue 软删不复用兜底。20056 message 措辞扩为「有效引用行——资源行/用户行/角色行」（复用错误码，不新排号） | docs/tasks/T-PERM-056.md 当前口径、api-contract.md §5.1、schema access-service.sql type_definition 表注释、PermissionErrorCode 20056、TypeDefinitionAppServiceImpl（rejectIfSubjectTypeReferenced + 锁块） | 报了先核出处 |
| 2026-09-09 | 权限查询统一引擎形态定案（grill Q1-Q7+Q15）：一引擎一套入参一个结果模型、多入口=参数预设——现状五套执行形态（query() 六工厂 / getDenied* 手写管线 / query-resources AppService 展开 / deleteRoles 局部规则 / canGrant 直查管线）全收编；树级展开拆两语义（判定面=查询前目标闭包改变结论、展示面=查询后条目克隆不改结论，清单面无判定面继承）；主语只认 roleIds（userId→角色解析入封装层含缓存）；resourceTypeCodes 用集合；位覆盖常开不可关；操作投影展开归展示面（结果双轨=原始行+覆盖投影）；带目标集 SQL 下推/无目标按角色全量（语义统一实现分层）；主资源上下文为一等入参；网关快照架构保留（ACCESS 单一位维持）。T-PERM-045 随之取消（范围并入 T-PERM-057） | docs/design/permission-center/query-engine-unification.md（终态设计，落地并入 implementation §3）、docs/tasks/T-PERM-057.md、docs/plans/permission-query-unification-plan.md | 报了先核出处 |
| 2026-09-09 | 判定面继承默认值矩阵 + 评估口径定案（grill Q12/Q13/Q5）：管理面写门禁（code/entityId 两轨）与读过滤面（菜单可见/组织可见/日志过滤）默认**开**（父授权覆盖子实例）；/auth/check 关+可选参数显式开（inheritMode 从「对单点判定结论无效」接通为目标闭包真实语义）；网关天然关；清单/视图面判定面继承**不适用**（无目标集，树扩展归展示面展开轨道、默认关+参数显式开）。管理面门禁条件评估**拉平为评估**（消除单点不评估/批量评估分叉）；冲突过滤**入参化**（默认运行时面开、配置面关，取代此前引擎常开的设计倾向）。行为差异（读面可见集变大/写门禁变严）随 T-PERM-057 回写 runbook FAQ | docs/design/permission-center/query-engine-unification.md §6/§7/§10、docs/tasks/T-PERM-057.md | 报了先核出处 |
| 2026-09-09 | T-API-002 check 族裁剪推翻（grill Q10+D1）：/auth/check、/auth/batch-check、/auth/check-interface 三端点**同口径全量回传**结果记录（matchedRoleIds/matchedPermissionIds 与 matchedResources[].resourceId 回线格式）——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场；**Query\* 响应族六字段裁剪维持不变**（推翻范围仅 check 族）；双副本 DTO（access-service+perm-common）同批改、CheckFamilyWireShapeTest 防回潮负向锁按新口径改写，实施 T-API-003。评审勿再报「check 响应泄漏内部 id」——该口径已按本定案推翻 | docs/tasks/T-API-003.md、docs/design/permission-center/query-engine-unification.md §9、api-contract.md §6.1/§6.2/§6.6（随 T-API-003 回写） | 再报直接撤回 |
| 2026-09-09 | 关联权限自动授权方向定案（grill Q8/Q11，T-PERM-054 暂缓原因更新）：**API 不单独授权、接口权限由操作权限关联派生**（授操作权限即有对应接口权限；网关按 {资源类型:操作权限} 关联映射 API 鉴定）为期望终态；方案与实施**后置**（现状 ACCESS-on-实体 + resource_api_mapping 绑定维持不动，单独任务 T-PERM-054 承载）；登录权限串维持 TYPE:OP+实例折算，与网关的一致性终态等本方向落地后在 {类型:操作} 基座对齐（粒度差=串类型粒度/门禁实例粒度为已知事实，不借道 T-PERM-054 挂起） | docs/tasks/T-PERM-054.md、docs/design/permission-center/query-engine-unification.md §9 | 报了先核出处 |
| 2026-09-09 | 权限视图/排查删除重设计方向（grill Q14+D2）：permission-view 七端点（effective-permissions/resource-users/role-permissions/effective-roles/resource-tree/explain/recent-changes）+ 前端排查页 + /query-permission-tree（零外部消费端点，D2 并入评估）拟删除重设计、具体范围待定（T-PERM-059）；**effective-permission-codes（登录串端点）不在删除面**；连动面（Gateway bootstrap 固定图两行、T-FE-043 absorb/cancel、architecture §14/runbook）随 T-PERM-059 处置 | docs/tasks/T-PERM-059.md、docs/design/permission-center/query-engine-unification.md §9 | 报了先核出处 |

## 已推翻（superseded）

| 原口径 | 出处 | 被取代 |
|---|---|---|
| T-PERM-040「includeGlobalFallback 合并参数 + 专属优先/全局回退」合并语义 | docs/tasks/T-PERM-040.md 退役注记 | 2026-08-30 全局操作概念退役（T-PERM-049） |
| T-PERM-037 投影轨条目（随全局操作键轨） | docs/tasks/T-PERM-037.md 失效注记 | 同上 |
| T-ACCESS-018「取消类型级保留」行级口径 | docs/design/access-service-architecture.md（T-PERM-052 取代注记） | 2026-09-05 资源类型级所有权（T-PERM-052） |
| 2026-09-06「SDK 直连端点内部 id 全线裁剪（T-API-002）」的 **check 族追加部分**（check/batch-check/check-interface 响应的 matchedRoleIds/matchedPermissionIds 与 matchedResources[].resourceId 裁剪）——**部分推翻**：原定案 Query\* 六字段裁剪仍有效，check 族三端点恢复全量回传 | docs/tasks/T-API-002.md（历史）、api-contract.md §5.7 注记 + §6.1/§6.2/§6.6（随 T-API-003 回写） | 2026-09-09 check 族三端点全量回传定案（T-API-003，见同日登记行） |
