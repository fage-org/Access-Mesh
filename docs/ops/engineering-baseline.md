# 工程验证基线

本地构建改动上游 API 时使用 `pwsh -File tools/build.ps1 access-service`（PowerShell），或在仓库根执行 `mvn install -pl access-service -am -DskipTests`。目标模块及全部 reactor 上游一起 install，后续局部启动不会拿到旧 SNAPSHOT；`mvn compile -pl access-service` 不满足这一点。脚本参数是模块相对目录，拒绝仓外路径和无 pom 的目录。测试仍按 AGENTS.md 的单测/收口轨道分别运行。

CI 为后端单测与收口测试记录实际耗时到 job summary，失败仍保持 Maven 退出码；目前不自动比较阈值。PR/手动触发跑完整容器与 E2E，push 保留单测并新增前端全部门禁。此安排避免同一次 push + PR 重复执行长容器组；若需要受保护分支的逐提交完整验证，再根据实际漏检事件或发布要求调整触发规则，本次不增加重复 job。

## 当前测量

| 环境/基线 | 命令 | 结果与耗时 |
|---|---|---|
| 2026-10-06，Windows / Java 21 / Docker Desktop，本计划最终修复工作树 | `mvn test -T 1C` | 2658 项，0 失败/错误/跳过；18:34 min (Wall Clock) |
| 同次大型参数用例 | ResourcePublicationHeavyPgIT | 65540 行，约 398.7 秒；属于收口必跑项 |

证据见 [本计划验证记录](../archive/2026-10-06/tasks/evidence/usage-review-20261006/final-verification.md)。该值是本机最终改动的实测，不代表 CI runner 性能承诺。

## 分层守护与文件规模评估

现有 `QueryBoundaryArchitectureTest` 已检查 13 能力包之间 Mapper 零容忍边界、只读 QueryMapper 方法命名、18 顶层包归属及引擎退役入口；`AccessServiceArchitectureTest` 复用同一 Mapper 规则并保护 BootstrapSeedWriter 的调用来源，另有 XML 与 PostgreSQL 行为检查。它们已在正常 Maven 单测轨执行，无需再建立第二套架构检查工具。规则不等于所有 Controller/AppService/DomainService 的任意跳层都已被机械覆盖；新增边界应在既有 ArchUnit 资产中按真实风险增加，不为“启用 ArchUnit”再造一套平行机制。

截至本次盘点，ResourceManageAppServiceImpl 约 1231 行、PermissionGrantPlanDomainServiceImpl 约 1074 行、UserManageAppServiceImpl 约 1044 行、OAuth2AppServiceImpl 约 1003 行。行数本身不是拆分理由；今后按独立业务职责、事务边界和已出现的修改耦合选择拆分点。实际拆分登记 [Q-065](../pending-problems.md#q-065)，本计划不进行通用重构。

## 工作区卫生

运行日志统一写入已忽略的 `.tmp/<任务名>/`，不再向仓库根持续堆 `.codex-*.log`。历史工作树先核对 `git worktree list`、归属与未提交内容，删除必须由所有者明确授权；不得清除当前工作树、其他聊天管理的工作树或 `frontend/node_modules`。本计划的历史工作树、根日志和根缓存实际删除，已按 2026-10-06 用户选择移出验收，未执行删除，已有备份保留。
