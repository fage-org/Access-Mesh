---
doc_type: task
id: T-ACCESS-049
title: 发布文档与版本化——quickstart/deployment 基线/CHANGELOG/README/LICENSE
status: proposed
plan: docs/plans/release-preview-plan.md
domain: access-service
design_refs:
  - docs/design/extension-guide.md
  - docs/design/README.md#产品定位与能力叙事三档口径
  - docs/README.md
depends_on: [T-ACCESS-047]
blocks: [T-ACCESS-050]
acceptance:
  - "docs/quickstart.md 新建：系统要求 + 全栈/开发模式两条启动路径 + 首管理员登录 + example 403→授权→200 演练（复用 extension-guide §2，不重写契约）"
  - "docs/ops/deployment.md 新建：生产部署基线（HTTPS 终止/密钥环境变量/nginx 同源反代/XFF 多层代理前提/管理端口回环/bootstrap 单实例/UTC 语义）——security-standards 对外前提的正式落档"
  - "frontend/README.md 重写为 AccessMesh 口径（上游 pure-admin 宣传页退役）"
  - "根 LICENSE 重写为本项目名义（2026-09-16 用户拍板：项目已与 RuoYi 无关）"
  - "CHANGELOG.md 新建（Keep a Changelog 体）：v0.1.0 能力概览 + 已知限制（单租户无运营/PERSONAL、GROUP_ROLE 未交付/动态数据权限暂缓/DDL 无迁移框架/bootstrap 单实例）"
  - "根 README.md 精简分层：对外叙事与内部项目状态（T-XXX 密集段）分离，三档口径不变；docs/README.md 补外部阅读入口；AGENTS.md compose 行同步"
  - "链接自检：新增文档被索引引用、无悬空；docs-governance 关键词扫描过"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-16
---

## 背景

README 现有「快速开始（开发中）」面向内部开发者、混排任务叙事；无系统要求/部署文档/CHANGELOG；frontend/README 是 pure-admin 上游宣传页（会误导外部用户去上游提 issue）；根 LICENSE 版权行为上游 RuoYi-Cloud-Plus。

## 范围

quickstart、deployment 基线、frontend README、根 LICENSE、CHANGELOG、根 README 精简、docs/README 与 AGENTS 指针同步。

## 当前口径

- **LICENSE（用户拍板 2026-09-16）**：根 LICENSE 重写为本项目名义（项目已与 RuoYi 无关）。**单列复核项**：`frontend/` 是 pure-admin-thin 明确派生（package.json name 仍为 pure-admin-thin），MIT 对 substantial portions 要求保留上游声明——frontend/ 内以上游声明文件保留署名（根 LICENSE 重写不回改）；执行期向用户展示终稿确认。
- quickstart 的 example 接入演练直接引用 extension-guide §2（五步接入），不在 quickstart 复制契约细节。
- deployment.md 属运维手册类（docs/ops/），只写部署前提与基线，不复写 security-standards 全文（内部规则文件保持原位）。
- CHANGELOG 首版只写 v0.1.0（能力概览+已知限制），历史不回溯补写（内部历史以 docs/README 归档记录表为权威）。

## 验收对照

见 acceptance 七条。

## 非目标 / 遗留

- 不建 CONTRIBUTING/SECURITY.md/英文版文档（后续发布批次）；不动 CI；版本号变更本体在 T-ACCESS-050 执行（本任务只备 CHANGELOG 与叙事）。
