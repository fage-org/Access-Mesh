# 2026-10-20 归档

[example-service 持久化权限演示计划](example-permission-demo-plan.md)已完成：执行任务均 done，T-FE-079 业务页面任务 cancelled。计划、十张任务卡和关联证据从 docs/plans、docs/tasks 迁入本批次；不撤销单实例、后台无用户 IP、成功产物归属及其他现行边界。

当前权威为[权限演示设计](../../design/example-permission-demo.md)、[API 契约](../../design/example-service-api-contract.md)、[服务设计](../../design/services/example-service.md)和[example schema](../../design/schema/example-service.sql)。操作入口见[使用指南](../../../example-service/examples/permission-demo.md)与[联合场景包](../../../example-service/examples/permission-demo-scenarios.md)。

[最终验收](tasks/evidence/example-permission-demo/T-ACCESS-142.md)保存完整联合主线、可执行 JAR、动态配置产物/进程观测、状态分类修正及标准完整回归。最终 `mvn test -T 1C`：3,148 passed / 0 failed / 0 error / 0 skipped，包含 E2E/heavy。此前跳过与失败均有独立记录，没有被计为成功。

源码与验证结果为本地交付；冻结候选部署仍保留其原版本与历史验收边界。
