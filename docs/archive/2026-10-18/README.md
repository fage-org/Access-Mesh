# 2026-10-18 归档

[候选版本独立部署与首次接入验收](candidate-deployment-acceptance-plan.md)及 T-ACCESS-131～135 已完成，任务与[最终验收证据](tasks/evidence/candidate-deployment/verification.md)同批归档。完整回归、前端门禁与两套栈组合冒烟通过；仅覆盖实际本地预览和同版本恢复。当前规范见[架构](../../design/architecture.md)、[接入指南](../../design/extension-guide.md)与[部署手册](../../ops/deployment.md)。原始位置为 docs/plans/candidate-deployment-acceptance-plan.md、docs/tasks/T-ACCESS-131～135.md 及 docs/tasks/evidence/candidate-deployment/，本目录保留历史证据，源/恢复环境与受限备份继续保留。

[T-ACCESS-130](tasks/T-ACCESS-130.md) 将公共 Redis 事务锁接入工程规范 §9.4、AGENTS、开发与评审入口；仅文档与指引变更，场景、链接及技能镜像检查完成。

[T-ACCESS-129](tasks/T-ACCESS-129.md) 完成公共锁外评及类型身份缓存写入的存量缺陷修复；[评审与验证](tasks/evidence/T-ACCESS-129/verification.md)保留原始 gpt-6.1-sol 报告、同类入口反例及完整回归证据。完整回归3025项全部通过，包含E2E/heavy。

[后端并发加固](backend-concurrency-hardening.md)已完成，T-ACCESS-122～128 与[验收证据](tasks/evidence/backend-concurrency/verification.md)归档。公共 Redis 事务锁下沉 common，Q-083～Q-089 已闭合；完整回归3018项全部通过，包含E2E/heavy。

[T-ACCESS-121](tasks/T-ACCESS-121.md) 完成后端并发专项排查及[公共 Redis 锁草案](../../design/redis-transaction-locks.md)；[核实证据](tasks/evidence/T-ACCESS-121/verification.md)保留 gpt-6.1-sol 原报告与问题映射。排查发现未修复，不能把本卡终态当作实现完成。

[T-ACCESS-120](tasks/T-ACCESS-120.md) 完成 Codex 外评确认的 Redis 事务锁取锁中断修复；密码重置和树写锁统一处理获取结果与释放责任，[原始评审与验收](tasks/evidence/T-ACCESS-120/verification.md)已归档。

[T-ACCESS-119](tasks/T-ACCESS-119.md) 完成测试环境待解决问题校准与最小修复；[验收记录](tasks/evidence/T-ACCESS-119/verification.md)保存定向反例、完整回归与本地双轨结论。Q-056/Q-070/Q-072/Q-079/Q-081 已按验证结果及已确认边界收敛，其他事项继续暂缓。

[T-ACCESS-118](tasks/T-ACCESS-118.md) 完成标准文件能力四组外评核实与修复；[记录](tasks/evidence/T-ACCESS-118/verification.md)保留外评原文、反例、用户确认的边界与完整回归。

[顶栏裁剪与标准文件能力补齐](standard-capability-completion-plan.md)及三张任务卡已完成，Q-071/Q-078 已收敛；[验收记录](tasks/evidence/standard-capability/verification.md)保存正式 JAR、真实页面、分类授权与完整回归证据。

[登录审计事务边界收敛](login-audit-transaction-plan.md)与 T-ACCESS-117 已完成，Q-067 已收敛；[验收记录](tasks/evidence/T-ACCESS-117/verification.md)保存单连接池、行锁与完整回归证据。

[最近变更核实修复与状态校准](recent-change-review-remediation.md)已完成，相关任务与[统一验收](tasks/evidence/recent-change-review/verification.md)随计划归档；T-PERM-036 描述已校准并保持暂缓。

独立任务 [T-ACCESS-114](tasks/T-ACCESS-114.md) 完成服务凭证 SHA-256 验密与旧哈希退役，Q-076 已收敛。任务卡、[验收记录](tasks/evidence/T-ACCESS-114/verification.md)和源码哈希清单随卡归档。

当前权威为[服务认证](../../design/service-authentication.md#service-credential-digest)、[契约总册 §24](../../design/access-service-api-contract.md#contract-section-24-1)、[schema](../../design/schema/access-service.sql)与[换发说明](../../ops/deployment.md#service-credential-digest-upgrade)。本目录只供实施和验收追溯。

独立任务 [T-ACCESS-097](tasks/T-ACCESS-097.md) 已于 2026-10-08 完成，本次补齐物理归档并收敛 Q-069。当前契约见[OAuth2](../../design/access-service-api-contract.md#contract-section-6)，不重新启动已完成改造。
