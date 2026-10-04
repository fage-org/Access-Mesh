---
doc_type: task
id: T-ACCESS-075
title: E2E 公共支持代码精简
status: done
plan: docs/archive/2026-10-04/testing-simplification-plan.md
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md#candidates
depends_on: 
  - T-ACCESS-069
blocks: []
acceptance:
  - "helper 仅覆盖真实重复调用方，业务步骤和失败定位仍可读"
  - "各 E2E 链路实际执行通过，异常退出与清理路径有核验，未残留进程/资源"
  - "生产与依赖拓扑不变，无服务模块跨服务测试依赖，无静态共享业务状态"
  - "设计 §4 回写抽取边界，记录包含新增 helper 在内的净变化"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ACCESS-075 E2E 公共支持代码精简

## 背景

承接[测试精简方案](../../../design/testing-simplification.md#candidates)与[执行计划](../testing-simplification-plan.md)。已抽取纯进程支持，独立生命周期与就绪探针保持，实跑及最终全量验证完成。

## 范围

1. 核对 e2e 模块现有支持设施，在 BasicRoleGrantVerticalSliceE2EIT、ExampleProtectedApiE2EIT、ExampleBusinessFinalCheckE2EIT 中只抽实际重复的启动、请求、等待与清理能力。
2. 保持各链路数据、进程、端口、身份来源、停用/重启/撤权场景独立，不隐藏断言或以共享可变状态换取少代码。
3. 以 -pl e2e -am 随 reactor 做定向验证；收口随完整 reactor 执行，报告实际发现与执行，不将失败环境探测当通过。

## 当前口径

按[设计的证据取舍](../../../design/testing-simplification.md#evidence-policy)与[验证边界](../../../design/testing-simplification.md#validation)执行，现役硬规则见[测试规范](../../../../.claude/rules/testing-standards.md)。每批改动只维护本批映射；开始前核对 HEAD 增量，不把附件估算或历史通过记录当本次验收。

## 验收对照

- [x] helper 仅覆盖真实重复调用方，业务步骤和失败定位仍可读
- [x] 各 E2E 链路实际执行通过，异常退出与清理路径有核验，未残留进程/资源
- [x] 生产与依赖拓扑不变，无服务模块跨服务测试依赖，无静态共享业务状态
- [x] 设计 §4 回写抽取边界，记录包含新增 helper 在内的净变化

## 非目标 / 遗留

不合并 E2E 场景，不改 JUnit 并行与 CI，不重复建设容器基建。


## 完成记录

2026-10-04：三条 E2E 与清理支持定向及完整并行回归均通过，29 testcase 零失败/错误/跳过；映射、净变化与进程清理见[验收证据](evidence/T-ACCESS-075/e2e-evidence.md)。
