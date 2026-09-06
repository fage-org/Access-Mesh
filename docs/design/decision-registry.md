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
| 2026-09-05 | 资源类型级所有权（T-PERM-052）：每 resource_type 单一所有权（extra.managedMode MANAGED/SYNC + syncSourceService）；USER/ORG/MENU/ROLE 种子声明 SYNC+access-service；syncTypes 维度退役；评审勿再建议行级方案 | AGENTS.md 权限中心实现提醒节、docs/tasks/T-PERM-052.md、access-service-architecture.md | 再报直接撤回 |
| 2026-09-06 | 本地双轨评审与 codex 外评两轨分离：codex 仅用户显式触发，本地收口不自动串联，续跑由用户拍板 | dual-track-local-review / codex-external-review 两 skill 触发纪律节 | 报了先核出处 |
| 2026-09-06 | 决策提问协议：任何提问决策场景必须举例说明 | .claude/rules/decision-question-protocol.md | 报了先核出处 |
| 2026-09-06 | 树写锁不加 tryLock/lock_timeout 等待保险丝（会误杀排在 full-sync 后的合法长等待，与不配 statement_timeout 同向；恢复=重启持锁实例或 lease TTL 30s 兜底） | 本表（会话定案；机制背景见 docs/tasks/T-PERM-044.md） | 报了先核出处 |
| 2026-09-06 | TaskExecutionLeaseConcurrencyTest 全量负载时序抖动定案不修：多方法轮流红 + 隔离运行恒绿即定性为已知抖动，不调查本任务改动 | 本表（会话定案；历史实证散见各任务卡） | 报了先核出处 |
| 2026-09-06 | 瞬时状态不入记忆（仓库为载体）；待拍板问题当场按决策提问协议直问，不驻留记忆攒议程 | 本表（会话定规） | 报了先核出处 |
| 2026-09-06 | 工作流审计沉淀定案：评审收口已随两轨分离 skill 治理落地；定案登记表=本文件；回归 SOP 与锁协议收编 rule/skill 不立项（能力内容留在评审/回归相关记忆） | 本表（会话定案） | 报了先核出处 |
| 2026-09-06 | SDK 直连端点内部 id 全线裁剪（T-API-002 执行中用户决策扩大面）：除原定案六字段外，check/batch-check/check-interface 响应的 matchedRoleIds/matchedPermissionIds 与 matchedResources[].resourceId 一并裁剪——评审勿报「check 响应缺来源 id 字段」类缺失 | api-contract.md §5.7 注记 + §6.1/§6.2/§6.6/§6.7 已删除字段条目、docs/tasks/T-API-002.md | 再报直接撤回 |
| 2026-09-06 | 权限排查页（permission-query）暂停待重做（T-FE-043 登记）：页面问题与功能记录后整体重做，后续任务对该页只做编译一致最小改动，不投入页面改造；评审勿报该页 UI/交互缺陷，改登记 T-FE-043 | docs/tasks/T-FE-043.md、api-contract.md §6.7 排查页暂停注记 | 再报直接撤回 |
| 2026-09-06 | AGENTS.md 只承载长期稳定事实与路由（外部评审核实后用户拍板）：任务进度/阶段枚举移出、指针化到 `docs/tasks/README.md`（禁以本文件历史任务号推断状态）；所有权段只留当前模型+错误码+防复活护栏，演进史交本表与架构文档；核心编码规范裁到高频硬约束（细则单一权威 project-rules）；新增 Agent 工作协议 5 步 | AGENTS.md | 报了先核出处 |
| 2026-09-06 | 统一响应壳全量更名 PermResult→R（工厂 success/error→ok/fail、Advice→RResponseAdvice、前端 TS 信封类型同步 R<T>）；线格式字段与取值不变（RWireShapeTest 锁）；引擎类 `dto/query/PermResult` 是权限查询内部结果、与响应壳是两个物，禁止合并或混淆 | common/model/R.java、project-rules.md §1.1、docs/design/frontend/service-interface-mapping.md | 报了先核出处 |
| 2026-09-06 | Agent 指令面收敛：删除 `.github/copilot-instructions.md`（第 4 份浓缩副本，漂移实证 3 处），规范入口=AGENTS.md + project-rules.md + skills/rules；勿再为 GitHub Copilot 重建独立指令副本（如需注入面用薄指针文件指向 AGENTS.md） | accessmesh-patterns SKILL.md（权威列表行）、本表 | 报了先核出处 |

## 已推翻（superseded）

| 原口径 | 出处 | 被取代 |
|---|---|---|
| T-PERM-040「includeGlobalFallback 合并参数 + 专属优先/全局回退」合并语义 | docs/tasks/T-PERM-040.md 退役注记 | 2026-08-30 全局操作概念退役（T-PERM-049） |
| T-PERM-037 投影轨条目（随全局操作键轨） | docs/tasks/T-PERM-037.md 失效注记 | 同上 |
| T-ACCESS-018「取消类型级保留」行级口径 | docs/design/access-service-architecture.md（T-PERM-052 取代注记） | 2026-09-05 资源类型级所有权（T-PERM-052） |
