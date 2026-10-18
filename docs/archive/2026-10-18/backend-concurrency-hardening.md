---
doc_type: plan
title: 后端并发缺口修复与公共 Redis 事务锁
status: archived
domain: cross-service
design_refs:
  - docs/design/redis-transaction-locks.md
tasks:
  - T-ACCESS-122
  - T-ACCESS-123
  - T-ACCESS-124
  - T-ACCESS-125
  - T-ACCESS-126
  - T-ACCESS-127
  - T-ACCESS-128
acceptance: "关联任务完成，定向反例与完整 reactor 回归通过，本地双轨无未处置项"
last_updated: 2026-10-18
---

## 目标

依据 T-ACCESS-121 已核实证据修复并发缺口，将现有 Redis 事务锁机械责任下沉 common。用户在本会话要求继续实施；详细契约见 design_refs，范围与验收见任务卡。

## 非目标

不恢复其他暂缓事项，不替换数据库约束/CAS/租约，不新增注解锁框架，不执行环境部署。

## 准入条件

基线及原报告已归档，当前使用测试环境；按现有业务语义修复，不把公共类迁移等同于缺陷闭合。

## 任务清单

| ID | 标题 | 状态 | 直接依赖 |
|---|---|---|---|
| [T-ACCESS-122](tasks/T-ACCESS-122.md) | 公共 Redis 事务锁能力提取 | ✅ | T-ACCESS-121 |
| [T-ACCESS-123](tasks/T-ACCESS-123.md) | 请求触达列与有效行更新 | ✅ | T-ACCESS-121 |
| [T-ACCESS-124](tasks/T-ACCESS-124.md) | 类型锁内新鲜读取 | ✅ | T-ACCESS-121 |
| [T-ACCESS-125](tasks/T-ACCESS-125.md) | 岗位成员锁内结构读取 | ✅ | T-ACCESS-121 |
| [T-ACCESS-126](tasks/T-ACCESS-126.md) | 主体生命周期与同步互斥 | ✅ | T-ACCESS-121 |
| [T-ACCESS-127](tasks/T-ACCESS-127.md) | 字典父子生命周期互斥 | ✅ | T-ACCESS-121 |
| [T-ACCESS-128](tasks/T-ACCESS-128.md) | Gateway 清理代际不回退 | ✅ | T-ACCESS-121 |

## 归档条件

所有关联任务终态；设计回写、问题收敛、测试证据和索引完整。完整回归不跳过 E2E/heavy，运行期间冻结源码。

## 当前进度

关联任务均已完成；定向反例、完整回归与代码/文档双轨通过，未追加外部评审。
