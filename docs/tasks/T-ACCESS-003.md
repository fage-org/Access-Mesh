---
doc_type: task
id: T-ACCESS-003
title: 收敛单数据源、MyBatis、Redis、JSON等运行基础配置
status: proposed
plan: docs/plans/access-service-merge-plan.md
domain: cross-service
design_refs:
  - docs/design/access-service-architecture.md#2-目标工程与部署单元
  - docs/design/access-service-architecture.md#6-可信请求上下文与安全策略
  - docs/design/access-service-architecture.md#71-统一缓存框架
  - docs/design/project-rules.md
  - docs/design/services/gateway.md
depends_on:
  - T-ACCESS-001
  - T-ACCESS-002
blocks: []
acceptance:
  - "access-service 只装配 access_db 数据源、一个事务管理器和一套 MyBatis-Flex 租户配置"
  - "Redis 统一为 logical DB 0；业务缓存只暴露一个 CacheService；Sa-Token 使用与业务缓存隔离、但由 Gateway 与 access-service 共享的唯一会话键命名空间"
  - "盘点并消除 Gateway 与原 admin 的 Sa-Token 配置差异，统一 token-name、login-type、Token解析模式、密钥和会话键命名空间；平台用户会话固定 timeout=2小时、active-timeout=30分钟，两端关键配置缺失或不一致时启动失败"
  - "删除重复 TenantContextHolder、MybatisFlexTenantConfig、自定义 ObjectMapper 和重复 Redis 序列化配置"
  - "删除 admin Spring Cache/裸 Caffeine 与业务侧 RedisTemplate 直接操作，保留框架允许的基础设施 Bean"
  - "配置属性、profile 与测试配置均不再引用 admin_db、perm_db 或两套 Redis DB"
  - "应用上下文测试证明基础设施 Bean 唯一且序列化、租户解析可用"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-12
---

# T-ACCESS-003 收敛单数据源、MyBatis、Redis、JSON等运行基础配置

## 背景

物理归并后，重复基础设施配置会造成 Bean 冲突和不一致的租户、缓存及 JSON 行为。

## 范围

- 收敛数据源、事务、MyBatis、Redis、缓存和 Jackson 配置。
- 删除旧服务特有且与统一框架冲突的配置。
- 增加唯一性和启动验证。

## 完成记录

（待实施后填写。）
