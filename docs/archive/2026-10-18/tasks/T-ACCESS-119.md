---
doc_type: task
id: T-ACCESS-119
title: 测试环境待解决问题校准与最小修复
status: done
plan: null
domain: cross-service
design_refs:
  - docs/design/frontend/login.md
  - docs/design/access-service-api-contract.md
  - docs/design/project-rules.md
  - docs/design/access-service-architecture.md
depends_on: []
acceptance:
  - 校准问题清单中的现役入口、已交付能力与剩余范围
  - 裁剪 Q-070 未使用的记住我状态，保留实际会话标记
  - 最小修复 Q-072 的 iframe 刷新查询参数位置
  - 定向验证 Q-079、Q-056、Q-081，确认的问题按有效边界处置
  - 完成定向验证、收口回归与本地代码轨和文档轨评审
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

测试环境的待解决问题存在已交付子项、缺陷前提不准确及未使用模板残留。本任务按用户指定范围核实与最小修复，来源为 [待解决问题](../../../pending-problems.md)。

## 范围

Q-070、Q-072 的最小改动，Q-079、Q-056、Q-081 的定向验证及确认问题处置，以及相关清单描述校准。

## 当前口径

不启用 iframe 动态路由或记住我功能，不扩展为通用前端框架。Q-056 必须区分工具理论碰撞与正式接口可达性；Q-079 必须区分异常消息携带输入与真实凭据泄露链；Q-081 必须区分事务写入串行化和响应之后的密码交付。

Q-081 采用 Redis 非等待锁，拒绝处理期间的重叠重置，接受提交后的交付窗口。租户内重置与平台首管理员恢复共用目标用户键，平台账号重置独立命名空间；实现及故障边界以设计引用为准。

## 验收对照

- [x] 清单校准及设计回写。
- [x] Q-070 裁剪与 Q-072 最小修复。
- [x] 三项定向核实和确认问题处置。
- [x] 测试与本地双轨评审。

## 非目标 / 遗留

T-PERM-036、权限诊断、完整契约补册、强制下线、验证码替代通道及大型服务拆分继续暂缓。无生产部署、数据库重建或迁移。

## 完成记录

2026-10-18：`mvn test -T 1C` 3000 项、0 失败/错误/跳过，含 E2E/heavy；`pnpm -C frontend test` 639 项通过，typecheck/lint:check/contracts:check/build 均通过。本地代码轨与文档轨无未处置问题；定向反例、首次运行失败处置及源码哈希核对见[验收记录](evidence/T-ACCESS-119/verification.md)。Q-056/Q-070/Q-072/Q-079/Q-081 按实际结果与已确认边界收敛，其余事项继续暂缓。
