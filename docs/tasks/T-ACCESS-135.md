---
doc_type: task
id: T-ACCESS-135
title: 候选版本验收收口
status: proposed
plan: docs/plans/candidate-deployment-acceptance-plan.md
domain: cross-service
design_refs:
  - docs/design/architecture.md
  - docs/design/extension-guide.md
  - docs/design/services/example-service.md
depends_on:
  - T-ACCESS-134
acceptance:
  - 所有验收证据能对应最终候选源码产物与配置且必要场景无未执行或阻断
  - 完整后端回归与前端质量门禁通过并确认 E2E/heavy 无误跳过
  - 本地双轨、设计回写、使用文档及候选结论核对完成
  - 计划任务索引和附属证据按生命周期规范归档
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-18
---

## 背景

本卡将 T-ACCESS-131～134 的产物与实测结果收敛为最终候选结论。历史测试通过记录仅作参考，不能自动替代本候选的部署和接入验收。

## 范围

核对候选提交、源码/构建输入摘要、JAR/前端包摘要、镜像 ID、DDL、脱敏配置与各项证据的关联。验收期间代码或配置变动须保留前后版本、原因及影响范围；重新构建受影响产物并部署，重验受影响路径。最终候选执行部署健康、普通用户授权允许/拒绝、文件下载的组合冒烟；恢复证据须对应最终数据库/文件格式及应用语义，相关输入变化则重做恢复。

阻断按当前契约修复并有回归证据；范围扩展或已暂缓能力不得顺带实施。未解决的必要场景缺口阻止通过结论；范围外已核实问题按生命周期技能登记，不将已知接受边界重新包装成缺陷。设计变更先回写权威文件，不在验收材料内另立契约。

## 当前口径

收口执行仓库根 mvn test -T 1C，包含 E2E/heavy，不带 skipE2E、skipHeavyIT 或 skipTestcontainers。上游 API 变化先按工程基线 install 刷新；按仓库纪律核实宿主 9100 冲突并妥善处理，不能擅停其他环境。Maven 运行期间禁止改源码，完整输出落盘，失败先隔离复跑定性。

前端依次执行 pnpm -C frontend test:run、pnpm -C frontend typecheck、pnpm -C frontend lint:check、pnpm -C frontend contracts:check、pnpm -C frontend build。执行前按 [package.json](../../frontend/package.json) 和[工程基线](../ops/engineering-baseline.md)核实工具版本及命令。完整验证集中在收口；此前针对缺陷只做必要定向验证。不为纯计划文档编写运行全仓测试。

新建/修改测试前使用[测试技能](../../.agents/skills/access-mesh-testing/SKILL.md)和[测试规则](../../.claude/rules/testing-standards.md)，跨服务自动化只进入 e2e 模块，已有 E2E 只承担其接线证据，不替代正式包部署和浏览器实测。完整回归结束核对输入未变化，结果按实际报告统计，区分 passed/failed/skipped/not run，不累计重叠定向测试冒充唯一用例数。

按[本地双轨技能](../../.agents/skills/dual-track-local-review/SKILL.md)先代码轨后文档轨独立只读核对。摘要写入 docs/tasks/evidence/candidate-deployment/verification.md，并保留可追溯结果、哈希和脱敏操作步骤；原始大日志位于 .tmp/candidate-deployment-acceptance/，报告不得只引用临时日志而无可提交摘要。归档时证据随任务迁移。

最终结论写清实际版本、环境、验证者、通过/失败/未测项、有效限制及复现入口。架构、示例与接入指南回写已验证范围；README/CHANGELOG/quickstart/部署手册核对与当前事实一致。保持预览定位，不因验收完成自行改版本号或宣称生产认证；是否发布为独立后续动作。

## 验收对照

- [ ] 最终候选与全部证据可对账，变更后的受影响验收已补齐，必要场景无阻断。
- [ ] 后端完整回归、前端门禁与最终部署组合冒烟通过，输入一致性与测试统计可信。
- [ ] 本地两轨结论及设计/使用文档回写完成，候选结果与未验证边界准确。
- [ ] 任务、计划、索引及证据归档完整，资源保留位置与后续清理责任明确。

## 非目标 / 遗留

不自动 push、触发远端 CI、创建版本标签或发布 Release；不把本地门禁结果称为远端 CI 通过。不清理其他聊天资源，不解除原有暂缓项，不自动追加外部 AI 评审。
