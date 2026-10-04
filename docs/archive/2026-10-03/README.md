# 2026-10-03 归档

<a id="retired-migrations"></a>
## 已退出支持的旧库迁移资产

T-ACCESS-073 按明确支持决定，分别退出 071 自动授权旧库迁移、062 API 授权旧库迁移及配套回滚。当前仅支持当前权威 schema 新建库；本目录只供历史追溯，不是现役执行入口，不承诺脚本与后续 schema 兼容。未操作任何部署数据库。

- [自动授权旧库迁移手册](ops/runbook-auto-grant-migration.md)、[盘点脚本](ops/auto-grant-migration-preflight.sql)、[迁移脚本](ops/auto-grant-migrate-071.sql)。
- [API 授权退役手册](ops/runbook-api-retirement-062.md)、[退役脚本](ops/api-authorization-retire-062.sql)、[回滚脚本](ops/api-authorization-rollback-062.sql)。

旧测试与 fixture 可从 `71e3c5e34ebe612c9940f97a3cba9d2abbd7d236` 追溯；不在归档目录复制可被测试发现的 Java 资产。SQL 原文保持，手册仅调整相对链接，历史命令中的 `docs/ops` 路径不再可执行。当前运行指引见[重建手册](../../design/access-service-rebuild-runbook.md)与[服务暂停恢复](../../ops/runbook-service-mode-switch.md)。
