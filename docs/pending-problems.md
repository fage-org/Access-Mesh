---
doc_type: problems
title: 待解决问题清单
counter: Q-069           # 已分配最大问题号；分配后冻结，不复用不重排
last_updated: 2026-10-08
---

# 待解决问题清单（pending problems）

**定位**：登记**已确认存在、但暂不足以立任务/计划**的问题——方案未定、范围未明，或用户明示暂不解决。本文件是 design/plan/task 三层（`design-plan-task-lifecycle` skill）的**前置队列**：问题在此排队，一旦可执行（方案清晰/用户拍板启动）即转任务/计划并回填关联；不在本文件长滞。

**边界**：决定（含“不解决”拍板）当轮归所属权威规范或任务，本文件只更新问题状态与引用；不复制规则、例外正文或转出后的实施细节。统一协议见 [文档治理](design/project-rules.md#decision-governance)，主题定位见[定案入口](design/decision-registry.md)。

## 未收敛问题

**阅读约定**：按根因或可共同处理的范围合并，子项各自保留证据、影响与既有边界。旧编号的去向见文末索引；编号合并不代表缺陷修复。原始登记细节见 [合并前快照](archive/2026-09-30/pending-problems-before-consolidation.md)，外部报告核实见 [核实记录](archive/2026-09-30/logic-review-verification.md)。本次整理没有重新运行业务复现。

<a id="q-059"></a>
## Q-059 管理员治理延后项：能力守卫与最后管理员保护

- **状态**：open
- **登记**：2026-10-05
- **来源**：使用者视角评审立项拍板（D1=B′ 收窄为种子行锁死，两项延后——用户明示「先记录，后面再考虑」）
- **关联**：[T-PERM-106](archive/2026-10-06/tasks/T-PERM-106.md)（已承接的种子行锁死）；[计划](archive/2026-10-06/usage-review-remediation-plan.md)

**现象与证据**：①user-role/assign 唯一门禁=操作者对目标角色持 ROLE:MANAGE（`UserManageAppServiceImpl.java:483-487,590-595`），对目标用户零门禁、bootstrap-admin 非保留角色——绑入全权角色无专项守卫与强审计；②无「最后管理员」概念——移除 bootstrap-admin 最后成员/停用最后管理员账号无任何关卡（种子行锁死后剩余的残余风险面）。

**影响与边界**：两者触发前提均为操作者已持 ROLE:MANAGE（仅 bootstrap-admin 成员），非低权越权；当前单管理员环境未发生。能力守卫候选形态=通用规则「绑入持 ROLE:MANAGE 行的角色要求操作者持同等能力+强审计」（零角色名特判）。

**设想方向（未定案）**：与审批机制（I5 治理路线图）合并评估；用户重启拍板后转任务。

<a id="q-060"></a>
## Q-060 权限排障能力新形态重做待重启

- **状态**：open
- **登记**：2026-10-05
- **来源**：使用者视角评审立项拍板（D16=B 只做 G2/G3 小改，重做待重启；G4/G5 首次归属重做面）
- **关联**：[T-PERM-107](archive/2026-10-06/tasks/T-PERM-107.md)（已承接的解释字段小修）；既有安排载体=[T-PERM-059](archive/2026-09-12/tasks/T-PERM-059.md)（done，「新设计方向另立任务」定案）与 [T-FE-043](archive/2026-09-12/tasks/T-FE-043.md)（cancelled，重做考虑事项随卡归档）；同事项旧登记 [Q-005](#q-005)（已收敛）；[计划](archive/2026-10-06/usage-review-remediation-plan.md)

**现象与证据**：2026-09-10 旧 permission-query 页面与 7 个诊断端点整体删除，重做安排既有载体在归档卡（T-PERM-059 定案③「另立任务」+T-FE-043 重做基线）——2026-10-05 D16=B 拍板维持延后并**新增 G4（实例粒度）/G5（授权时间线）归属重做面**，故立本条作活跃指针（延续 Q-005 收敛时「重做启动时从看板计数器取号」的裁定，编号即取号入口）。现存排障手段=auth/check（服务凭证专用面，管理员无开箱通道）+用户详情角色列表+自行拼四层接口心算条件与互斥；effective-permission-codes 粒度只到类型:操作；授权溯源无时间线读端点。

**影响与边界**：排障是 IAM 核心日常旅程，当前靠领域专家人肉拼图。重启约束：Q-045 定案（外部查询不开放 TRACE、生产固定关闭）；新诊断入口须先定义身份来源与诊断授权；新端点=bootstrap 固定图加行（存量库 fail-fast）。

**设想方向（未定案）**：单用户单资源「说人话」诊断入口+实例粒度+授权时间线；真实排障痛点积累后立项。

<a id="q-061"></a>
## Q-061 审计三表导出与保留策略（含 location 死列）

- **状态**：open
- **登记**：2026-10-05
- **来源**：使用者视角评审立项拍板（D11=维持现状永久保留，导出/分区/TTL 后续考虑）
- **关联**：[T-ACCESS-085](archive/2026-10-06/tasks/T-ACCESS-085.md)（已承接的门禁与留痕）；[计划](archive/2026-10-06/usage-review-remediation-plan.md)

**现象与证据**：operation_log/change_log/sys_login_log 三表 DDL 注释「永久保留」，全文 0 处 PARTITION、无清理作业、无导出端点（audit 包 grep export/csv/download 零命中）——「把三个月审计交给合规」只能分页 API 手动翻页或直连库导。`sys_login_log.location` 列 DDL 有、代码从不写入（死列）。

**影响与边界**：取证交付形态与增长上限无答案；长期运行 operation_log 是仅次于业务表的膨胀源。预览期数据量小，无真实合规需求支撑保留窗口参数选型。

**设想方向（未定案）**：按时间范围导出端点+PG 分区/保留窗口配置；合规需求出现后立项。

<a id="q-062"></a>
## Q-062 未成册五族契约补册（等接口完全稳定）

- **状态**：open
- **登记**：2026-10-05
- **来源**：使用者视角评审立项拍板（D9=暂不补写，等接口完全稳定再做）
- **关联**：[T-API-013](archive/2026-10-06/tasks/T-API-013.md)（已承接的导航件：册首指针/跨册锚链/错误码索引）；[计划](archive/2026-10-06/usage-review-remediation-plan.md)

**现象与证据**：契约总册明示五族不在册内补写（auth 登录族/dict/notice/job/login-log，T-ACCESS-040 登记）+四个零散端点，合计 36 端点无契约形状文档——外部消费者唯一出处是 Java DTO 源码。

**影响与边界**：第三方接入与脚本消费方需读源码；与「没界面、没出口」叠加成三重不可见。补册重访条件=接口完全稳定（避免文档腐化，原取舍理由维持）。

**设想方向（未定案）**：五族+零散端点最小形状表（字段+错误码）；login-log 已随 T-ACCESS-085/T-FE-065 转正先行补形状表。

<a id="q-068"></a>
## Q-068 登录辅助能力延后项集合（强制下线/验证码替代通道/跨标签会话承载）

- **状态**：open
- **登记**：2026-10-06
- **来源**：逐任务三通道评审 P3 处置时发现的任务卡登记缺口——T-ACCESS-082（两条延期项）、T-ACCESS-083（验证码延后）、T-FE-064（C10）各自声称「登记/延后」但本清单此前零对应条目（2026-10-06 逐任务评审补录合并）
- **关联**：[T-ACCESS-082](archive/2026-10-06/tasks/T-ACCESS-082.md)、[T-ACCESS-083](archive/2026-10-06/tasks/T-ACCESS-083.md)、[T-FE-064](archive/2026-10-06/tasks/T-FE-064.md)

**现象与证据**：三项延后能力在任务卡「非目标/遗留」节声明但无登记载体：①**强制下线**——管理员主动终结指定用户平台会话的能力（现仅重置密码链路顺带 logout，无独立入口）；②**验证码替代通道**——图形验证码的无障碍/行为式/音频替代（当前唯一形态 4 位纯数字，见 Q-067 关联的验证码强度议题）；③**跨标签会话承载**（T-FE-064 C10）——令牌改 HttpOnly Cookie 承载（security-standards §3 定案：启用 cookie 通道须先补 CSRF 防护，属前置约束）。

**影响与边界**：三项均为能力缺口非缺陷；现在没出事=预览期无对应需求压力。另：T-ACCESS-082 卡内「并发窗口已由 085e1d5fa 行锁实施闭合」的后续补丁指针缺失（卡为归档时点快照不回改，本条为指针补录载体）。

**设想方向（未定案）**：按需求优先级逐项立项；②启动时与 Q-067 修法评估联动（验证码形态影响计数锁与限流设计）；③启动时按 security-standards §3 前置补 CSRF 面。

<a id="q-069"></a>
## Q-069 OAuth2 client_id 跨租户命名冲突与存在性泄露

- **状态**：converted
- **登记**：2026-10-08
- **来源**：租户生命周期批次三通道评审（claude 通道发现并核实，定性"既有约束被本批多租户常态化放大"）；同日用户拍板修法方向
- **关联**：[T-ACCESS-097](tasks/T-ACCESS-097.md)

**现象与证据**：`sys_oauth2_client` 的 `uk_oauth2_client_id` 唯一索引不含 tenant_id（schema L116），客户端按 clientId 单参解析（OAuth2AppServiceImpl.getValidClient / RequestContextInterceptor）。租户 B 注册租户 A 已用标识（如 `admin-web`）被全局拒绝，且错误信息向 B 泄露该标识已被他租户占用。

**影响与边界**：历史单租户期撞名场景不存在；本批"各租户自行注册客户端"成为常态后冲突必然出现——跨租户命名耦合 + 存在性泄露，无数据损坏。

**拍板（2026-10-08）**：client_id 改租户内唯一（索引 (tenant_id, client_id)、客户端解析带租户、重复注册不泄露他租户存在性）；实施细节与验收见关联任务卡。

<a id="q-067"></a>
## Q-067 登录事务内同步 REQUIRES_NEW 审计造成连接池双重占用

- **状态**：open（用户拍板暂缓，2026-10-06）
- **登记**：2026-10-06
- **来源**：逐任务三通道评审 P1（本地双轨+claude+codex sol 六张任务卡独立发现，核实成立；修法分叉已列，用户拍板「记录问题，暂不解决」）
- **关联**：逐任务三通道评审（T-ACCESS-082/083/084/085/090、T-FE-064 六卡独立发现；评审过程报告 2026-10-06 按用户指令移出仓库，不再留链接）

**现象与证据**：`AuthAppServiceImpl.login` 整方法 @Transactional（085e1d5fa 行锁串行化拍板形态），事务内 5 个分支（:192/:200/:206/:213/:228）同步调 `LoginLogDomainServiceImpl.recordLoginLog`（REQUIRES_NEW，:52）。REQUIRES_NEW 挂起外层事务不还连接、内层再借第二条（Spring DataSourceTransactionManager 语义+官方池大小警告，及 mybatis-flex FlexTransactionManager 1.11.7 sources jar 双实证）；HikariCP 默认池 10、借连接等待超时 30s（application.yml 无覆盖）。10 个并发匿名登录（Gateway 白名单免鉴权可达）即占满全池，各审计等第二条连接 30s 超时，期间全服务所有 DB 操作停摆且可重发循环。

**影响与边界**：现在没出事=预览期无部署、无并发登录。审计独立于主事务回滚的语义（REQUIRES_NEW 的目的：失败分支抛异常回滚时日志必须留痕）必须保留；有问题的只是「事务内同步借第二条连接」的实现形态。

**设想方向（未定案，2026-10-06 已向用户列过）**：① 审计 @Async+REQUIRES_NEW（AuditDomainServiceImpl.asyncRecordLog 先例，当时推荐项）；② login 事务收窄重构（行锁读+校验+签发进窄事务，审计移事务外，改动面最大）；③ 仅调大连接池（缓解非修复，可叠加）。用户拍板暂缓；重启时从①评估。

<a id="q-065"></a>
## Q-065 大型应用服务的职责拆分评估

- **状态**：open
- **登记**：2026-10-06
- **来源**：[T-ACCESS-089](archive/2026-10-06/tasks/T-ACCESS-089.md) 已定范围：本次只评估，实际拆分进入路线图
- **关联**：[工程基线](ops/engineering-baseline.md)

**现象与证据**：资源管理、授权计划、用户管理、OAuth2 应用服务在本次盘点分别约 1232/1074/1044/1003 行（以 [工程基线](ops/engineering-baseline.md) 为准——2026-10-06 逐任务评审修正本行旧 959 漂移，登记面不维护独立计数），聚合多个相关入口和校验步骤，改动定位与测试准备成本较高。

**影响与边界**：现有功能有回归覆盖；尚无证据表明仅按行数拆分能改善事务边界或复用。不能为缩短文件把同一事务拆成分散编排。

**设想方向（未定案）**：后续真实功能改动时识别独立职责与复用单元，再决定是否抽取 DomainService；本计划不做大型重构。

<a id="q-056"></a>
## Q-056 apiRouteResourceKey 拼接存在 Q-044 同型理论碰撞面

- **状态**：open
- **登记**：2026-10-01
- **来源**：T-PERM-096 类推扫描（用户拍板登记）；[Q-044](#q-044) 同型
- **关联**：[T-PERM-096](archive/2026-10-04/tasks/T-PERM-096.md)（类推发现记录）；P3（理论可构造、现实未发生）

**现象与证据**：`BusinessKeyUtil.apiRouteResourceKey(method, path, resourceCode)` 以 `|` 拼三段做映射同步的内存索引，消费点 [MappingSyncHandlerImpl.java:79/90/95](../access-service/src/main/java/cn/ac/fage/accessmesh/access/resource/service/domain/impl/MappingSyncHandlerImpl.java)（存量行索引、incoming 活跃集、按资源 id 拼回查）。`pathPattern` 为 URL 自由文本（`|` 是合法 URL 字符无需转义）、`resourceCode` 自由文本且处尾段之前——与 Q-044 已修的「多个自由文本段相邻拼接」同形态。

**影响与边界**：碰撞需同时满足「某行 path 含 `|`」且「同方法下另一行字段恰可拼出同串」（如 `POST|/api/x|y` 与 path=`/api/x`、resourceCode=`y` 的行），当前实际注册路径未出现该形态；后果为同步过期清理误判（漏删/误删映射行），不涉及权限判定面。修法方向可循 Q-044 元组化先例（record 键或 percent-encode 中段）；属 Q-044 设想「内部消费者全量迁移分开评估」范围，随问题清单批次排期。

## 已收敛（含合并索引；详情在关联问题/任务或历史来源）

> closed（合并）只关闭旧登记编号，实际问题由关联 open 条目继续承载；修复、重复与合并分别注明，不回收编号。合并依据为 2026-09-30 本次“重复或类似问题精练合并”要求，原证据见 [合并前快照](archive/2026-09-30/pending-problems-before-consolidation.md)。

| Q-ID | 标题 | 收敛形态 | 关联 | 收敛日期 |
|---|---|---|---|---|
| <a id="q-064"></a>Q-064 | 不存在的 MVC 资源被兜底映射为 HTTP 500 | 已修复（2026-10-07 轻量清扫批）：common `GlobalExceptionHandler` 补 `NoResourceFoundException` 映射——HTTP 404 + 信封 code=404（与 SecurityException→403 映射形态一致），不再落 `Exception` 兜底 500；`ExampleServiceApplicationTest` 恢复业务端口未知路径断言（404+code=404 实跑绿；旧实现 500 已有 2026-10-06 实跑记录实证）。边界核对随批闭合：`NoHandlerFoundException` 需 `throwExceptionIfNoHandlerFound` 显式开启（默认关）本栈不可达不处理；`HttpRequestMethodNotSupportedException`（405 族）仍走兜底 500，非本条登记面不扩。架构册 `/actuator/**` 行同批订正 | —（2026-10-07 轻量清扫批，无任务卡载体） | 2026-10-07 |
| <a id="q-058"></a>Q-058 | MenuDomainService 两个零调用树写方法（deleteWithChildren/insertBatch） | 已删除（2026-10-07 轻量清扫批）：接口+实现整体删除，全仓零调用复核（含测试）；消除无锁树批量软删 API 未来误用绕过菜单树写互斥的通道（Q-048 同型；Q-010 死方法清理先例）；menu 定向测试 28 项绿 | [T-ADMIN-031](archive/2026-10-04/tasks/T-ADMIN-031.md)（发现载体） | 2026-10-07 |
| <a id="q-066"></a>Q-066 | sys_org.org_type 标签语义与数字解析的潜在错配 | 已修复（2026-10-07 轻量清扫批短期防御）：三处读面组装（OrgAppServiceImpl.toResp / UserAppServiceImpl 用户页 OrgBrief / UserOrgDomainServiceImpl.getUserOrgBriefs）收敛至 `OrgOperationCodeMapper.parseWireOrgType` 单源——null/空白/不可识别标签→null 降级不中断读取、历史标签 ORG/POSITION 归一 1/2、数值原样解析；红跑实证旧实现两炸法（null 与 POSITION 均 NumberFormatException，2 红）。**登记口径修正**：旧实现失败形态是信封 400「参数错误」（NumberFormatException 经 IllegalArgumentException 处理器），非登记的 500。独立边界保持：org_type schema 口径（SMALLINT+CHECK 或标签语义）未拍板未实施，脏数据现降级为 null 展示 | [T-API-011](archive/2026-10-06/tasks/T-API-011.md) | 2026-10-07 |
| <a id="q-032"></a>Q-032 | 创建挂载成员动作码与岗位裁剪边界 | closed（关联任务已验收，已确认的独立边界保持） | [T-ORG-005](archive/2026-10-04/tasks/T-ORG-005.md) | 2026-10-04 |
| <a id="q-038"></a>Q-038 | 树过滤展示根、资源状态与父组织名称 | closed（关联任务已验收，已确认的独立边界保持） | [T-ACCESS-065](archive/2026-10-04/tasks/T-ACCESS-065.md) | 2026-10-04 |
| <a id="q-015"></a>Q-015 | 设计、契约与代码注释漂移 | closed（文档/注释主题核对与原样 schema 验证完成；无本卡运行时行为变更） | [T-ACCESS-066](archive/2026-10-04/tasks/T-ACCESS-066.md) | 2026-10-04 |
| <a id="q-040"></a>Q-040 | 服务凭证覆盖运行时查询规划 | closed（运行时查询及剩余同步凭证接线已落地；真实密钥轮换按部署步骤执行） | [T-ACCESS-068](archive/2026-10-04/tasks/T-ACCESS-068.md)、[T-ACCESS-079](archive/2026-10-04/tasks/T-ACCESS-079.md)、[T-ACCESS-080](archive/2026-10-04/tasks/T-ACCESS-080.md) | 2026-10-04 |
| <a id="q-043"></a>Q-043 | 可选字段显式清空协议 | closed（逐域边界、DTO、真实 NULL 与已有表单已由 T-API-006 实施） | [T-API-005](archive/2026-10-04/tasks/T-API-005.md)、[T-API-006](archive/2026-10-04/tasks/T-API-006.md) | 2026-10-04 |
| <a id="q-045"></a>Q-045 | 外部查询不开放 TRACE 的适配边界 | closed（关联任务已验收，范围与证据见任务） | [T-PERM-102](archive/2026-10-04/tasks/T-PERM-102.md) | 2026-10-04 |
| <a id="q-028"></a>Q-028 | 主体组共享遍历等价重构 | closed（关联任务已验收，范围与证据见任务） | [T-PERM-101](archive/2026-10-04/tasks/evidence/T-PERM-101/verification.md) | 2026-10-04 |
| <a id="q-024"></a>Q-024 | 树根重叠守卫与并发互斥 | closed（关联任务已验收，范围与证据见任务） | [T-ORG-004](archive/2026-10-04/tasks/T-ORG-004.md) | 2026-10-04 |
| <a id="q-018"></a>Q-018 | 业务字段空白与列宽校验 | closed（关联任务已验收，范围与证据见任务） | [T-ADMIN-033](archive/2026-10-04/tasks/T-ADMIN-033.md) | 2026-10-04 |
| <a id="q-049"></a>Q-049 | cron 写前校验与本实例注册观测 | closed（关联任务已验收，范围与证据见任务） | [T-ADMIN-032](archive/2026-10-04/tasks/T-ADMIN-032.md) | 2026-10-04 |
| <a id="q-029"></a>Q-029 | OAuth2 委托租户与用户有效性 | closed（同租户及每次请求动态用户检查已由 T-ADMIN-035 实施） | [T-ADMIN-034](archive/2026-10-04/tasks/T-ADMIN-034.md)、[T-ADMIN-035](archive/2026-10-04/tasks/T-ADMIN-035.md) | 2026-10-04 |
| <a id="q-022"></a>Q-022 | grant_dep_id 闲置列去留评估 | closed（删除定案与 T-PERM-105 实施已落地） | [T-PERM-103](archive/2026-10-04/tasks/T-PERM-103.md)、[T-PERM-105](archive/2026-10-04/tasks/T-PERM-105.md) | 2026-10-04 |
| <a id="q-037"></a>Q-037 | 停用主体入口失败并静默清空授权草稿 | closed（入口禁用提示；失效预选确认放弃，取消保留草稿） | [T-FE-061](archive/2026-10-04/tasks/T-FE-061.md) | 2026-10-04 |
| <a id="q-021"></a>Q-021 | 菜单种子图标未完整注册 | closed（T-FE-062：离线注册补齐，保留既有键；coins/history 使用 coin/clock 图形） | [T-FE-062 证据](archive/2026-10-04/tasks/evidence/T-FE-062/verification.md) | 2026-10-04 |
| <a id="q-014"></a>Q-014 | 会话、网关测试依赖固定 sleep 构造时序 | closed（真实到期轮询、续写验证及同族固定余量清扫完成；完整并行回归通过，跨层安全预算按明确范围保留） | [T-ACCESS-067](archive/2026-10-04/tasks/T-ACCESS-067.md) | 2026-10-04 |
| <a id="q-057"></a>Q-057 | 用户角色分配入口的 roleKey/subjectKey 拼接与 roleTypeDomainKey+split 反解可构造碰撞 | 已修复（随 T-PERM-104 收敛，2026-10-03 拍板 C 双管齐下）：三写入口标识码 `subjectTypeCode`/`roleTypeCode`/`domainCode` 补 @Pattern（^[A-Z][A-Z0-9_]*$，与建域/建类型入口同款；`domainCode` 空串从「视为 null」改拒 400——全局域须显式传 null）+ 服务层分组/回读键改 record 元组键（split 反解删除，`BusinessKeyUtil.subjectKey/roleKey/roleTypeDomainKey` 三键退役）；核实修正登记口径两处（subjectKey 侧无静默碰撞随批统一元组化、batch-assign 链路无暴露——注解为入口族一致性对齐）；碰撞对回归锁旧实现实证红 2（「期望异常未抛」=静默错配形态）+ @Pattern 用例红 3；契约 §10.4 落账 | [T-PERM-104](archive/2026-10-04/tasks/T-PERM-104.md) | 2026-10-03 |
| <a id="q-048"></a><a id="q-048-closed"></a>Q-048 | 菜单创建、删除缺树写互斥 | 已修复；createMenu/deleteMenu 补 SYS_MENU 树写锁（锁先于门禁与首次树读取，对齐 updateMenu 与 §17.1 锁序），并发「删父+挂子」两方向交错分别收敛为 10201/10204；契约与验证见关联任务 | [T-ADMIN-031](archive/2026-10-04/tasks/T-ADMIN-031.md) | 2026-10-03 |
| <a id="q-031"></a>Q-031 | 同步资源 codeType 与业务键归一不一致 | 已修复；契约与验证见关联任务（T-PERM-100 两拍板：存量=无部署无存量不订正、寻址侧 TypeResolution 一并 trim 扩面；写入/寻址/发布指纹同源归一，契约 §12.1/§19.1/§19.2/§19.7） | [T-PERM-100](archive/2026-10-04/tasks/T-PERM-100.md) | 2026-10-03 |
| <a id="q-023"></a>Q-023 | 删除类型所有者角色缺引用守卫或提示 | 已修复；契约与验证见关联任务（T-PERM-099 三拍板：硬守卫整批拒绝+覆盖缺省引用+sync 通道扩面收口；契约 §10.3/§13.1/§19.4） | [T-PERM-099](archive/2026-10-04/tasks/T-PERM-099.md) | 2026-10-03 |
| <a id="q-050"></a>Q-050 | 操作位写入与准入目录校验边界不一致 | 已修复；契约与验证见关联任务 | [T-PERM-098](archive/2026-10-04/tasks/T-PERM-098.md) | 2026-10-03 |
| <a id="q-047"></a>Q-047 | 角色重新指派静默忽略窗口或关系变更 | 已修复；契约与验证见关联任务 | [T-ADMIN-030](archive/2026-10-04/tasks/T-ADMIN-030.md) | 2026-10-02 |
| <a id="q-027"></a>Q-027 | 新增分组角色未展开成员检查互斥 | closed（2026-10-02 随 T-PERM-097 收敛——实施核实任务前提与现实断层并经用户拍板改卡：危害场景要求新增行 `target_type='GROUP_ROLE'`，该形态自 T-PERM-043 起无任何写入方（现行写入口全部写死 `'ROLE'`），现行唯一活口（assign/sync 绑存量组角色）产出行在写守卫与运行时**一致不展开**、不发生 Y 静默失效，实际后果为零权限假绑定。改卡落地「绑定面入口收紧」：assign/batch-assign（字符串层+role_type=5 值层双保险）与 user-role sync/full-sync（scope 级、先于服务-类型白名单）拒绑 GROUP_ROLE（20022，对齐角色面先例），revoke 保留为存量行清理通道；持有侧/运行时组展开零改动；碰撞对红跑单测 5+PG 1 旧实现实证红。原「新增侧展开子树」设想随 role_inclusion 单事实源立项〔T-PERM-043 双事实源技术债〕另行处理） | [T-PERM-097](archive/2026-10-04/tasks/T-PERM-097.md) | 2026-10-02 |
| <a id="q-044"></a>Q-044 | 资源复合拼接键碰撞 | closed（T-PERM-096+T-FE-060 done：后端三内存索引（共享批量解析 resourceLookup、转授 resourceEntityIdByKey/结果映射）改结构化 record 元组，checkCanGrant 结果键=GrantCheckKey 本身；BusinessKeyUtil 四拼接构造器退役（resourceCodeTypeKey/resourceTripleCodeKey/grantCheckKey + 类推清扫零调用死方法 resourceTripleValueKey），golden 锁与对外协议键不动；前端 grant-keys.ts 单源 JSON 元组编码收口授权决策五段键/资源三段/树节点键/矩阵行键/主体树角色键 + 类推 changeGroupKey（inlineName 自由文本）；碰撞对回归锁双端旧实现实证红（后端 expected 100 got 200 与 duplicate element、前端 8 红）；元组边界口径入 engine/implementation §8.4、键约定入 permission-grant.md §2.3；类推核实 roleProjectionIndexKey 无碰撞形态（受限段+尾段自由文本）；roleKey/subjectKey 初判「无碰撞」经收口后外评订正（分配入口无 @Pattern+split 反解，登记 Q-057）、apiRouteResourceKey（path 中段自由文本）理论碰撞面登记 Q-056，两者均属「内部消费者全量迁移分开评估」范围随清单批次排期） | [T-PERM-096](archive/2026-10-04/tasks/T-PERM-096.md)、[T-FE-060](archive/2026-10-04/tasks/T-FE-060.md) | 2026-10-01 |
| <a id="q-055"></a>Q-055 | 签名过滤器顺序注释 | closed（重复登记，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-054"></a>Q-054 | 系统配置前端设计陈旧 | closed（文档漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-053"></a>Q-053 | 自动授权旧 TODO | closed（注释漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-052"></a>Q-052 | 业务字段缺长度校验 | closed（写入口字段校验同族，按 2026-09-30 本次合并要求归入 Q-018；合并本身不代表修复，当前处置见关联问题） | [Q-018](#q-018) | 2026-09-30 |
| <a id="q-051"></a>Q-051 | v3.5 旧准入设计未标替代 | closed（文档漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-042"></a>Q-042 | 用户角色查询端点旧指代 | closed（契约指代漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-041"></a>Q-041 | SDK 总览漏服务凭证拦截器 | closed（文档漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-039"></a>Q-039 | 资源树与列表状态过滤不同 | closed（树过滤与展示同族，按 2026-09-30 本次合并要求归入 Q-038；合并本身不代表修复，当前处置见关联问题） | [Q-038](#q-038) | 2026-09-30 |
| <a id="q-034"></a>Q-034 | 岗位可见性未按 VIEW_POSITION 精化 | closed（动作码入口覆盖同族，按 2026-09-30 本次合并要求归入 Q-032；合并本身不代表修复，当前处置见关联问题） | [Q-032](#q-032) | 2026-09-30 |
| <a id="q-030"></a>Q-030 | OAuth2 客户端与会话租户未匹配 | closed（委托链校验同族，按 2026-09-30 本次合并要求归入 Q-029；合并本身不代表修复，当前处置见关联问题） | [Q-029](#q-029) | 2026-09-30 |
| <a id="q-026"></a>Q-026 | 组织根节点 DDL 注释不一致 | closed（注释漂移同族，按 2026-09-30 本次合并要求归入 Q-015；合并本身不代表修复，当前处置见关联问题） | [Q-015](#q-015) | 2026-09-30 |
| <a id="q-019"></a>Q-019 | 过滤后树无法解析父组织名 | closed（树过滤与展示同族，按 2026-09-30 本次合并要求归入 Q-038；合并本身不代表修复，当前处置见关联问题） | [Q-038](#q-038) | 2026-09-30 |
| <a id="q-046"></a>Q-046 | 旧 /sync 写路径在 OPERATION_ADMISSION 下「能删不能增」——新路由整批 20071 且错误码指错方向 | closed（2026-09-28 随 T-ACCESS-062 收敛——登记时拍板的设想方向②落地：`POST /api/access/service-config/sync` 端点、`ServiceConfigSyncReq` DTO、`InterfaceSyncDefinition.from` v1 适配与前端「仅接口登记（旧协议）」选项整体删除（404 负向锁=LegacyInterfaceRetirementTest），接口声明唯一入口=sync-v2；契约 §25.1 同批改写） | [T-ACCESS-062](archive/2026-10-01/tasks/T-ACCESS-062.md) | 2026-09-28 |
| <a id="q-033"></a>Q-033 | 契约总册 org CRUD 门禁行/正文未带岗位精化码 | closed（2026-09-24 随 T-ACCESS-055 doc-only 收敛——§4 门禁表三行+§8.4~§8.6 补「按目标 orgType 解析精化码（岗位 *_POSITION）」注记，与 org-user-permission-contract 对齐；[历史定案原文](archive/2026-09-26/decision-registry-before.md) 同日行；正文条目 2026-09-25 补迁本索引） | [T-ACCESS-055](archive/2026-09-24/tasks/T-ACCESS-055.md) | 2026-09-24 |
| <a id="q-036"></a>Q-036 | PositionTab 展示面两处存量：位置列恒「-」与成员加载失败落空态 | closed（T-FE-058 done：①index.vue 传 org-tree prop 修复父路径解析；②展开区三态区分（成员列表/失败占位+重试/暂无成员），失败不再误显空态） | [T-FE-058](archive/2026-09-24/tasks/T-FE-058.md) | 2026-09-23 |
| <a id="q-035"></a>Q-035 | 新增岗位弹窗 initialData.parentOrgId 通道失效——上级恒默认根组织 | closed（T-FE-058 done：openCreatePositionDialog 改传 parentOrgId/parentOrgName prop 对齐 index.vue 先例；浏览器实测上级预选「默认组织」、不手选直接提交创建成功） | [T-FE-058](archive/2026-09-24/tasks/T-FE-058.md) | 2026-09-23 |
| <a id="q-025"></a>Q-025 | UserOrgAppServiceImpl 读面 resolveDefaultTreeOrgIds 私有副本与新共享入口并存 | closed（2026-09-22 随 T-ORG-003 收敛：换绑 OrgTreeConfigDomainService.resolveDefaultTreeOrgIds 共享入口并删除私有副本——[历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-21 行绑定的收敛时机兑现；退化根（配置在而根失联）由共享入口空返回统一折算 ORG_TREE_CONFIG_NOT_FOUND，正常形态两实现等价；顺带清无调用方死 helper isPositionOrg） | [T-ORG-003](archive/2026-09-24/tasks/T-ORG-003.md) | 2026-09-22 |
| <a id="q-016"></a>Q-016 | logOut 本地清理被服务端注销 await 推迟 + T-FE-048 两变体（同会话并发无 single-flight、跨会话旧响应覆盖） | closed（T-FE-054 done：2026-09-20 AskUserQuestion 四问拍板「注销改 fire-and-forget」——四子项全收口：①注销请求发出即不等（黑洞挂 ≤10s 消除）②本地清理同步段完成、注销完成后不再补清理（新登录凭据竞态消除，回归锁含红跑态 try/finally 收尾防污染）③refreshSessionCapability 入口内共享在途 Promise single-flight（指纹=accessToken，同会话并发只发一次 user-menu）④refreshUserMenu 回写（Pinia+userKey）与侧栏重建前代际守卫（旧会话响应〔成功/失败〕不污染新会话）。原登记行方向 B〔令牌代际守卫保留 await〕随拍板弃用；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-20 行） | [T-FE-054](archive/2026-09-20/tasks/T-FE-054.md) | 2026-09-20 |
| <a id="q-020"></a>Q-020 | /menu-retry 页会话过期后「重新检查菜单」按陈旧状态提示（本地凭证已无时不发请求） | closed（T-FE-054 done：2026-09-20 拍板「随本卡收口」——initRouter 开头无凭证分支：统一提示「会话已过期」+ logOut 跳登录 + 抛 SessionExpiredError，menu-retry retry() catch 后不再按陈旧 menuLoadFailed 弹失真业务提示。判定挂在会话能力初始化统一入口（initRouter），非 menu-retry 单点判 token——2026-09-19「不做单入口判空」口径的落地形态；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 同日行） | [T-FE-054](archive/2026-09-20/tasks/T-FE-054.md) | 2026-09-20 |
| <a id="q-017"></a>Q-017 | user/index.vue 死解构 + 组织点击双请求（useUserManage 双实例各发一次 /user/page） | closed（T-FE-051 done：2026-09-19 AskUserQuestion 拍板「就地化」——index.vue 删除整个 useUserManage 实例（未消费解构整体清零），selectedOrgId 就地化本地 ref，onOrgChange 不再调 loadTable，成员表加载由 MemberTab watch(orgId)→onSearch 链路独占（点组织单请求、挂载即首载一次）；重复点击同一节点行为不变（watch 值不变不触发，原 index 实例刷新本就无人消费）。同卡两项拍板之二：handleToggleStatus 移入 hook 透后端 error.message。定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 同日行） | [T-FE-051](archive/2026-09-20/tasks/T-FE-051.md) | 2026-09-19 |
| <a id="q-013"></a>Q-013 | TaskExecutionLeaseConcurrencyTest 剩余两个裸 sleep(1200) 方法未改有界轮询 | closed（T-ACCESS-051 done：takeoverAfterExpiryPreventsOldHolderFromOverwriting 改 5s 有界轮询至 tryClaim 接管成功、takeoverReexecutesWithSameIdempotencyKey 改每轮扫描+终态检查（断言语义均不变），终态条件抽 isTerminal 与 awaitTerminal 共用；双轨评审零 P0-P2；定向容器轨 10/10 绿 + 收口全量含 E2E 1724 项 0 失败。纪律出处 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-06/2026-09-16 行；评审上报三处同族裸 sleep 登记 Q-014） | [T-ACCESS-051](archive/2026-09-18/tasks/T-ACCESS-051.md) | 2026-09-18 |
| <a id="q-008"></a>Q-008 | SERVICE/API 固定图种子行维持 MANAGED，是否声明内部来源收紧 | closed（T-PERM-069 done：2026-09-18 用户拍板「仅 API 收紧」——①API 种子声明 SYNC+access-service，唯一事实入口=service-config/sync 接口声明通道+bootstrap 固定图，管理面资源 CRUD 20055（回归锁旧种子下实证失败）；②SERVICE 维持 MANAGED（新行唯一通道=管理面手工建行做按服务实例级授权，收紧即零 writer 死局，重启评估须以 service-config 联动建行配套为前置）；存量 dev 库 86 行 MANUAL 全为固定图零野行、订正语句登记 runbook；双轨评审全处置、全量含 E2E 1721 项 0 失败。定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-18 行） | [T-PERM-069](archive/2026-09-18/tasks/T-PERM-069.md) | 2026-09-18 |
| <a id="q-007"></a>Q-007 | sync 通道跨类型父子边是否收紧为同类型父边 | closed（T-PERM-068 done：三定案全落地——①sync/full-sync 显式异类型父边 NON_RETRYABLE/PARENT_TYPE_MISMATCH（先于父解析与版本写入）+ 缺省回填同类型（契约 §19.2 原意兑现，半传静默解挂漂移同步修复）；②管理面 create/batch-create 对齐 move 20053 + 单条裸 parentId 补存在性/类型校验；③判定面闭包止步与 remove 跨类型级联守卫保留作 DB 直写脏数据防线。10 回归锁旧实现下实证失败；全量含 E2E 1716 项 0 失败。定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-17 行） | [T-PERM-068](archive/2026-09-17/tasks/T-PERM-068.md) | 2026-09-17 |
| <a id="q-006"></a>Q-006 | ORG_VISIBILITY 缓存 key 改名后的滚动发布双命名空间失效 | closed（T-ACCESS-048 done：ORG_VISIBILITY_LEGACY evict-only 别名 + flush 同批双 evictAll + 未知覆盖键启动 WARN + 三处回归锁；部署镜像日志实证 legacy evict 生效；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-16 处置行，机制入 dual-layer-cache-framework skill 双副本。滚动发布过渡窗口结束后删除别名与第二次 evictAll 即回退面。2026-09-24 T-ACCESS-054 终结：用户确认无 pre-T-ACCESS-048 构建实例在跑（U011 拍板），别名条目+第二次 evictAll+三处回归锁整体删除，AccessCacheCatalogBoundaryTest 升反射精确集双向锁（9 条目），机制模式留 skill 双副本供未来 catalog 改名复用） | [T-ACCESS-048](archive/2026-09-16/tasks/T-ACCESS-048.md)、[T-ACCESS-054](archive/2026-09-24/tasks/T-ACCESS-054.md) | 2026-09-16 |
| <a id="q-009"></a>Q-009 | 存量跨能力 mapper 直读收敛（19 类 30 边冻结白名单的后续消化） | closed（T-ACCESS-043~046 done：四批全量收敛 30 边至零、白名单退役为零容忍绝对禁断、负向自证改测试源集夹具；全量回归含 E2E 绿 + 双轨评审；定案与硬契约见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-15 两行） | [capability-mapper-convergence-plan](archive/2026-09-15/capability-mapper-convergence-plan.md)（T-ACCESS-043~046，已归档） | 2026-09-15 |
| <a id="q-001"></a>Q-001 | URL 路径风格统一（admin 裸路径 vs perm 前缀路径） | closed（T-ACCESS-042 done：全链路单命名空间 /api/access/**——外部=服务路径、无 Gateway StripPrefix、无 admin/perm 家族段；登录族并入 /api/access/auth/**；user-role/list 双轨碰撞管理轨改名 view；一次性切换零兼容。定案与实施期裁决见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-15 行） | [T-ACCESS-042](archive/2026-09-16/tasks/T-ACCESS-042.md) | 2026-09-15 |
| <a id="q-002"></a>Q-002 | USER 写入口自身豁免的范围限定 | closed（T-PERM-067 done：收窄为档案字段——档案字段豁免保留、启停/删除不豁免（admin 轨 /user/update 自禁对齐 CANNOT_DISABLE_SELF 硬禁 + perm 轨死分支语义统一）、/user/reset-password 定位自助改密通道；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-14 行；盘点修正=可达暴露面全在 admin 轨） | [T-PERM-067](archive/2026-09-14/tasks/T-PERM-067.md) | 2026-09-14 |
| <a id="q-003"></a>Q-003 | operationCodeKey 族大小写口径不一致（授权域归一 vs 查询域裸拼） | closed（T-PERM-066 done：raw 严格化——入站 DTO @Pattern 大写 400/90001 + 定义侧锁死 + 授权域归一退役两域统一 raw；定案见 [历史定案原文](archive/2026-09-26/decision-registry-before.md) 2026-09-14 行，契约总册 §2.5 集中注记） | [T-PERM-066](archive/2026-09-14/tasks/T-PERM-066.md) | 2026-09-14 |
| <a id="q-004"></a>Q-004 | BusinessKeys / SyncKeyCodec 命名偏离 XxxUtil 规范 | closed（2026-09-14 轻量清扫批次：`BusinessKeys`→`BusinessKeyUtil`、`SyncKeyCodec`→`SyncKeyCodecUtil`，按 project-rules §6.2「去掉末尾 s」规则机械改名；代码+测试+XML 注释+skills 双副本+AGENTS+活设计文档（34+17 文件）同批替换，[历史定案原文](archive/2026-09-26/decision-registry-before.md) 带日期历史行不改写；golden 锁测试随类更名 `BusinessKeyUtilParityTest`） | [2026-09-14 批次四](archive/2026-09-14/README.md) | 2026-09-14 |
| <a id="q-011"></a>Q-011 | 资源依赖页（3.4）页面级真实联调与 mock 退役缺口 | closed（T-FE-044 done 且验收覆盖：Gateway +6 端点 + mock 退役 + 六场景冒烟；联调并修复 api 路径 /perm 前缀缺陷——登记时「api 层已按契约对齐」断言的路径部分被证伪，URL 契约锁 6 用例钉住） | [T-FE-044](archive/2026-09-14/tasks/T-FE-044.md) | 2026-09-14 |
| <a id="q-012"></a>Q-012 | mock/refreshToken.ts 模板死文件（拦截虚构端点、零调用） | closed（随 Q-011 并入 T-FE-044 顺带删除） | [T-FE-044](archive/2026-09-14/tasks/T-FE-044.md) | 2026-09-14 |
| <a id="q-010"></a>Q-010 | SystemConfigMapper.selectByTenantId 零消费死方法 | closed（随 2026-09-14 轻量清扫批次顺带删除：接口方法 + XML 语句；全仓零调用 T-ACCESS-037 已双轨核实，删除后 SystemConfigAppServiceImplTest 10/10 绿；无任务卡载体，登记口径即顺带删） | —（2026-09-14 归档清扫批次，见 archive/2026-09-14/README.md 批次二） | 2026-09-14 |
| <a id="q-005"></a>Q-005 | 权限视图/排查页删除后新形态重做 | closed（重复——任务层已有安排载体：T-FE-043 随卡归档「新形态另立任务」+ T-PERM-059 定案③「另立任务」；2026-09-13 用户裁定：已有任务载体的事项不登记问题清单，重做启动时从看板计数器取号） | T-PERM-059（done）、T-FE-043（cancelled，已归档） | 2026-09-13 |
| <a id="q-063"></a>Q-063 | 租户开通／运营能力 | converted → 已收敛（关联八项任务 done，验收覆盖） | [计划与验收](archive/2026-10-08/tenant-lifecycle-plan.md) | 2026-10-08 |
