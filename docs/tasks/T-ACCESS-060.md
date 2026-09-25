---
doc_type: task
id: T-ACCESS-060
title: （ADM-T05）失效、TTL 边界与在途代次
status: proposed
plan: docs/plans/r2-query-engine-and-admission-plan.md
domain: access-service
design_refs:
  - docs/design/r2-unified-query-and-admission.md §5.3/§8.5
depends_on:
  - T-ACCESS-058
  - T-ACCESS-059
blocks: []
acceptance:
  - "§8.5 失效矩阵全链落地：授撤/角色归属与有效期/AUTO_DEP 改变、条件同 ID 改规则与启停、操作覆盖/定义变更、父授权撤销或结构变化、映射新增/改绑/停用/删除/FULL 缺失、服务模式/配置代次/协议切换——删除一个来源不清掉其他有效来源"
  - "条件关联服务反查按「引用条件的授权类型→该类型所需操作→映射服务」安全超集再按实际覆盖优化（不沿旧授权资源=API 资源联接）——markConditions 通道扩展反查并广播 serviceCodes（PermissionChangeAspect/PermissionChangeContext 改动面）"
  - "新快照 TTL、上游安全目录寿命与截止时间按 §5.3 重新推导并纳入启动校验（不宣称旧 30 秒边界自动覆盖新依赖）；回填保留读前令牌/剩余 TTL；N19/N21/N23 全绿（旧在途读取不覆盖新代次、保留加载去重/回源截止/失败关闭）"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-25
---

# T-ACCESS-060 （ADM-T05）失效、TTL 边界与在途代次

## 背景

设计 §8.5/§5.3（报告临时编号 ADM-T05）。新准入把业务操作覆盖转换为快照候选，依赖关系已变——不能沿用旧快照约 30 秒安全结论；即便采用新鲜操作定义，广播+TTL 仍非零延迟强一致（设计明示）。

## 范围

- 失效触发逐项反向测试（每个触发有负向用例）；网关 L1/回源截止与新目录边界同批推导；负缓存与节点能力处理。

## 非目标 / 遗留

- 专用短 TTL/版本化业务操作缓存为备选（须重做安全边界证明，设计 §5.3 非默认）——本卡默认新鲜数据库读取。
