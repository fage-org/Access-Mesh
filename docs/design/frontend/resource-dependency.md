# 3.4 资源依赖页前端设计

> status: adopted
> last_reviewed: 2026-09-21
> 当前实现：T-PERM-071 的管理写入口关闭与只读页面 + T-PERM-073 的声明诊断与来源解释（本页 §5 已实现）。
> 正式契约：[总册 §12.3](../access-service-api-contract.md#123-资源依赖只读查询apiaccessresource-dependency)、[manifest §19.10（071 已交付）](../access-service-api-contract.md#1910-独立依赖-manifest071-已交付)。历史 CRUD 联调证据见 [T-FE-044](../../archive/2026-09-14/tasks/T-FE-044.md)，不作为现役写契约。

## 1. 用途与边界

本页只读查看资源依赖图，并提供循环检查。依赖声明由所属业务服务通过 manifest 发布；页面提示修改入口所在服务。不存在手工新增、编辑、删除、批量导入或跨 owner override。

source 是被授权资源，target 是自动补全的目标资源。sourceOperationBits 为触发操作（null 为任意操作），requiredOperationBits 为目标操作；自动授权物化已随 072 落地（图中存在依赖且角色持有满足触发的种子即会物化 AUTO_DEP；物化不改变资源启停等运行时鉴权语义），依赖页仍只读。

## 2. 布局与交互

顶部说明条提示“依赖声明由所属业务服务发布，本页提供只读查看与检查”。PureTableBar 保留关键词过滤、重置、刷新、循环检查和依赖图入口。表格列为依赖关系、触发操作、要求操作、描述、创建时间；移除自动开关列和操作列。空态提示由所属服务发布声明。

名称、资源类型优先使用响应中的静态字段，资源树映射仅作回退；资源停用或引用数据加载失败不应使已有名称丢失。操作名按资源类型隔离，通过 BigInt 位运算映射，避免跨类型同 bit 误配与 32 位截断。

依赖图沿用 el-drawer 与 ECharts 图组件，支持缩放和拖动；循环检查由 CycleCheckDialog 发送源/目标业务键。页面不在前端推导角色自动权限或计算保存影响。

## 3. 请求与权限

| 请求 | 门禁 | 用途 |
|---|---|---|
| resource-dependency/list | DEPENDENCY:VIEW | 全量列表，关键词前端过滤 |
| resource-dependency/graph | DEPENDENCY:VIEW | 扁平边列表建图 |
| resource-dependency/check | DEPENDENCY:VIEW | 只读成环检查 |

路径统一 `/api/access/resource-dependency/*`，POST + JSON。门禁保持 computed，随会话热刷新更新；路由门禁仍走 menus 派生机制，详见 login.md。`perms.ts` 的 PERMS、PERM_LIST 与 VIEW_PERMS 仅提供 VIEW，路由和 mock 共同消费，不保留写权限按钮常量。

useListLoad 保持 latest-wins 与失败保留旧列表；失败必须提示，不能伪装空结果。图读取失败不打开一个伪造空图。只读状态不替代服务端权限检查。

## 4. 组件与接线

- index.vue：说明条、过滤、只读表格、检查对话框与图抽屉。
- utils/hook.ts：列表与引用数据装载、资源/操作名称映射、过滤；无创建/更新/删除请求构造。
- components/CycleCheckDialog.vue、DependencyGraph.vue：沿用只读交互。
- api/resource-dependency.ts：只暴露 list/graph/check/explain/declaration-status 五个只读接口（后两个见 §5）；旧写 API 和 DependencyForm/types 已删除。

## 5. 声明诊断与来源解释（T-PERM-073 已实现，2026-09-21）

工具栏两个只读诊断入口（2026-09-21 用户定案：explain 前端入口仅本页）：

- **声明状态**（`resource-dependency/declaration-status`，DEPENDENCY:VIEW）：抽屉展示每服务 manifest 发布状态（代次/revision/状态/dirty/最近同步）+ 声明行（源→目标业务键、RESOLVED/REJECTED 与拒绝原因中文释义）；REJECTED 行提示由所属服务重新发布恢复，载荷损坏降级 null 业务字段不遮蔽状态面。
- **来源解释**（`resource-dependency/explain`，DEPENDENCY:VIEW）：抽屉输入角色业务键（类型下拉 BASIC_ROLE/ORG/POSITION + externalId）+ 可选目标事实（资源类型/资源/操作级联选择，条件恒查无条件变体）→ 节点/边表展示共享逻辑 DAG；节点状态四象限（显式种子/已生效/应有未落库/无来源存量）区分 desired 与 actual；点选节点沿直接边本地展开；截断（总数另报）与漂移（角色级）显式提示，读取失败不解释为「无来源」。

explain/预览的正式门禁、条件身份、截断与漂移规则以契约 §11.4.1/§12.3.1 为准。界面不得将截断或读取失败解释为“无来源”，不得把 desired 当成实际已生效权限。纯函数（事实标签/状态象限/边过滤）落 `utils/explain-view.ts`（含 vitest），抽屉组件 `components/DeclarationStatusDrawer.vue`、`components/ExplainViewerDrawer.vue`。
