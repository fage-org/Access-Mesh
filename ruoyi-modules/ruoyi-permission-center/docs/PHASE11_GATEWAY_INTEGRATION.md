# Phase 11: Gateway Integration

## 验收状态：已完成

---

## 1. 输入依赖

- Phase 9 的 `/api/perm/policy/interface-snapshot` 接口快照接口
- Phase 9 的 `/api/perm/version/query` 版本查询接口
- Phase 10 的令牌扩展信息（`permissionVersion`、`abstractUserId`）
- Spring Cloud Gateway 基础设施
- Caffeine 缓存库

---

## 2. 输出接口

### 2.1 Gateway 内部组件

| 组件 | 说明 |
|------|------|
| `HttpPermissionSnapshotClient` | HTTP 权限快照客户端，调用 permission-center |
| `PermissionSnapshotCache` | 本地快照缓存，使用 Caffeine 实现 |
| `GatewayPermissionAuthorizer` | 网关鉴权入口，整合缓存和匹配器 |
| `PrincipalContextResolver` | 主体上下文解析器，从令牌读取扩展信息 |
| `PermissionRuleMatcher` | 接口权限规则匹配器 |

### 2.2 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `gateway.authz.enabled` | `false` | 是否启用接口鉴权 |
| `gateway.authz.fail-open` | `true` | 无快照或无匹配时是否放行 |
| `gateway.authz.http-client-enabled` | `false` | 是否启用 HTTP 客户端 |
| `gateway.authz.permission-center-url` | `http://localhost:9202` | permission-center 地址 |
| `gateway.authz.cache-max-size` | `10000` | 缓存最大容量 |
| `gateway.authz.cache-expire-minutes` | `5` | 缓存过期时间（分钟） |
| `gateway.authz.grayscale-tenants` | `[]` | 灰度租户列表 |
| `gateway.authz.grayscale-routes` | `[]` | 灰度路由列表 |

---

## 3. 实施内容

### 3.1 HTTP 权限快照客户端

- **类**：`HttpPermissionSnapshotClient`
- **功能**：
  - 调用 `/api/perm/policy/interface-snapshot` 获取快照
  - 调用 `/api/perm/version/query` 查询版本
  - 重试机制（最多 3 次，指数退避）
  - 降级策略（返回空快照）

### 3.2 本地快照缓存

- **类**：`PermissionSnapshotCache`
- **缓存键**：`(tenantId, subjectKey, permissionVersion)`
- **缓存策略**：
  - 最大容量：10000 条
  - 过期时间：5 分钟
  - 版本变化自动失效（新版本创建新条目）
- **统计信息**：命中率、命中次数、未命中次数、驱逐次数

### 3.3 主体上下文解析

- **类**：`PrincipalContextResolver`
- **扩展**：
  - 从令牌扩展信息读取 `permissionVersion`
  - 从令牌扩展信息读取 `abstractUserId`
  - 优先级：请求头 > 令牌扩展 > 默认值

### 3.4 灰度发布策略

- **租户级灰度**：仅对配置的租户启用鉴权
- **路由级灰度**：仅对配置的路由启用鉴权
- **匹配规则**：支持通配符 `/**`

---

## 4. 失败模式

| 场景 | 处理方式 |
|------|----------|
| permission-center 不可用 | 返回空快照，根据 `fail-open` 决定是否放行 |
| 缓存未命中 | 从 permission-center 加载 |
| 版本变化 | 新版本创建新缓存条目，旧条目自动过期 |
| 规则不匹配 | 根据 `fail-open` 决定是否放行 |

---

## 5. 验收记录

### 5.1 定向回归

```bash
mvn -pl ruoyi-gateway -am test -DskipTests=false -Pdev \
  "-Dsurefire.failIfNoSpecifiedTests=false" \
  "-Dtest=PermissionSnapshotCacheTest,PermissionAuthzPropertiesTest,PermissionRuleMatcherTest"
```

**结果**：Tests run: 14, Failures: 0, Errors: 0, Skipped: 0

### 5.2 关键验证点

- [x] 缓存命中时不重复调用 permission-center
- [x] 缓存未命中时从 delegate 加载
- [x] 版本变化后获取新快照
- [x] 灰度租户过滤正确
- [x] 灰度路由匹配正确（支持通配符）
- [x] 令牌扩展信息正确读取

---

## 6. 风险与未决问题

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| HTTP 调用延迟 | 所有请求增加鉴权延迟 | 使用本地缓存减少调用频率 |
| 缓存容量限制 | 热点用户可能被驱逐 | 可根据实际调整 `cache-max-size` |
| 灰度配置复杂 | 配置错误可能导致鉴权失效 | 提供配置校验和监控 |

---

## 7. 后续优化方向

1. **Dubbo 集成**：使用 Dubbo 替代 HTTP 调用 permission-center
2. **异步预加载**：在登录时预加载快照，减少首次请求延迟
3. **监控指标**：暴露缓存命中率、鉴权延迟等 Prometheus 指标
4. **配置中心**：支持 Nacos 动态更新灰度配置

---

## 8. 相关文件

| 文件 | 说明 |
|------|------|
| `HttpPermissionSnapshotClient.java` | HTTP 权限快照客户端 |
| `PermissionSnapshotCache.java` | 本地快照缓存 |
| `GatewayPermissionAuthorizer.java` | 网关鉴权入口 |
| `PrincipalContextResolver.java` | 主体上下文解析器 |
| `PermissionRuleMatcher.java` | 规则匹配器 |
| `PermissionAuthzProperties.java` | 配置属性 |
| `PermissionSnapshotCacheTest.java` | 缓存测试 |
| `PermissionAuthzPropertiesTest.java` | 配置测试 |

---

## 9. 配置示例

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
    # 灰度租户（为空表示所有租户）
    grayscale-tenants:
      - "000000"
    # 灰度路由（为空表示所有路由）
    grayscale-routes:
      - "/api/order/**"
      - "/api/product/**"
```
