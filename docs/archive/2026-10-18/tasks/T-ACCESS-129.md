---
doc_type: task
id: T-ACCESS-129
title: 锁内类型身份写入核实与修复
status: done
plan: null
domain: access-service
design_refs:
  - docs/design/redis-transaction-locks.md
  - docs/design/access-service-api-contract.md#type-lifecycle
depends_on:
  - T-ACCESS-124
acceptance:
  - 保存指定 gpt-6.1-sol 外评及公共锁事务、联合锁序和写入口核查证据
  - 定向验证类型缓存迟到回填能否使同步写入已删除类型，确认则修复同类写入口
  - 按实际变更完成定向验证、完整回归及本地双轨评审
  - 校正主体类型删除的现状说明，保留有效接受边界和暂缓事项
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

用户要求指定模型评审公共锁事务边界、跨 Redis/数据库锁序及写入口覆盖，并修复确认的问题。外评未确认送评增量新增或放大的缺陷，指出资源和角色同步仍通过类型缓存确定落库身份的存量风险。

## 范围

基线 3e7ec01e3。核实迟到缓存回填与同码类型重建的交错，沿同类持久化写入排查；公共锁与既有读取缓存按职责保留。

## 当前口径

按[公共锁读取边界](../../../design/redis-transaction-locks.md)核对：取得互斥不能使缓存类型身份自动新鲜，决定持久化身份的读取须来自当前数据库。不得以短缓存 TTL 证明已落库数据会自动恢复。

## 验收对照

- [x] 完成指定模型参数的只读外评。
- [x] 类型身份风险定向核实与确认问题处置。
- [x] 同类扫描、必要回归与本地双轨。
- [x] 设计回写及证据归档。

## 非目标 / 遗留

不改变 Q-081 提交后交付窗口、watchdog 故障边界、树锁等待行为或主体类型删除已接受的 best-effort 语义。Q-060/Q-062/Q-065/Q-068 与 T-PERM-036 继续暂缓，不部署、不推送。

## 完成记录

外评、存量观察核实、同类扫描及反例证据见[核实记录](evidence/T-ACCESS-129/verification.md)。2026-10-18：定向106项单测及14项容器测试通过；完整 `mvn test -T 1C` 3025项全部通过，包含E2E/heavy。本地双轨与输入一致性检查完成。
