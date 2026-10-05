---
doc_type: task
id: T-ACCESS-089
title: 维护者工程基线（CI 时长/SNAPSHOT 防呆/工作区卫生）
status: proposed
plan: docs/plans/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/project-rules.md（构建纪律/分层约束）
  - README.md（构建说明）
depends_on: []
blocks: []
acceptance:
  - "CI 记录测试时长+基线随收口更新进 docs（自动对比延后至首例劣化）"
  - "SNAPSHOT 局部构建陷阱最小机制落地（mvn enforcer / 常用命令包装脚本〔局部编译自动 -am〕/ README 指引强化，三选一定稿）：改上游 API 后按文档/脚本局部编译不再拿旧 SNAPSHOT（路径覆盖用例）"
  - "分层守护评估（ArchUnit 已在 access-service pom:192，评估启用范围，结论至少登记）"
  - "工作区清扫规约落地：根目录 47 个 .codex-*.log+空 node_modules+3.3GB worktrees 清扫，磁盘回收"
  - "CI push 收口形态与超大文件拆分：评估结论登记（路线图）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-05
---

# T-ACCESS-089 维护者工程基线

## 背景

无随 HEAD 更新的时长基线与 CI 时长记录（归档有 522s/244s/173s 历史实测，缺当前基线）；SNAPSHOT 局部构建陷阱靠文档纪律（mvn compile 不 install 上游，下游拿旧 SNAPSHOT 假失败）无机制缓解；核心分层禁跳层无机械守护；根目录 47 个 .codex-*.log（最大 2.3MB）+3.3GB 历史 worktrees；CI push 不跑容器轨/E2E；超大实现文件集中四能力包。

## 范围

CI 时长记录+基线、SNAPSHOT 防呆机制三选一、分层守护评估、清扫规约、两项评估登记。

## 当前口径

自动对比/劣化告警延后（首例劣化再建）；SNAPSHOT 机制取三选一实现时定稿（优先评估包装脚本——对日常命令形态侵入最小）。

## 验收对照

- [ ] CI 时长可见+基线在 docs
- [ ] SNAPSHOT 机制落地+路径覆盖用例
- [ ] 分层守护评估登记
- [ ] 清扫完成磁盘回收
- [ ] push 形态/超大文件评估登记

## 非目标 / 遗留

- 超大文件实际拆分：路线图登记。
