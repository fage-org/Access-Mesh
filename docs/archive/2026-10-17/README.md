# 2026-10-17 归档

[架构与使用场景持续审查问题修复计划](scenario-audit-remediation-plan.md)已完成，15 张任务卡随计划归档至 `tasks/`，附属证据位于 `tasks/evidence/`。现行规范仍以 `docs/design/` 及项目规则为准，本批材料用于追溯。

- [任务索引](../../tasks/README.md)：保留任务终态与归档链接。
- [依赖修复与扫描证据](tasks/evidence/T-ACCESS-105/maven-remediation.md)：包含批准范围、原始报告、完整本地回归和真实 CI 验收。
- [CI 记录](tasks/evidence/T-ACCESS-105/ci-verification-2026-10-17.json)：提交 `f8f5ecbff30c930623ca54243e28aab46500b1eb` 的三项 push 门禁通过，报告工件已下载并核对 SHA-256。

本地完整后端回归 2870 项、前端 562 项通过；后端含容器/E2E/heavy，0 失败/错误/跳过。依赖安全门禁通过，仍保留已批准的 braces 开发路径风险和 Docker 测试依赖风险，不声称漏洞清零；边界与撤回条件见[现行安全规范](../../../.claude/rules/security-standards.md#6-依赖安全扫描)。

原先未纳入计划的 Q-070~Q-080 条目继续由[待解决问题清单](../../pending-problems.md)承接，不因本计划归档而自动关闭。
