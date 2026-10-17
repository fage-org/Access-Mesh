---
doc_type: task
id: T-ACCESS-102
title: example-service 子报表真实父关系核对
status: done
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/extension-guide.md
depends_on: []
blocks: []
acceptance:
  - "/sub-view 读取子对象后核对业务真实父（复用 DemoReport.parentCode 派生或等价核对），请求父与真实父不符按既有拒绝语义处理；通过后返回同一对象并回显真实父"
  - "最终权限检查消费真实父上下文；错父用例覆盖「授权绑定 report-2、业务明细属 report-1」的合法授权组合（探针已实证当前实现会返回实际明细内容）"
  - "extension-guide.md §2.4 最终检查接线示例同步修正"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

P2（外部持续审查发现，探针实证 13 个生产类局部执行，2026-10-14 主代理核实成立）：`example-service/.../ReportController.java:174-183` 的 /sub-view 直接用 `req.parentReportCode` 构造 Target.childOf 做最终权限检查并回显，未核对读取到的子对象真实父（`DemoReport.parentCode()` 已提供派生方法但未被使用）。管理员建立「主授权 report-2:VIEW + 子授权 report-1-detail:SUB_VIEW 绑定该主行」的合法授权组合后，普通用户提交 `parentReportCode=report-2` 即可取回实际属于 report-1 的明细内容——违背契约 §25.7「上下文子权限提供真实父、错父拒绝」，并向接入开发者示范错误接线。引擎检查「父命中授权与子行 dependOn 相符」本身正确，不承担业务对象所属关系。

## 范围

example-service /sub-view 控制器与最终检查接线；错父测试补全；接入指南示例。

## 当前口径

可信租户范围内读取实际子对象、复用现有父派生核对；不新增父关系数据库、通用资源图、签名上下文，不限制通用 grant-plan 只能绑定物理树父（通用跨类型子权限能力保留）。

## 验收对照

- [x] 读取可信租户内的实际子对象后核对真实父，不匹配按既有 30004 拒绝。
- [x] 最终检查与成功响应消费同一子对象和真实父。
- [x] E2E 先证明错业务父的授权组合在引擎中允许，再证明业务端拒绝；接入指南 §2.4 已同步。

## 非目标 / 遗留

权限引擎判定逻辑不动；DECISION-001（导出领取边界，见待解决问题清单 Q-073）不在本卡范围。

## 完成记录

2026-10-17：`mvn test -T 1C` 全 reactor 通过，包含 E2E 与 heavy；本次生成的 Surefire XML 共 2860 个 testcase，失败/错误/跳过均为 0（按 XML 实际 testcase 计数，避免嵌套测试汇总行差异）。
错业务父且权限检查返回允许的单测在旧行为下失败；ReportControllerTest 20 项与 ExampleBusinessFinalCheckE2EIT 12 项通过。

代码轨核对可信租户取数、业务父核对顺序、同一对象检查及返回、引擎允许而业务拒绝的 E2E 反例；文档轨核对最终检查职责、指南 §2.4 与既有跨类型子授权边界。两轨无未解决 P0–P2 或待决策项；未引入无调用方机制。
