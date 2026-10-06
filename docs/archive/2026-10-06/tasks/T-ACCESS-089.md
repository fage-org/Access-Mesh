---
doc_type: task
id: T-ACCESS-089
title: 维护者工程基线（CI 时长/SNAPSHOT 防呆/工作区卫生）
status: done
plan: docs/archive/2026-10-06/usage-review-remediation-plan.md
domain: cross-service
design_refs:
  - docs/design/project-rules.md（构建纪律/分层约束）
  - README.md（构建说明）
  - docs/ops/engineering-baseline.md
depends_on: []
blocks: []
acceptance:
  - "CI 记录测试时长+基线随收口更新进 docs（自动对比延后至首例劣化）"
  - "SNAPSHOT 局部构建陷阱最小机制落地（mvn enforcer / 常用命令包装脚本〔局部编译自动 -am〕/ README 指引强化，三选一定稿）：改上游 API 后按文档/脚本局部编译不再拿旧 SNAPSHOT（路径覆盖用例）"
  - "分层守护评估（ArchUnit 已在 access-service pom:192，评估启用范围，结论至少登记）"
  - "工作区清扫规约落地；历史工作树、根日志与根缓存的实际删除已由用户于 2026-10-06 移出本次验收"
  - "CI push 收口形态与超大文件拆分：评估结论登记（路线图）"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-06
---

# T-ACCESS-089 维护者工程基线

## 背景

无随 HEAD 更新的时长基线与 CI 时长记录（归档有 522s/244s/173s 历史实测，缺当前基线）；SNAPSHOT 局部构建陷阱靠文档纪律（mvn compile 不 install 上游，下游拿旧 SNAPSHOT 假失败）无机制缓解；核心分层禁跳层无机械守护；根目录 47 个 .codex-*.log（最大 2.3MB）+3.3GB 历史 worktrees；CI push 不跑容器轨/E2E；超大实现文件集中四能力包。

## 范围

CI 时长记录+基线、SNAPSHOT 防呆机制三选一、分层守护评估、清扫规约、两项评估登记。

## 当前口径

自动对比/劣化告警延后（首例劣化再建）；SNAPSHOT 采用最小包装脚本 `tools/build.ps1`，固定目标与上游一起 install；CI 耗时写 job summary，不设自动比较告警。

## 验收对照

- [x] CI 时长可见+基线在 docs
- [x] SNAPSHOT 机制落地+路径覆盖用例
- [x] 分层守护评估登记
- [x] 清扫规约在册（实际删除不在本次验收）
- [x] push 形态/超大文件评估登记

## 非目标 / 遗留

- 超大文件实际拆分：Q-065 路线图登记。
- 2026-10-06 用户明确移出历史工作树、根日志、根缓存的实际删除。自动审批拒绝了已获授权的删除，返回 `blocked by policy`，未提供原因；未执行删除，已有备份保留。


## 完成记录

2026-10-06：实现与设计回写完成。`mvn test -T 1C` 2658 项，0 失败/错误/跳过，包含 E2E 与 heavy；前端 508 项、lint/typecheck/build 与 35 组 DTO 对账通过。任务对应行为证据、失败处置和本地双轨复审见 [最终验收](evidence/usage-review-20261006/final-verification.md)。
