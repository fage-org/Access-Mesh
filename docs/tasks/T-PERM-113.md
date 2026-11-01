---
doc_type: task
id: T-PERM-113
title: 操作定义读取统一与掩码缓存退役
status: proposed
plan: docs/plans/architecture-boundary-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-architecture.md#operation-catalog-consistency
  - docs/design/engine/implementation.md
depends_on: []
acceptance:
  - "请求在修改提交后新建时按新继承掩码判定；旧读取不再污染后续请求。"
  - "明确退役目录的调用、配置、启动校验与测试去向，不删除其他目录保护。"
  - "保留批量装载，无循环单条 SQL；冷/热形态不增加重复目录读取。"
  - "复用 QueryReadSupportTest、QueryReadSupportPgIT、OperationPermissionCacheEvictionPgIT 的独有证据；用确定性闸门覆盖交错，按新语义替换旧缓存约束并回写规范。"
design_writeback:
  required: true
  status: pending
last_updated: 2026-11-01
---

## 背景

来源：原报告（外评快照按定规不入库） §一.1、§三.9/12、§七；覆盖定位见[范围映射](evidence/architecture-boundary-remediation/scope-map.md)。基线为 `44e5b03e2`，原报告的推测与测试声明不自动作为本任务已完成证据。

P1：QueryReadSupport.maskOperations 查库后无条件回填，可能越过 OperationAppServiceImpl 的提交后失效。VIEW.bit=2、UPDATE.bit=4、旧 inheritMask=2 改为 0 时，迟到回填可恢复旧覆盖关系。

## 范围

普通判定复用 resolveOperations 已装载的请求内 freshOperations；核对所有缓存读写入口，退役 OPERATION_PERMISSIONS_BY_TYPE、仅服务该目录的失效与配置/序列化约束。按实际依赖清理旧测试，保留顺序修改、并发交错、准入与最终判定一致性的有效证据。

## 当前口径

方案已确认，目标以服务架构对应锚点为准；当前代码尚未交付。请求内记忆维持首次读取语义，不承诺在途请求随提交线性化。公共缓存框架、其他目录及授权快照剩余 TTL 协议保持。缓存技能的 .agents/.claude 双副本在实现时同批更新。

权威回写位置：[docs/design/access-service-architecture.md#operation-catalog-consistency](../../docs/design/access-service-architecture.md#operation-catalog-consistency)、[docs/design/engine/implementation.md](../../docs/design/engine/implementation.md)。本卡处于 proposed，仅编排后续工作；本次建卡不执行生产代码或测试改动。核查卡只授权形成证据/方案，具体实现必须由用户确认后另行登记实现卡。现有明确取舍按原范围消费，不重复要求用户决定。

## 验收对照

- [ ] 请求在修改提交后新建时按新继承掩码判定；旧读取不再污染后续请求。
- [ ] 明确退役目录的调用、配置、启动校验与测试去向，不删除其他目录保护。
- [ ] 保留批量装载，无循环单条 SQL；冷/热形态不增加重复目录读取。
- [ ] 复用 QueryReadSupportTest、QueryReadSupportPgIT、OperationPermissionCacheEvictionPgIT 的独有证据；用确定性闸门覆盖交错，按新语义替换旧缓存约束并回写规范。

## 非目标 / 遗留

不自动启动本计划外暂缓事项。新发现缺陷或范围变化须先核实；有现有任务/问题载体则回链，无载体且暂不可执行才按治理登记。需要增加实现卡时从看板计数器取未占用编号，同步计划与 T-ACCESS-159 的依赖；不以本卡核查结论自动授权实施。

