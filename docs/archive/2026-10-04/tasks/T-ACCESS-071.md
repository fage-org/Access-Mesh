---
doc_type: task
id: T-ACCESS-071
title: Java 测试同结构表达与夹具精简
status: done
plan: docs/archive/2026-10-04/testing-simplification-plan.md
domain: cross-service
design_refs:
  - docs/design/testing-simplification.md#candidates
depends_on: 
  - T-ACCESS-069
blocks: []
acceptance:
  - "逐批映射列出原 case 到保留 case/assert，参数标签可定位且没有万能开关模板"
  - "相关单测实跑通过，实际执行案例可追溯，结构减少不冒充运行减少"
  - "高风险删除有针对性回归信号证据，真实 PG 事务与 mock 阻断未相互替代"
  - "设计 §4 回写保留/参数化/删除结论与证据位置"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-03
---

# T-ACCESS-071 Java 测试同结构表达与夹具精简

## 背景

承接[测试精简方案](../../../design/testing-simplification.md#candidates)与[执行计划](../testing-simplification-plan.md)。已完成同结构参数化与无状态测试支持复用；原行为案例保持，证据见完成记录。

## 范围

1. 分能力小批处理 OAuth2ClientTtlTest、OAuth2AuthCodeClientBindingTest、OAuth2CodeExchangeNegativeTest、退役路由/请求契约、QueryStagesTest 与 common 的 TTL/响应模型候选。
2. 逐批确认实际重复后参数化或抽无状态局部工厂；没有维护收益的候选保留并记录理由，不为减少文件合并能力边界。
3. 保留 PKCE/客户端/租户/audience/烧码重放/审计、真实路由缺失、wire shape、拒绝原因与禁止下游调用、TTL 上限/耗尽/起点等独有信号；读取对应权限/缓存技能及测试规则。

4. 将 Gateway exchange/response/route 与 PG 请求信封/HMAC 的重复安排纳入候选；优先小范围无状态支持函数，签名头与 Bearer 身份路径显式区分，不抽大型基类。大而快的授权/查询单测不以提速名义优先压缩。

## 当前口径

按[设计的证据取舍](../../../design/testing-simplification.md#evidence-policy)与[验证边界](../../../design/testing-simplification.md#validation)执行，现役硬规则见[测试规范](../../../../.claude/rules/testing-standards.md)。每批改动只维护本批映射；开始前核对 HEAD 增量，不把附件估算或历史通过记录当本次验收。

## 验收对照

- [x] 逐批映射列出原 case 到保留 case/assert，参数标签可定位且没有万能开关模板
- [x] 相关单测实跑通过，实际执行案例可追溯，结构减少不冒充运行减少
- [x] 高风险删除有针对性回归信号证据，真实 PG 事务与 mock 阻断未相互替代
- [x] 设计 §4 回写保留/参数化/删除结论与证据位置

## 非目标 / 遗留

不改生产抽象，不修改 ItInfra/并发/CI；与在办业务修复发生重叠时重新核对增量。


## 完成记录

2026-10-03：OAuth2/退役路由参数化、Gateway exchange 与 HMAC 支持复用完成；夹具批次 168 案例、契约批次 30 案例均零失败/错误/跳过。映射、保留理由及命令见[证据](evidence/T-ACCESS-071/java-fixture-evidence.md)。完整计划回归由 T-ACCESS-076 承担。
