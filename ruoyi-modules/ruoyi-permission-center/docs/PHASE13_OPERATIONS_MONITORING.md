# Phase 13: 运维、监控与治理

## 验收状态：已完成

---

## 1. 输入依赖

- Phase 0-12 所有后端接口已就绪
- ruoyi-common-prometheus 模块（Micrometer + Prometheus）
- Spring Cloud Bus（RabbitMQ）
- Actuator 端点配置

---

## 2. 输出接口

### 2.1 指标体系

| 指标名称 | 类型 | 说明 |
|----------|------|------|
| `permission.check.requests` | Counter | 鉴权请求总数 |
| `permission.check.duration` | Timer | 鉴权耗时 |
| `permission.check.denials` | Counter | 鉴权拒绝数 |
| `permission.snapshot.requests` | Counter | 快照请求总数 |
| `permission.snapshot.duration` | Timer | 快照耗时 |
| `permission.snapshot.cache.hits` | Gauge | 缓存命中数 |
| `permission.snapshot.cache.misses` | Gauge | 缓存未命中数 |
| `permission.snapshot.cache.size` | Gauge | 缓存大小 |
| `permission.version.requests` | Counter | 版本查询请求总数 |
| `permission.version.failures` | Counter | 版本查询失败数 |
| `permission.version.changes` | Counter | 版本变更数 |
| `permission.condition.evaluations` | Counter | 条件求值请求总数 |
| `permission.condition.errors` | Counter | 条件求值异常数 |
| `permission.condition.duration` | Timer | 条件求值耗时 |
| `permission.conflict.checks` | Counter | 冲突检测请求总数 |
| `permission.conflict.hits` | Counter | 冲突命中数 |
| `permission.dependency.checks` | Counter | 依赖检查请求总数 |
| `permission.dependency.failures` | Counter | 依赖检查失败数 |
| `permission.audit.writes` | Counter | 审计写入请求总数 |
| `permission.audit.failures` | Counter | 审计写入失败数 |

### 2.2 Gateway 指标

| 指标名称 | 类型 | 说明 |
|----------|------|------|
| `gateway.permission.requests` | Counter | Gateway 鉴权请求总数 |
| `gateway.permission.duration` | Timer | Gateway 鉴权耗时 |
| `gateway.permission.denials` | Gauge | Gateway 鉴权拒绝数 |
| `gateway.permission.cache.hits` | Gauge | Gateway 缓存命中数 |
| `gateway.permission.cache.misses` | Gauge | Gateway 缓存未命中数 |
| `gateway.permission.http.calls` | Counter | Gateway HTTP 调用数 |
| `gateway.permission.http.duration` | Timer | Gateway HTTP 调用耗时 |
| `gateway.permission.http.failures` | Gauge | Gateway HTTP 调用失败数 |

### 2.3 日志体系

| 日志类型 | Logger | 说明 |
|----------|--------|------|
| 鉴权拒绝日志 | `PERMISSION_AUDIT` | 记录鉴权拒绝详情 |
| 快照刷新日志 | `PERMISSION_AUDIT` | 记录快照刷新详情 |
| 版本变更日志 | `PERMISSION_AUDIT` | 记录版本变更详情 |
| 条件审核日志 | `PERMISSION_AUDIT` | 记录条件审核详情 |
| 冲突发现日志 | `PERMISSION_AUDIT` | 记录冲突发现详情 |
| 依赖断裂日志 | `PERMISSION_AUDIT` | 记录依赖断裂详情 |

### 2.4 告警规则

| 告警名称 | 级别 | 触发条件 |
|----------|------|----------|
| PermissionSnapshotHighFailureRate | warning | 快照失败率 > 10% |
| PermissionSnapshotHighLatency | warning | 快照 P95 延迟 > 1s |
| PermissionCacheHitRateLow | warning | 缓存命中率 < 50% |
| PermissionCheckHighDenialRate | warning | 鉴权拒绝率 > 30% |
| PermissionCheckHighLatency | warning | 鉴权 P95 延迟 > 500ms |
| PermissionVersionQueryHighFailureRate | warning | 版本查询失败率 > 10% |
| PermissionConditionEvaluationErrors | warning | 条件求值异常 > 10/min |
| PermissionConflictHits | warning | 冲突命中 > 5/min |
| PermissionDependencyCheckFailures | warning | 依赖检查失败 > 10/min |
| PermissionAuditWriteFailures | critical | 审计写入失败 > 1/min |
| GatewayPermissionDenialSpike | warning | Gateway 拒绝 > 100/min |
| GatewayPermissionHttpFailures | warning | Gateway HTTP 失败 > 10/min |

### 2.5 灰度开关

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `gateway.authz.enabled` | false | Gateway 鉴权总开关 |
| `gateway.authz.fail-open` | true | 无快照时是否放行 |
| `gateway.authz.grayscale-tenants` | [] | 灰度租户列表 |
| `gateway.authz.grayscale-routes` | [] | 灰度路由列表 |
| `gateway.authz.grayscale-users` | [] | 灰度用户列表 |
| `gateway.authz.grayscale-percentage` | 0 | 灰度比例 |
| `gateway.authz.precise-check-enabled` | false | 精确鉴权开关 |
| `gateway.authz.rollback-version` | null | 回滚版本 |
| `gateway.authz.excluded-tenants` | [] | 排除租户列表 |
| `gateway.authz.excluded-routes` | [] | 排除路由列表 |
| `auth.permission.version-write-enabled` | true | 登录版本写入开关 |
| `auth.permission.subject-mapping-enabled` | true | 主体映射开关 |
| `auth.permission.fallback-mode` | false | 降级模式 |
| `permission.kernel.precise-check-enabled` | true | 精确鉴权开关 |
| `permission.kernel.condition-evaluation-enabled` | true | 条件求值开关 |
| `permission.kernel.conflict-detection-enabled` | true | 冲突检测开关 |
| `permission.kernel.dependency-check-enabled` | true | 依赖检查开关 |

