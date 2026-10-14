---
doc_type: task
id: T-ACCESS-102
title: example-service 子报表真实父关系核对
status: proposed
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
  - "extension-guide.md §8.6 最终检查接线示例同步修正"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-14
---

## 背景

P2（外部持续审查发现，探针实证 13 个生产类局部执行，2026-10-14 主代理核实成立）：`example-service/.../ReportController.java:174-183` 的 /sub-view 直接用 `req.parentReportCode` 构造 Target.childOf 做最终权限检查并回显，未核对读取到的子对象真实父（`DemoReport.parentCode()` 已提供派生方法但未被使用）。管理员建立「主授权 report-2:VIEW + 子授权 report-1-detail:SUB_VIEW 绑定该主行」的合法授权组合后，普通用户提交 `parentReportCode=report-2` 即可取回实际属于 report-1 的明细内容——违背契约 §25.7「上下文子权限提供真实父、错父拒绝」，并向接入开发者示范错误接线。引擎检查「父命中授权与子行 dependOn 相符」本身正确，不承担业务对象所属关系。

## 范围

example-service /sub-view 控制器与最终检查接线；错父测试补全；接入指南示例。

## 当前口径

可信租户范围内读取实际子对象、复用现有父派生核对；不新增父关系数据库、通用资源图、签名上下文，不限制通用 grant-plan 只能绑定物理树父（通用跨类型子权限能力保留）。

## 验收对照

见 frontmatter `acceptance`；探针（ObjectContextProbe，真实 Controller→BusinessPermChecker→引擎）可转正，错父合法组合用例须旧实现下失败。

## 非目标 / 遗留

权限引擎判定逻辑不动；DECISION-001（导出领取边界，见待解决问题清单 Q-073）不在本卡范围。
