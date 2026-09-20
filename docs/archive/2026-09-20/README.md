# 2026-09-20 归档批次

- **frontend-session-consistency-plan.md**：前端会话生命周期与交互一致性修复（2026-09-19 立项 → 2026-09-20 收口归档）。T-GW-009 + T-FE-045~056 十三任务全 done：P1 阻断试用（登出真注销 / 强制改密闭环 / 用户删除确认 / 会话权限热刷新 / 登录半成功语义）+ P2 一致性清理（findParentOrgName 递归 / 列表加载 composable 化 / 跨层级拖拽+禁用确认 / 守卫 next-return 卫生 / token 过期短路 / 授予入口文案二分 / menus 派生路由级 UX 门禁）+ Gateway 白名单纳入 reset-password 前置。逐卡双轨评审与外部评审（claude/codex）处置完毕，定案见 `decision-registry.md` 2026-09-19/09-20 各行。
- **tasks/**（九张独立卡随计划归档；T-GW-009、T-FE-049/053/055 为纯看板行无独立卡）：T-FE-045/046/047/048/050/051/052/054/056。
- **归档批次三项待办执行通过（当日起栈验证）**：
  1. 全量回归 `mvn test -T 1C`（E2E 实跑）1726 项 0 失败 0 错误 0 跳过（容器轨 212 + E2E 14 实跑；首跑 Docker 未启动致静默跳过、起 Docker 后重跑收敛）；
  2. T-FE-046 端到端验收六项判据全过：smoke-release-user（仅 `example:demo:hello` 授权、无 API:ACCESS）经 admin 用户页真实管理链路重置密码 → 登录即阻断 redirect /change-password → 深链 #/system/user 与 #/system/role 均被守卫弹回 → 纯空白密码（8 空格）被前端 whitespace 规则拦截 → 正确改密成功放行进系统 → 后端 force_reset_pwd 翻转 false + 新密码重登直达不再阻断；reset-password 请求经 Gateway 白名单放行（T-GW-009 前置闭环实证）；
  3. T-GW-009 Nacos 远端覆盖键复核：`dataId=gateway.yml&group=DEFAULT_GROUP` → `config data not exist`，远端无覆盖、白名单即本地 application.yml 清单，遗留收敛。
- **权威设计入口**：`docs/design/frontend/login.md`（登出流程 / 强制改密闭环 / 会话权限热刷新 / 路由级 UX 门禁等节）、`docs/design/frontend/permission-grant.md`、`docs/design/frontend/role-manage.md`、`docs/design/services/gateway.md`（白名单清单）。
