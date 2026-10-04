# T-FE-062 验证证据

2026-10-04：保留菜单种子与静态路由的图标键，补齐离线注册。`ep/coins`、`ep/history` 经确认分别注册现有 `coin`、`clock` 图形；无后端、schema 或权限行为变更。当前规则见 [登录与菜单设计](../../../../../design/frontend/login.md)。

- `pnpm build`、`pnpm typecheck`、`pnpm lint` 均退出 0。
- `pnpm test`：41 文件、484 案例通过，零失败。
- 浏览器：从 `BootstrapGraphDefinition` 提取菜单图标键，用 Vite 加载实际 `offlineIcon.ts` 和 Iconify 离线组件逐个渲染，截图目视确认全部种子图标显示，包括 coins/history。临时验证页面已删除。此证据验证离线注册与渲染，不冒充登录后侧栏的端到端验收。

代码轨：逐项核对种子、静态路由、实际 Iconify 图形存在性及离线分支；注册沿用现有机制，无新增抽象或运行时网络请求，无 P0–P3 问题。

文档轨：核对已确认的别名映射与设计、看板、计划、问题收敛一致；无新契约、无待决事项。不为静态注册清单添加重复实现式测试。
