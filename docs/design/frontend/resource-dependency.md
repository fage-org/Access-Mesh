# 3.4 资源依赖页前端设计

> status: adopted
> last_reviewed: 2026-09-21
> 当前实现：T-PERM-071 的管理写入口关闭与只读页面；进一步的声明诊断、角色来源解释由 T-PERM-073 实现。
> 正式契约：[总册 §12.3](../access-service-api-contract.md#123-资源依赖只读查询apiaccessresource-dependency)、[manifest §19.10](../access-service-api-contract.md#1910-独立依赖-manifest071-实施中)。历史 CRUD 联调证据见 [T-FE-044](../../archive/2026-09-14/tasks/T-FE-044.md)，不作为现役写契约。

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
- api/resource-dependency.ts：只暴露 list/graph/check；旧写 API 和 DependencyForm/types 已删除。

## 5. 待实施展示

073 将增加声明来源服务、发布/编译状态、失败原因，以及按角色目标权限读取的来源 DAG。explain/preview 的正式门禁、条件身份、截断与漂移规则以契约 §11.4.1/§12.3.1 为准。界面不得将截断或读取失败解释为“无来源”，不得把 desired 当成实际已生效权限。
