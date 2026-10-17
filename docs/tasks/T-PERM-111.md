---
doc_type: task
id: T-PERM-111
title: explain 与声明诊断修正批（空目标身份/码类型丢失/发布代次序列化）
status: done
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/dependency-auto-grant.md
  - docs/design/access-service-api-contract.md
depends_on: []
blocks: []
acceptance:
  - "有效空目标 explain 保留已解析 target 的完整事实身份（资源/类型/操作），actual 为空显示中性「未生成自动权限」而非红色「无来源存量」（非 desired 且 actual 非空才显示孤儿标记）"
  - "来源解释目标构造保留所选目录对象的 codeType（选项 key/value 携带完整业务键），同码不同 codeType 不再查错实体；事实标签按需展示码类型"
  - "DependencyDeclarationStatusResp.publicationGeneration 字符串序列化（ToStringSerializer 同款策略），前端线类型改 string 原样显示；大代次（>2^53）不再失真"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

P2×2 + P3×1 批（外部持续审查发现，探针实证双端，2026-10-14 主代理核实成立）：

- **空目标身份丢失**（P2）：角色对有效但不参与依赖图的目标发起独立来源解释，后端渲染 desired=false/actual=[] 且业务身份字段全 null；前端 ExplainViewerDrawer 显示「?/?」与红色「无来源存量」——审计员无法核实所查对象，且把正常「未生成」误读为 AUTO_DEP 脏数据。
- **codeType 丢失**（P2）：ExplainViewerDrawer 的 ResourceOption 不含 codeType，目标构造硬编码 `codeType:"default"`，下拉 option 以 id 为 key 却仅以 resource.code 为 value——目录同时存在 `REPORT/shared/default` 与 `REPORT/shared/BIZ` 时，选 BIZ 实际查询 default 的解释（探针实证缺目标行 106），仅 BIZ 时合法选项报不存在。
- **发布代次失真**（P3）：`DependencyDeclarationStatusResp.java:24` 裸 Long 无字符串序列化标注，前端声明为 number；>2^53 的合法代次经 JSON→JS Number 精度丢失，两个不同代次显示同值（Axios 解析阶段已丢，探针实证）。schema 该列为正 BIGINT、project-rules 对其他大数列已有十进制字符串响应要求。

## 范围

explain 渲染身份与前端标签；目标构造完整键；声明诊断字段序列化与前端线类型。

## 当前口径

保留完整 FactKey 契约（§12.3.1）与 target 保底定案；不删空目标、不新增对外 DTO 字段/诊断 API/图数据库。入口已解析的 FactKey 通过内部 ExplainTargetFact 传给渲染，避免为补显示重复查询。codeType 修复不让后端忽略该字段、不合并同码实体。主键 number 例外口径不扩大到该字段。

## 验收对照

- [x] 有效空目标保留入口解析的完整身份，actual 为空显示中性未生成状态。
- [x] 目标选择与请求携带完整资源业务键，非默认 codeType 标签可区分。
- [x] publicationGeneration 以十进制字符串输出，前端 string 原样展示。

## 非目标 / 遗留

与互斥审计关联缺失（T-PERM-112）分卡处理；Q-060/061 不重启。

## 完成记录

2026-10-17：`mvn test -T 1C` 全 reactor 通过，包含 E2E 与 heavy；本次生成的 Surefire XML 共 2860 个 testcase，失败/错误/跳过均为 0（按 XML 实际 testcase 计数，避免嵌套测试汇总行差异）。
前端 `pnpm test` 50 文件、562 项通过；`pnpm typecheck`、`pnpm lint:check`、`pnpm build` 与 `pnpm contracts:check`（44 组）通过。
空目标 PgIT 与大代次序列化用例均先验证旧行为失败；AutoGrantInsightPgIT 21 项通过，前端恢复旧空目标状态与 codeType=default 行为时 2 项测试失败。

代码轨核对目标身份传递、codeType 完整键、空目标/孤立存量区分及大整数字符串线格式；文档轨核对 FactKey、预览/推导边界与声明诊断契约。两轨无未解决 P0–P2 或待决策项；未引入无调用方机制。
