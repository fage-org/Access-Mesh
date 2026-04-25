# ADR-004：Gateway StripPrefix 与接口路径映射协调

**状态**：✅ 已采纳

**日期**：2026-04-24

**上下文**

Gateway 通过 StripPrefix 剥离路径前缀转发到后端服务（如 `/admin/api/users` → `/api/users`）。
问题：`resource_api_mapping.path_pattern` 应存储 Gateway 剥离前还是剥离后的路径？这直接影响鉴权匹配。

**决策**

`resource_api_mapping.path_pattern` 存储 **Gateway 接收到的原始请求路径**（客户端发送的路径）。

**实现**：

- 服务上报接口时，SDK 自动拼接 `service_config.base_path` + 实际监听路径 = 完整路径
- Gateway 鉴权时直接用客户端原始请求路径匹配 `path_pattern`
- 示例：
  - admin-service 监听 `POST /api/users/list`
  - service_config.base_path = `/admin`
  - 自动生成 path_pattern = `POST /admin/api/users/list`
  - Gateway 收到 `POST /admin/api/users/list` → StripPrefix=1 → 转发 `POST /api/users/list`

**后果**

- 每个路由的 StripPrefix 值可独立配置
- 新增服务路由时按规划自由调整剥离层级
- 上报侧自动拼接，Gateway 侧直接匹配，逻辑一致
