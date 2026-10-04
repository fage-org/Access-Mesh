---
doc_type: task
id: T-ADMIN-032
title: job cron 写前校验与调度失败可观测
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §17.3（job 族未成册注记）
  - docs/design/access-service-architecture.md §8（任务、异步与审计）
depends_on: []
blocks: []
acceptance:
  - "createJob/updateJob 在写入前解析校验 cron（Spring CronExpression 同源解析），非法 cron 拒绝保存（错误码定案入册；@NotBlank 仅拒空白的缺口闭合）"
  - "updateJob 先取消旧调度前校验：新 cron 非法时不撤销当前实例旧调度（其他实例旧 cron 执行窗口不被坏输入破坏）"
  - "scheduleJob 注册失败不再只记日志：可观测结果定案（如任务落 DISABLED_ALARM 类状态或告警事件，按 decision-question-protocol 举例上报用户后拍板）并实现；对账可见注册态与库态不一致"
  - "回归锁：非法 cron 保存旧实现成功/新实现拒绝；启用任务注册失败路径可观测（旧实现只记日志实证）"
  - "契约 §17.3 job 族注记与 architecture §8 口径同步"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ADMIN-032 job cron 写前校验与调度失败可观测

## 背景

承接 [Q-049](../../../pending-problems.md#q-049)：`JobAppServiceImpl.createJob/updateJob` 未在写入前解析 cron；scheduleJob 构造 trigger 失败只记日志。Spring 6.1.5 CronTrigger 构造时立即解析，JobCreateReq 的 @NotBlank 仅拒空白不验语法。后果：运维创建启用任务传非空非法 cron，API 成功、库中启用、但未注册调度，对账也无法修正；更新会先取消当前实例旧调度，其他实例可能仍按旧 cron 执行。默认种子只开放 VIEW/TRIGGER/ENABLE，无任务管理 UI，入口为运维 API。

## 范围

写前 cron 校验（create/update）；update 撤旧调度前校验；注册失败可观测定案+实现；契约 §17.3 与 §8 口径。

## 当前口径

校验器与运行时同源（Spring CronExpression），非法表达式在写入及撤销旧调度前拒绝，错误码为 10602。注册失败保留数据库启用配置，详情与分页展示本实例的注册状态，继续由现有 60 秒对账重试；不新增数据库状态或告警框架。该边界于 2026-10-04 确认，权威见契约 §17.3 与架构 §8.1。

## 非目标 / 遗留

- job 管理 UI（无此规划）。

## 验收对照

- [x] createJob/updateJob 在写入前解析校验 cron（Spring CronExpression 同源解析），非法 cron 拒绝保存（错误码定案入册；@NotBlank 仅拒空白的缺口闭合）
- [x] updateJob 先取消旧调度前校验：新 cron 非法时不撤销当前实例旧调度（其他实例旧 cron 执行窗口不被坏输入破坏）
- [x] scheduleJob 注册失败不再只记日志：可观测结果定案（如任务落 DISABLED_ALARM 类状态或告警事件，按 decision-question-protocol 举例上报用户后拍板）并实现；对账可见注册态与库态不一致
- [x] 回归锁：非法 cron 保存旧实现成功/新实现拒绝；启用任务注册失败路径可观测（旧实现只记日志实证）
- [x] 契约 §17.3 job 族注记与 architecture §8 口径同步

## 完成记录

2026-10-04：定向验证与本地代码/文档双轨核对完成；完整 `mvn test -T 1C` 共 2462 项，零失败/错误/跳过，包含 E2E 与 heavy。[定向证据](evidence/T-ADMIN-032/verification.md)，[完整回归](evidence/pending-problems-clearance/batch-2026-10-04.md)。
