# Phase 7 Condition System

本文件补充 `ruoyi-permission-center` 条件系统的 Phase 7 落地说明，优先级服从 `plan/PHASE0_*`、`plan/DESIGN.md`、`plan/MIXED_KERNEL_EXECUTION_PLAN.md`。

## 范围

- 目标表：`permission_condition`
- 作用边界：
  - 条件授权仅参与精确鉴权
  - `gateway` 不负责条件求值
  - 接口快照默认不携带条件

## 功能点

1. 条件建模
   - 支持 `PRESET` / `CUSTOM`
   - 自定义条件创建默认进入 `PENDING`
   - PRESET 条件固定保持 `APPROVED`
   - 增加独立 `enabled` 开关实现启停，和审核状态解耦
2. 条件审核
   - 自定义条件通过 `APPROVED` / `REJECTED` 完成审核
   - 审核动作要求写入 `reviewedBy` / `reviewedAt`
   - 已审核的自定义条件发生定义变更时自动退回 `PENDING`
3. 条件启停
   - `enabled=true` 表示启用
   - `enabled=false` 表示停用
   - 运行时条件生效要求同时满足 `status=APPROVED` 且 `enabled=true`
4. PRESET handler 机制
   - `handler code -> Spring Bean`
   - 首批内置：
     - `WORKDAY_ONLY`
     - `INTERNAL_IP`
   - 保存 PRESET 条件时校验 handler 已注册
   - 不再接受外部 `condition:{code}` / `{code}` 显式覆盖
5. CUSTOM 表达式机制
   - 使用 SpEL
   - 表达式先做语法校验，再执行
   - 执行使用 `SimpleEvaluationContext + MapAccessor`
   - 默认 200ms 超时保护，超时或拒绝均按失败处理
   - 默认执行器改为有界队列，避免慢表达式无限堆积
6. 条件上下文
   - 固定暴露五类上下文：
     - `request`
     - `subject`
     - `network`
     - `resource`
     - `business`
   - 其中 `request` / `subject` / `network` / `resource` 由服务端构建，不信任调用方同名字段
   - 同时保留兼容性扁平别名，如 `tenantId`、`userId`、`clientIp`、`currentDateTime`，但这些别名只作为服务端上下文的扁平投影，不接受调用方直接注入保留字段
   - `business` 上下文也不允许出现与上述保留别名重名的字段，命中时统一按 `PERM-101` 拒绝
   - Java 内部调用中，`PermissionCheckRequest.context` 仅承载业务上下文；受信任的 `request` / `network` 等命名空间通过内部 `trustedContext` 注入
7. 管理接口
   - 新增 REST 契约：
      - `GET /api/perm/conditions`
      - `POST /api/perm/conditions`
      - `PUT /api/perm/conditions/{conditionId}`（支持审核 / 启停等局部更新）
   - 保留历史兼容入口：
     - `POST /api/perm/conditions/list`
     - `POST /api/perm/conditions/save`
     - `POST /api/perm/conditions/remove`
8. 参数与错误语义
   - 条件请求 DTO 补齐校验
   - 非法 `conditionSource` / `status` / 未注册 PRESET / 非法 CUSTOM 表达式统一返回 `PERM-101`
   - 更新/删除不存在条件统一返回 `PERM-101`
   - 已停用条件在授权写入阶段按 `PERM-106` 处理

## 条件上下文示例

```json
{
  "request": {
    "requestId": "req-001",
    "currentDateTime": "2026-04-06T10:00:00"
  },
  "subject": {
    "tenantId": 1,
    "userId": 1001,
    "bizDomainId": 2001
  },
  "network": {
    "clientIp": "10.0.0.8"
  },
  "resource": {
    "resourceEntityId": 3001,
    "resourceCode": "API_ORDER",
    "operationPermissionId": 4001,
    "operationCode": "ACCESS"
  },
  "business": {
    "level": 3,
    "enabled": true
  }
}
```

## 回归命令

```bash
mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -Pdev "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PermissionConditionServiceImplTest,PermissionConditionExpressionEvaluatorTest,DefaultConditionEvaluatorTest,PermissionConditionControllerTest,ControllerValidationTest,PermissionServiceImplTest,PermissionServicePipelineIntegrationTest"
```

## 风险与后续

- 当前 CUSTOM 表达式超时采用线程池隔离，后续可继续收敛为可配置执行器与指标埋点。
- 目前接口快照仍保持首期边界；真正面向 `gateway` 的条件剔除逻辑继续以 Phase 9 交付为准。
