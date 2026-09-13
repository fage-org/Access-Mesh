---
doc_type: task
id: T-ACCESS-038
title: 错误码合类不合号
status: proposed
plan: docs/plans/access-capability-fusion-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-capability-structure.md#§3
  - docs/design/access-service-architecture.md#§9
depends_on:
  - T-ACCESS-033
blocks: []
acceptance:
  - "AdminErrorCode / PermissionErrorCode 合一为单一错误码面（单类或单包分文件）；1xxxx/2xxxx 编号段原值保留、零重排"
  - "同名异号碰撞裁决表落卡：USER_NOT_FOUND(10001/20015)、USER_ALREADY_EXISTS(10002/20016)、INVALID_PARAM(10008/20044) 三组双侧均有生产调用方——统一符号面下以能力前缀符号名或分组常量消解编译冲突，三组编号对与各自调用方语义原样保留；映射断言载体为 ErrorCodeContractTest 改写（单映射键 × 码值分段：segments_ownedBySingleEnum 判据由「归属枚举」改「归属编号段」、noDuplicateCodesAcrossEnums 等价断言在合并后单枚举内自证码值唯一、90 项基线逐条保持）——不新建断言文件替代既有锁"
  - "全仓 import 与抛出点收敛；错误码引用测试/断言更新"
  - "access-service-architecture §9 表述更新：枚举面合一、分段与不重编号字面维持、新增码归属段规则写明"
  - "全量回归绿"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-13
---

## 背景

两枚举类是平行设施，但 §9 定案字面只约束编号段（两段分立、不得重编号、不得新增 4xxxx）——合类不合号不违反其字面，属设施收敛而非编号变更。前端按数字码分支面极窄（仅授权页 GRANT_ERROR_CODE 映射表，码值不变零影响）。

## 范围

枚举面合一 + 全仓引用收敛 + §9 文档表述更新（含新增码归属段规则）。

## 当前口径

- 合一后单一错误码面承载两段编号；「能力无专属段」——新增码沿用现有两段归属规则在 §9 更新时写明。
- 9xxxx 公共技术失败段维持。

## 验收对照

见 frontmatter acceptance。

## 非目标 / 遗留

- 不重排、不合并编号段，不新增 4xxxx 段。
