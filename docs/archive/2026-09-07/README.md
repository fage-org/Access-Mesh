# 2026-09-07 归档批次：计划治理收尾

## 归档原因

本批次为计划治理收尾（用户 2026-09-07 指令「先归档治理」），按 project-rules §文档治理与 design-plan-task-lifecycle skill §6.5 执行物理归档与四处索引同步：

- `product-positioning-landing-plan`：两任务 T-ACCESS-027/028 全部 done，计划 2026-08-28 即 completed（自身进度区已登记「物理归档条件已满足，待后续归档批次执行」）。
- `frontend-phase3-plan`：九任务（T-FE-015~022 + T-FE-037）2026-09-04 全部 done；归档条件中的硬门禁——「menus 接线完成后统一补一轮全页导航/F5/直达 URL 冒烟验收」（2026-08-31 设计定案，归档门禁式）——于 2026-09-07 经用户拍板「跑统一冒烟后归档」并执行通过（21/21 PASS）。

## 归档门禁执行记录（frontend-phase3-plan）

- 环境：docker compose 三容器（nacos/redis/postgresql）+ access-service（按 runbook 重建库 + bootstrap 重种：131 grants / 15 menus / 82 apiResources / 默认组织树）+ gateway + 前端 vite 8890。
- 执行方式：playwright-core 驱动系统 Edge headless。IAB 在锁屏桌面下渲染帧冻结（rAF 600ms 零帧、Vue transition 卡 enter-from），不可用于冒烟——环境性限制，非产品缺陷。验证码经 Redis TTL 最大键读取；admin 密码在重建库后由 bootstrap 重种、与 dev-secrets 对齐（执行中确认旧库密码与 dev-secrets 漂移，sys_login_log 两次「密码错误」实证）。
- 结果 **21/21 PASS**：登录→welcome 1；侧栏后端派生菜单 14 项 1；**全页导航 13/13**（组织与用户/角色管理/类型定义/系统配置/操作日志/业务域/服务与接口/资源与操作/权限条件/冲突规则/资源依赖/权限变更日志/权限排查——每页主内容区渲染业务内容，空态文案计入有效渲染）；**直达 URL 3/3**（#/system/user、#/system/resource-operation、#/perm/grant?subjectType=ROLE 隐藏入口含 query 形态）；**F5 会话恢复 3/3**（type-def/biz-domain/operation-log，刷新后未回登录页、内容恢复）。
- 执行期订正一处误判：IAB 冻结态下「权限排查页死菜单」结论系渲染假象（grep 截断 + 渲染冻结双重误导；路由实际已注册），headless 下导航与渲染正常，撤回。
- 逐页内容证据：本机会话产物 `C:\Users\li\.zcode\tmp-smoke\result.json`（不入库）。

## 内容定位

| 文档 | 说明 |
|------|------|
| `product-positioning-landing-plan.md` | 产品定位落地计划（2026-08-28 立项 → 当日收口）。设计定案：产品定位 = 开源通用 IAM；暂缓能力（自动授权 T-PERM-035 / 动态数据权限 T-PERM-036）维持暂缓等 PM 重申。含三档叙事整改与 perm-data 删除两任务编排记录，仅作历史追溯 |
| `frontend-phase3-plan.md` | 前端 Phase 3 前后端联调计划（2026-06-29 立项 → 2026-09-04 九任务收口 → 2026-09-07 门禁通过归档）。含 menus 接线三项设计定案、逐页联调收口记录与归档门禁执行记录（上文），仅作历史追溯 |

## 归档自检清单执行记录

- [x] 源文件移动后已修复内部相对链接（positioning 任务清单表 `../tasks/` → `../../tasks/` 2 处；phase3 计划无 `../` 相对链接）。
- [x] 全仓 grep 旧路径 `product-positioning-landing-plan` / `frontend-phase3-plan` 无活文档残留（9 张任务卡 frontmatter `plan` 字段、tasks/README 看板 9+2 行、plans/README 当前计划表、docs/README 与 design/README 归档记录表均已同步指向归档位置或改为「已归档」标注；archive 内历史引用按惯例不改）。
- [x] `docs/README.md` 归档记录表已刷新（本批次置顶行含两计划与门禁记录）。
- [x] `docs/design/README.md` 归档记录表已刷新（本批次置顶行）。
- [x] 稳定结论已沉淀 `docs/design/`（三档叙事口径入口 `design/README`、architecture §4.2/§4.3；Phase 3 联调发现已随各任务回写 api-contract 等契约文档；本批次归档文档不再作为实现依据）。

## 当前权威设计入口

- 产品定位与三档口径入口：`docs/design/README.md`
- 前端页面契约与 UI 终态：`docs/design/permission-center/api-contract.md` + `docs/design/frontend/*.md`
- menus 接线与 bootstrap 固定图：`docs/design/access-service-architecture.md` §14