---

## 3. 实施内容

### 3.1 指标收集器

- **PermissionMetrics.java** - 指标名称常量类
- **PermissionMetricsCollector.java** - 权限中心指标收集器
- **GatewayPermissionMetrics.java** - Gateway 指标收集器

### 3.2 结构化日志

- **PermissionLogEntry.java** - 日志实体类
- **PermissionLogWriter.java** - 日志记录器

### 3.3 告警规则

- **permission_alerts.yml** - Prometheus 告警规则文件

### 3.4 灰度开关

- **PermissionAuthzProperties.java** - Gateway 灰度配置（扩展）
- **PermissionEnhancementProperties.java** - Auth 权限增强配置
- **PermissionKernelProperties.java** - 权限内核配置

### 3.5 MQ 版本失效通知

- **PermissionVersionChangedEvent.java** - 版本变更事件
- **PermissionVersionMqPublisher.java** - MQ 发布器
- **PermissionVersionEventListener.java** - Gateway 事件监听器

---

## 4. 验收记录

### 4.1 定向回归

```bash
mvn -pl ruoyi-modules/ruoyi-permission-center -am test -DskipTests=false -Pdev "-Dtest=PermissionMetricsCollectorTest"
```

**结果**：Tests run: 10, Failures: 0, Errors: 0, Skipped: 0

### 4.2 关键验证点

- [x] 指标收集器可正常记录各项指标
- [x] 缓存命中率计算正确
- [x] 日志记录器可输出 JSON 格式日志
- [x] 告警规则文件格式正确
- [x] 灰度开关配置可动态刷新
- [x] MQ 事件可正常发布和监听

---

## 5. 配置示例

### 5.1 Gateway 配置

```yaml
gateway:
  authz:
    # 是否启用接口鉴权
    enabled: true
    # 无快照或无匹配时是否放行
    fail-open: true
    # 是否启用 HTTP 客户端
    http-client-enabled: true
    # permission-center 地址
    permission-center-url: http://permission-center:9202
    # 缓存配置
    cache-max-size: 10000
    cache-expire-minutes: 5
    # 灰度租户
    grayscale-tenants:
      - "000000"
    # 灰度路由
    grayscale-routes:
      - "/api/order/**"
      - "/api/product/**"
    # 排除路由
    excluded-routes:
      - "/api/health"
      - "/actuator/**"
```

### 5.2 Auth 配置

```yaml
auth:
  permission:
    # 是否启用登录版本写入
    version-write-enabled: true
    # 是否启用主体映射
    subject-mapping-enabled: true
    # 降级模式
    fallback-mode: false
    # permission-center 地址
    permission-center-url: http://permission-center:9202
```

### 5.3 Permission-center 配置

```yaml
permission:
  kernel:
    # 是否启用精确鉴权
    precise-check-enabled: true
    # 是否启用条件求值
    condition-evaluation-enabled: true
    # 是否启用冲突检测
    conflict-detection-enabled: true
    # 是否启用依赖检查
    dependency-check-enabled: true
    # 快照缓存配置
    snapshot-cache-max-size: 10000
    snapshot-cache-expire-minutes: 5
```

---

## 6. 风险与未决问题

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 指标采集影响性能 | 中 | 使用 Micrometer 内置采样机制 |
| 日志量过大 | 中 | 配置日志滚动策略 |
| 告警风暴 | 低 | 合理设置告警阈值和聚合窗口 |
| MQ 消息丢失 | 中 | 实现 MQ 消息确认机制 |

---

## 7. 后续优化方向

1. **Grafana 大盘**：创建权限中心专用监控大盘
2. **告警通知**：对接钉钉、企业微信等通知渠道
3. **日志平台**：对接 ELK 或其他日志平台
4. **分布式追踪**：集成 SkyWalking 或 Zipkin

---

## 8. 相关文件

| 文件 | 说明 |
|------|------|
| `PermissionMetrics.java` | 指标名称常量 |
| `PermissionMetricsCollector.java` | 指标收集器 |
| `GatewayPermissionMetrics.java` | Gateway 指标收集器 |
| `PermissionLogEntry.java` | 日志实体类 |
| `PermissionLogWriter.java` | 日志记录器 |
| `permission_alerts.yml` | 告警规则文件 |
| `PermissionAuthzProperties.java` | Gateway 灰度配置 |
| `PermissionEnhancementProperties.java` | Auth 权限增强配置 |
| `PermissionKernelProperties.java` | 权限内核配置 |
| `PermissionVersionChangedEvent.java` | 版本变更事件 |
| `PermissionVersionMqPublisher.java` | MQ 发布器 |
| `PermissionVersionEventListener.java` | Gateway 事件监听器 |
