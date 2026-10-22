# T-FE-080 实施验证

日期：2026-10-22。代码基线：`ca952ae7a`。联合结果见 [T-ACCESS-150](../T-ACCESS-150/verification.md)。

## 自动验证

- `pnpm exec vitest run`：61 files / 648 tests 全部通过。
- `pnpm typecheck`、改动文件 ESLint、`pnpm contracts:check`（49 registered groups）及 `pnpm exec vite build` 均通过。
- 候选读取复用 useListLoad/usePagedList；受控 Promise 验证 A/B 切换、迟到成功/失败和失败清空，未使用固定 sleep。首次配置不依赖持久化主授权 ID。
- 当前页全选保存具体子项，动态 ALL 独立；父 ALL 使用类型目录，具体父使用关系候选。已有子项独立展示并可撤销。类型声明保留未触达扩展字段，未知模式不降为普通资源。
- 响应消费使用已登记 ResourceTreeNode、ResourceAssociationOwnerResp、ResourceAssociationDetailResp、ResourceResp，没有另建未登记的响应镜像。

## 真实浏览器验证

环境：localhost:8848，独立 E2E PostgreSQL/Redis 与 access/gateway/example 进程，e2e-customer 测试租户。用户已授权本次验证码及登录；无真实业务账户或数据。

1. 资源管理选择销售报表 amount（code 9）：显示唯一归属 sales-report、业务来源 example-service；业务实例编辑入口隐藏。[只读来源截图](resource-field-readonly.png)。
2. 从角色管理进入 association-reader 原授权页。assoc-b 的字段候选只有其 recordCode 13、amount 14，已有勾选正确；[专属字段截图](grant-exclusive-b.png)。
3. 同一父授权切换 DEMO_DATA_SCOPE，整个有效目录含 self、team-t1、department-d1、region-r1、region-r2，只有原有 R2 被选；[通用目录截图](grant-shared-b.png)。
4. 切换销售报表并首次勾选其 DATA_READ 主权限；子字段列表切换为该报表的 2～10，选择 recordCode 2 与 amount 9，再在通用目录仅选择 R1。动态 ALL 保持未选。
5. 确认形成 4 条新增（主权限 1 + 子权限 3），点击“保存全部”后未保存清单消失。[保存结果](grant-saved.png)。重新打开授权弹窗，sales-report 和 assoc-b 均显示“已授权、3 子权限”，原 B 配置保留；[回读截图](grant-reopened.png)。

浏览器及独立服务已在验证后关闭。截图证明页面路径；真实访问限制由 PG 与跨服务 E2E 证明，不以截图替代鉴权测试。

## 本地评审

代码轨：主代理只读核对加载、主上下文切换、分页、勾选、草稿来源、只读入口、声明更新和异常态；未发现未处理的确定性缺陷。复用原页面、草稿事务与请求代际，不新增关系工作台或独立授权状态系统。

文档轨：独立核对关系契约、页面设计和 DTO 清单；整类引用与实例授权分开，页面未提供通用实例子集引用。真实浏览器证据已补齐。存疑待用户决策项及无调用方的可裁剪机制：无。
