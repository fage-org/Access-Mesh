---
doc_type: task
id: T-ACCESS-049
title: 发布文档与版本化——quickstart/deployment 基线/CHANGELOG/README/LICENSE
status: review
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
  status: done
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

## 完成记录

- 提交 `e2854c689`（2026-09-16）。
- 六件交付：docs/quickstart.md（外部五步入口/两条启动路径/example 演练/FAQ 表）、docs/ops/deployment.md（七节基线：拓扑/密钥/TLS/XFF/UTC/数据升级/监控）、frontend/README.md（AccessMesh 口径 + 上游 Third-party Notice）、根 LICENSE（© The AccessMesh Authors）、CHANGELOG.md（v0.1.0 + 六条已知限制 + Unreleased 段）、根 README 分层（项目状态改 v0.1.0 叙事 + 已知限制摘要；文档表 +4 行；快速开始压缩为三命令）。
- 配套同步：docs/README 新增「外部使用者入口」+ 目录树补 quickstart/deployment；AGENTS.md compose 行补全栈档；compose 头注释翻指 quickstart。
- LICENSE 复核项落位：frontend/ 是 pure-admin-thin 明确派生——frontend/LICENSE 上游 MIT 声明保留未动 + frontend/README 以 Third-party Notice 交代署名；根 LICENSE 重写（用户 2026-09-16 拍板）。终稿待用户过目确认。
- 链接自检：新增/修改文档的相对链接目标全部实存（quickstart 7 链、deployment 5 链、README 文档表 8 链逐个核实）。
