---
doc_type: task
id: T-ACCESS-062
title: （ADM-T07）API 独立授权与 legacy 协议退役
status: done
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §6.6/§9.2/§9.4
  - docs/design/access-service-api-contract.md §11.4/§18.3/§18.4/§19.8/§25
  - docs/design/access-service-architecture.md §14
  - docs/design/engine/implementation.md §5.3
  - docs/design/engine/core-flows.md
  - docs/design/services/gateway.md
  - docs/design/schema/access-service.sql
  - docs/design/frontend/permission-grant.md
  - docs/design/frontend/service-interface-mapping.md
  - docs/design/extension-guide.md §2
  - docs/quickstart.md
depends_on:
  - T-ACCESS-061
  - T-PERM-092
blocks: []
acceptance:
  - "全服务迁完后受控清理全部有效 API 类型授权与旧快照缓存（N30：AUTHORITY_ROOT 等受保护行走受控迁移、不在普通授权页硬删；不误删业务授权或 API 登记目录；回滚数据可追踪）"
  - "API 授权生产入口退役：bootstrap 固定图删除 API:ACCESS 类型级 GrantSpec（BootstrapGraphDefinition T-API-001 行）与 apiRoutes 派生实例授权——删种子行使已初始化库重启呈固定图 fail-fast（沿 T-ADMIN-029/T-ACCESS-054 先例，处置=受控清理先行或重建库，runbook 记账）；授权写入口（apply-grant-plan/保存）对 API 类型拒绝（类型门禁先例形态）；前端授权页资源树 API 分支退役；登录权限串 API:ACCESS 段随授权清零自然收敛（无单独改造）"
  - "负向验收：空库启动后 role_resource_permission 零 API 类型授权行；写入口对 API 类型拒绝的错误码与 reason 回归锁"
  - "legacy checkInterface 在线/快照协议退役（旧命名空间、在途加载、负缓存一并处理）；API 登记目录保留（资源/接口元数据继续经 resource_api_mapping 与 API 资源行维护）"
  - "N29 终态确认：无永久双执行——R2 已完成（T-PERM-092）且全部服务已迁新模式，旧协议与旧授权模式退役闭环；计划两个完成条件全部闭合"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-28
---

# T-ACCESS-062 （ADM-T07）API 独立授权与 legacy 协议退役

## 背景

设计 §9.2 第二个完成条件（报告临时编号 ADM-T07）：本卡完成才表示 API 独立授权模式退役——不能用「R2 入口统一了」证明 T-PERM-054 完成。

## 范围

- 清理脚本/迁移受控通道与回滚账本；契约总册 legacy 章节退役标注；注册 API 保留目录。
- API 授权生产入口退役（bootstrap 种子行、写入口类型门禁、前端授权页分支）——API 登记目录与映射维护保留不动。

## 非目标 / 遗留

- 不删除 resource_api_mapping 的 API 登记功能（方案 A 仍消费登记实体与路由映射）。

## 当前口径

- 不考虑旧客户端兼容或专用错误码；API 类型写入按现有参数校验机制拒绝（20044，中文说明原因），旧协议直接删除。（2026-09-28 确认）

- 存量清理覆盖所有租户、所有来源的有效 API 类型授权，先盘点和备份，再受控删除；保留非 API 业务授权、API 登记实体及映射。发现非 API 子授权引用待删 API 父授权时停止并报告，不自动级联删除业务授权。（2026-09-28 确认）

- 服务配置不再提供鉴权模式选择：删除 `service_config.api_auth_mode`、服务配置请求/响应中的 `apiAuthMode`、对应枚举及前端控件。全部服务统一使用业务操作准入；协议响应中标识新协议的常量不属于服务配置开关。存量迁移与旧版本回退须使用成套代码及数据备份，不保留运行时切回旧模式的入口。（2026-09-28 确认）

## 验收对照

- API 授权生产入口：bootstrap 删除 API 类型级与实例级授权；授权计划预览/保存拒绝 API 主行、子行及遗留行操作，系统种子入口同样拒绝；前端类型候选、资源树和操作列排除 API。空库零 API 授权与旧固定图拒启由 AccessBootstrapPgIT 验证。
- 协议退役：check-interface、interface-snapshot、旧 sync 端点及专用 DTO/装配器删除，404 负向测试覆盖；服务配置模式字段全链路删除。接口登记使用 sync-v2，业务准入要求必填；旧查询执行体已由 T-PERM-092 删除，新准入和业务最终检查不保留双执行。
- 存量清理：迁移/回滚脚本与运行手册位于 docs/ops；多租户、三种授权来源、异常业务子引用、整批回滚与不可覆盖备份由 ApiAuthorizationRetirementPgIT 验证。旧/新网关快照均为本地 L1，退役部署需停止全部旧节点以清除缓存、负缓存及在途加载；共享权限事实缓存按目录 SCAN 清理。

