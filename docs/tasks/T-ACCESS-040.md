---
doc_type: task
id: T-ACCESS-040
title: API 契约深合一与设计文档重组
status: proposed
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§7
  - docs/design/permission-center/api-contract.md（合并源）
  - docs/design/services/admin-service-api-contract.md（合并源）
depends_on:
  - T-ACCESS-033
blocks: []
acceptance:
  - "两份 API 契约并为一份 access-service 契约总册：按能力分章、两个 URL 家族同册分列；旧契约文件转 superseded（superseded_by 指向总册，保留原位可解析）"
  - "docs/design/permission-center/ 目录解散：overview / core-flows / implementation 等设计内档归引擎子系统文档位"
  - "「活引用」按可执行规则判定并通过：权威入口全部改挂新册/新路径——AGENTS.md 权威表、根 README.md、docs/README、design/README、plans/README、.claude/skills 与 .claude/rules 指针（含 .agents 双副本，隐藏目录必须扫到）、仍 adopted 的设计文档（含 docs/design/frontend/*、docs/design/services/gateway.md、example-service.md、extension-guide.md）、docs/ops/runbook-full-sync.md、代码注释；豁免面（允许清单，落卡备查）= docs/archive/**、superseded 文件本体、registry 历史行（协议不改）、已终态任务卡与历史档案的 design_refs（保留原样）、.claude/worktrees 本地残留；验收命令 = `rg --hidden -e 'permission-center/|admin-service-api-contract' -e '\b(overview|core-flows|implementation|query-engine-unification)\.md\b' --glob '!docs/archive/**' --glob '!.git/**' --glob '!.claude/worktrees/**' --glob '!**/target/**'` 每个命中路径满足豁免谓词之一（archive / superseded 本体 / registry 历史行 / 已终态卡 design_refs / worktrees）——豁免面在实施时落成显式路径清单随卡存档，验收 = 命中集合 ⊆ 清单（rg 默认跳过隐藏目录必须 --hidden；第二个正则捕**裸文件名**引用——目录解散后 overview/core-flows/implementation 等同名文件在新位不唯一，裸名命中按「已重挂/已归档」二分核对；2026-09-13 实测非归档命中 98 文件，含 4 个技能双副本文件；本卡自身 design_refs 指向合并源属任务固有，随批改挂新册后移出命中集）"
  - "034 / 036 / 037 / 041 任务卡 design_refs 重挂新册（防后续回写旧册造成权威分裂）"
  - "docs/README 与 design/README 索引刷新；归档/迁移自检清单通过"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-13
---

## 背景

文档叙事层定案（capability-structure §7）：契约深合一 + 目录去历史化重组；任务 ID 前缀不动。非归档引用面实测 98 文件（2026-09-13 `rg --hidden` 核，排除归档/.git/worktrees/target——默认扫描漏隐藏目录只见 94，技能双副本 4 文件必须扫到），远超旧版卡面枚举——验收以允许清单二分（权威入口 vs 档案）判定，与「superseded 保留原位」的口径统一：活引用 = 非允许清单命中。

## 范围

1. 契约总册编写（内容迁移为主，两份契约现状语义不变——034/036/037 的语义变更随后落新册）。
2. permission-center/ 目录重组、admin-service-api-contract.md 退役、设计内档归位。
3. 全仓引用更新（按 acceptance 允许清单口径）与索引刷新。
4. 下游任务卡（034/036/037/041）design_refs 重挂新册。

## 当前口径

- 本任务先于 034/036/037 执行（计划顺序），其契约回写全部落新册，避免旧册双写。
- superseded 保留原位不物理删除（交叉链接可解析），物理归档随后续批次。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不改契约语义内容（只重组；语义变更归 034/036/037）。
- 规则/技能文件的口径重写归 T-ACCESS-041（本任务只改其中的路径引用）。
