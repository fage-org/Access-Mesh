# T-ACCESS-065 树过滤与父名称验证

2026-10-04 定向验证：`mvn test -pl access-service -Dtest=RoleManageAppServiceImplTest,ResourceManageAppServiceImplTest,OrgAppServiceImplDelegatedDirectoryTest,UserWriteAppServiceCreateStatusTest,ResourceOperationKeyPgIT,OrgTreeIncludePositionsPgIT,UserCreateWithOrgPgIT`，单测 80 项、容器 27 项，零失败/错误/跳过。追加资源 SQL→服务构树联动验证与 COMMENT 原样 DDL 验证：`mvn test -pl access-service -Dtest=AccessServiceSchemaPostgresTest,ResourceOperationKeyPgIT#resourceTreeIncludesDisabledResourcesByDefault`，22 项通过。

前端 `pnpm test`：41 文件、487 项通过；`pnpm typecheck`、`pnpm lint`、`pnpm build` 均退出 0。最终完整后端回归已通过，见[最终验收](../pending-problems-clearance/final-audit.md)。

| 子项 | 原行为证据 | 当前验证 |
|---|---|---|
| 过滤父节点后的子支 | 角色测试原实现期望 1 根、实际 0，断言失败 | 公共 TreeBuilder 仅用过滤后的节点集选展示根，保留 parentId；角色与真实资源查询都覆盖 |
| 资源状态口径 | 真实 PG 原 selectResourceTree 漏掉停用父，包含断言失败 | false 包含父子，true 排除停用父但保留启用子；服务构树将子提升为展示根；管理缺省全状态、选择器显式 true |
| 父组织名 | 旧 OrgResp 无字段；直接执行 Git HEAD 的旧前端函数，空过滤树＋parentOrgId=2 显示「未知」，新函数消费 parentOrgName=市场部 显示「市场部」 | 详情可见/不可见父级两面单测；PG 状态过滤后响应父名；前端响应字段与真实根/不可见标签 |

旧 orgTree.spec 的递归查找案例随生产查找路径退役，改由响应字段显示、空父标识、缺失名称案例承接；不再维护零调用的递归 helper。PositionTab 的完整路径追溯仍有真实消费者，保留该逻辑，仅在树中无路径时使用服务端直接父名。未修改组织树本身的根/状态/类型过滤定案。

代码轨：可见范围仍先于构树，展示根不篡改关系；父名只复用已返回导航节点或经既有 VIEW 判定的父级，批量读取不逐行查库。文档轨：契约 §8/§10.5/§12.1、选择器调用、DTO/前端类型一致；不扩组织树过滤语义，无新通用框架或未决取舍。