## 完成记录

- 2026-09-28 开发库：按确认停止旧 access-service（端口 9119），连接释放后执行 `api-authorization-retire-062.sql`；有效 API 授权 107→0，模式列删除。非 API 授权、resource_entity 与 resource_api_mapping 迁移前后整表哈希一致；未清理 API 登记目录。
- 开发库完整备份保存于 `C:/Users/li/.codex/backups/Access-Mesh/T-ACCESS-062-20260928/dev-before-t062.dump`，SHA-256=`7AD6289F4C0052FB12A1E2EBE7ADE85AC435F0CE7F1E011ABECCF140857ED059`。独立数据库 `access_t062_rehearsal_20260928` 完成迁移→回滚演练，授权及目录/映射整表哈希恢复一致；迁移账本保留原行与执行信息，运行库维持退役后状态。
- Redis 按 `perm:role-perm-snapshot`、`perm:effective-roles`、`access:org-visibility` 目录执行 SCAN 检查，残留键为零；旧服务保持停止。
- 2026-09-28 全量回归 `mvn test -T 1C`：2354 项，0 失败、0 错误、0 跳过，BUILD SUCCESS；包含 access-service 容器组 434 项（迁移专项 6 项及 heavy）、跨服务 E2E 27 项。初始退役负向测试在删除前失败，删除后通过；范围/业务查询与新准入验收继续保留。
- 前端 `pnpm lint`、`pnpm build`、`pnpm typecheck` 通过；补齐 API 授权候选过滤测试的浏览器存储夹具后，`pnpm test` 454 项全通过、无未处理异常，修改测试文件的 ESLint/Prettier 检查通过。
- 本地代码轨实证通过：授权主/子行与系统种子关闭、预览/保存共用守卫、空库零 API 授权、旧库拒启、停用终校验与配置代次保留、迁移事务和回滚保护；无未决设计项或需另增机制。文档轨实证通过：契约/schema/架构/引擎/接入指南与页面口径一致，权限查询 skill 双副本一致，修改 Markdown 的文件链接检查与轮次词扫描通过。
- 2026-09-28 收口评审（本地双轨＋claude/grok 双通道）：claude P2×2（API 类型非 ACCESS 操作可作准入要求成恒 deny 死配置；网关白名单防漂移锁未随六端点更新）、P3×4 与 grok P3×1 全核实成立并同批处置——准入要求禁 API 类型整类收窄（写侧 20071＋读侧 assembler 兜底 20071＋前端类型/操作双滤＋schema/契约/盘点手册同步，EXPORT 反例与 PgIT API 类型用例为回归锁、旧实现下失败）；touchedIds 分支补回归锁；退役清扫 24 处（注释/Javadoc/五册文档/权威 DDL/e2e 探针）；Q-046 收敛；退役手册补「回滚后重新迁移」小节；死代码四项裁剪（preserveEnforce/selectForSnapshot/bootstrap 实例分支/前端 version 恒真分支，用户拍板）。grok「bootstrap 缺操作指向 059 脚本已失效」主张经核撤回（059 面向未迁移旧库，与 062 后状态不相交）。定向验证：access 单测轨 4 类＋AccessBootstrapPgIT 18＋InterfaceAdmissionPgIT 18（含 heavy 2）全绿；前端 typecheck/eslint/定向 vitest 全绿。
- schema 的配置代次注释同步前向/回滚 SQL 后，`mvn test -pl access-service -Dtest=ApiAuthorizationRetirementPgIT -Dsurefire.failIfNoSpecifiedTests=false` 于 2026-09-28 复跑 6 项全通过；运行库同步执行同一 COMMENT，未改业务数据。N29/N30 完成，计划的旧引擎退出与旧协议退出两个技术完成条件均闭合；所属计划其他任务状态仍按各自验收维护。
- 2026-09-28 追加外评（贴回结论核实）处置三项，全部事实性修正无设计取舍：盘点手册 ③ 补操作定义完整性校验（类型不可反查/API 类型在启用映射命中即快照 20071→网关 503、binary_bit 非单个正位为写侧必拒脏数据，对齐读侧触发条件）；② 纠正重叠路径歧义拦截时机——非快照构建期，请求匹配期网关本地等价检测→503、在线 20070，§三补恢复流量前实际路径抽验（通配遮蔽精确路由必抽，503 即不恢复）；20055 运行时提示与同款残留由 service-config/sync 订正为 sync-v2（guard×3/LocalProjectionOwner/PgIT 断言收紧为 v2 锁/e2e 注释/schema extra 注释/rebuild-runbook/architecture），刻意保留面（负向锁、冻结迁移脚本、closed 决策记录、显式「已删除」叙述）不动。定向 `ServiceConfigCascadePgIT` 复跑通过。
