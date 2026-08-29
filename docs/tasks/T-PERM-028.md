---
doc_type: task
id: T-PERM-028
title: 3.1 资源+操作定义后端——业务键切换/bigint 字符串线格式/extraClear/VIEW 门禁补齐/类型联动预置（resource-entity + operation-permission）
status: done
plan: docs/plans/frontend-phase2-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§5.3
  - docs/design/frontend/resource-operation.md#§8
  - docs/design/frontend/type-definition.md#§8
  - docs/design/frontend/service-interface-mapping.md#§7.6
depends_on:
  - T-FE-008
blocks: []
acceptance:
  - "业务键切换：resource-entity detail/update/move/remove 切 (resourceTypeCode, code, codeType)（codeType 缺省归一 default）；operation-permission detail/update/remove 切 (resourceTypeCode[可空=全局操作], code)。混合形态（2026-08-29 用户决策）：detail/update 键字段平铺、move 嵌套 {resource, parent|null}、remove {items:[键]}。不保留内部 id 兼容（未上线先例 T-PERM-034）"
  - "update 收窄：resource 侧 code 可更新字段删除（业务键不可变，mock 早已拒绝）；operation 侧 code/type 定位键不可更新"
  - "extraClear 显式清空：ResourceUpdateReq 增 boolean extraClear，true 时优先于 extra 将 extra 置 null（JSON null 无法区分「未传」与「清空」）；置 null 经 UpdateEntity 强制写列（BaseMapper.update 默认忽略 null 字段）"
  - "move 校验补齐：跨资源类型 / 目标父为自身或子孙 → 20053 RESOURCE_PARENT_INVALID（新码，一类两因 message 区分；原内部 id 实现完全缺失两项校验，成环会使树构建不收敛）；移到顶层 parentId 置 null 同样走 UpdateEntity"
  - "detail 查不到语义收紧：data:null 宽松形态删除，统一抛 20004/20005（对齐同域 update/move 先例）"
  - "VIEW 门禁三处补齐（2026-08-29 用户决策全补）：resource-entity/list、resource-entity/detail 补类型级 RESOURCE:VIEW，operation-permission/detail 补类型级 OPERATION:VIEW（tree 与 operation list 门禁 T-PERM-042 已有；种子由 DDL CRUD 预置组覆盖——§8 第 3 项 2026-08-28 核实注记成立）"
  - "bigint 十进制字符串线格式：OperationPermissionResp.binaryBit/inheritMask 加 @JsonSerialize(ToStringSerializer)（全项目 bigint 序列化策略首例，project-rules §7.4 定策略）；请求侧 Long 组件 Jackson 宽容接受字符串；前端线格式全切 string，显示/运算/排序全 BigInt，表单保留 el-input-number 提交转字符串（2026-08-29 用户决策）"
  - "resource_type 创建联动预置（2026-08-29 用户决策实现）：TypeDefinitionAppServiceImpl.createType 在 typeKey=resource_type 时同事务 INSERT 四条 CREATE(1,0)/VIEW(2,0)/UPDATE(4,2)/DELETE(8,2)（DDL 预置组模板同款，新类型位段空闲无 uk 冲突；跨域写入先例 ServiceConfig 级联 T-PERM-027）"
  - "T-PERM-027 §7.6 资源选择器联动落地（2026-08-29 用户决策实现）：MappingForm 裸「资源实体 ID」数字输入替换为「资源类型下拉 + el-tree-select 资源树选择」，选中取节点内部 id 提交（映射 API 仍 resourceId，api-contract §5.4 定案），数据源 resource-entity/tree"
  - "XML 参数名既有缺陷修复：OperationPermissionMapper.selectByResourceTypeAndCode 的 #{operationCode} → #{code}（mapper 参数名不一致，此前无真实调用方从未暴露，PgIT 抓出）"
  - "前端与 mock 对齐：api 类型与函数签名切业务键/字符串位值；hook/表单/移动表单改造；bit-ops 类型放宽 string；type-def mock 接 presetOperationsForType 联动"
  - "design_writeback：api-contract §5.3 业务键定稿块 + 位字段口径 + 联调门禁收口 + §5.4 选择器登记收口、resource-operation.md §5/§8、type-definition.md §8 第 4 项、service-interface-mapping.md §7.6、project-rules §7.4 bigint 策略、看板行 ✅"
design_writeback:
  required: true
  status: done
last_updated: 2026-08-30
---

# T-PERM-028 3.1 资源+操作定义后端——业务键切换/bigint 字符串线格式/extraClear/VIEW 门禁补齐/类型联动预置

> 状态：done（2026-08-29 收口）
> 依赖：T-FE-008（前端资源与操作定义页 + 🔧 清单登记）
> 归属：frontend-phase2（3.1 资源+操作定义后端）

## 背景

T-FE-008 前端资源与操作定义页在 API 核对中登记 6 项 🔧（resource-operation.md §8）：detail/update/move/remove 用内部 id（应切业务键）、VIEW 门禁种子疑缺、bigint 63 位精度、extra 无法清空（后并入本卡范围）、resource_type 联动预置无实现（T-PERM-023 §8 第 4 条改归属）；另含 T-PERM-027 §7.6 手工映射资源选择器联动登记。

## 设计定案（2026-08-29，五项均经用户决策）

1. **键形态混合**：detail/update 键字段平铺请求体顶层；move 嵌套 {resource, parent|null}（parent null=顶层，可空嵌套对象语义清晰）；remove {items:[键]}。operation 侧 resourceTypeCode 可空=全局操作。
2. **VIEW 门禁三处全补**：list/detail 与 tree/listOperations 同口径类型级；bootstrap §14.4 最小集已持有，不阻断首管理员（§14.5 终态表当时只列授权页 3 接口，本批补齐缺口）。
3. **选择器 + 预置两项都做**：资源选择器属本卡登记范围（T-PERM-027 原文「随 T-PERM-028 一并落地」）；类型联动预置按 DDL 模板实现。
4. **位输入线 string + 数值控件**：传输/显示/运算全链 string+BigInt 无损；表单保留 el-input-number（2^53 内输入精确，业务位值均在低段），提交转字符串。

## 范围与实现

- DTO：ResourceKeyReq（+normalizedCodeType 归一 default）/ResourceKeysReq/OperationKeyReq（+isGlobal）/OperationKeysReq 新增；ResourceUpdateReq（键平铺+extraClear）/ResourceMoveReq（嵌套）/OperationUpdateReq（键平铺）重写；OperationPermissionResp 位字段加 ToStringSerializer。
- 服务与实现：getResource/getOperation 业务键 + 类型级 VIEW 门禁 + 404 抛错；updateResource/moveResource/updateOperation 业务键定位；deleteResources/deleteOperations 分组批量键解析（resource 按 resourceTypeCode 分组复用 selectByTypeAndCodesAndCodeTypes + (code,codeType) 对过滤；operation 专属/全局两轨）；move 跨类型+防环 20053；listResources/countResources 补 VIEW 门禁。
- 强制置空：UpdateEntity.of 显式更新列（extraClear 与 move 顶层两处；BaseMapper.update 忽略 null 字段，PgIT 抓出后从 UpdateChain 改用 UpdateEntity——mock mapper 无法解析实体类）。
- TypeDefinition 预置：createType 尾部 typeKey=resource_type 分支同事务插四条操作位。
- 测试：ResourceManageAppServiceImplTest 8→17（门禁/键定位/extraClear/move 三态/create codeType 归一）；OperationAppServiceImplTest 4→11（detail 门禁/专属全局轨/键批删/多类型稀疏组合防笛卡尔误删）；TypeDefinitionAppServiceImplTest 12→14（预置正反）；HttpApiPathSnapshotTest 快照 4 处 DTO 名；新增 OperationPermissionWireFormatTest（2^62 位值字符串断言 + 请求宽容解析）与 ResourceOperationKeyPgIT（3 用例真库：资源键全链路含级联/操作键两轨/预置模板）。
- 前端：api/resource-operation.ts 重写（键类型/字符串位值）；hook 业务键提交与 BigInt 排序；ResourceMoveForm 键化；OperationForm 编辑态 string→number；index.vue detail 键定位 + effectiveBits 字符串化；bit-ops 签名放宽 string；mock 全对齐（键定位/extraClear/20053/字符串位值/type-def 联动）；MappingForm 资源选择器。

## 已知限制

- **operation 更新位值不预检 uk_typed_bit 冲突**：靠 DB 唯一索引兜底（create 同现状），冲突时报系统错误码——位值管理页低频操作，维持现状不加预检（避免过度设计）。
- **remove 未命中键静默跳过**：对齐原 ids 批删语义（不部分失败）；批量调用方以返回行数/事后查询核对。
- **表单数值控件输入上限 2^53**：bit 54~63 无法经表单直接输入（可经 API 写入）；超精度高位值在编辑表单中只读精确展示（数值控件往返丢精度——复评 P1 收口：脏检查提交 + 只读字符串展示，只改名称等编辑不再改写位字段）。
- **resource_entity move 并发成环窗口（登记 T-PERM-044 扩入，2026-08-30 用户决策）**：moveResource 防环校验为 check-then-act 无锁，并发交叉移动（A→B 同时 B→A）可双双通过校验成环；子孙查询同 UNION ALL 递归 CTE，与三棵树后果同构，随 T-PERM-044 四树统一加固（行锁/advisory lock 或 UNION 去重二选一）。
- **操作定义缓存失效缺口（既有，登记 T-PERM-047）**：OPERATION_PERMISSIONS_BY_TYPE（L1 60m/L2 120m）在 create/update/deleteOperation 后无 evict，引擎最长 1-2 小时按旧位值判定；目录注释承诺的「跨实例 L1 失效广播」写路径从未接线（旧 id 版同样缺失，非本批引入）。2026-08-29 双轨评审 P2 登记，用户决策立独立任务（evictAfterCommit 接线，键粒度需按 type 维度定夺）。

## 验收对照

- design_refs：api-contract §5.3 业务键定稿块 + 位字段口径 + 联调门禁收口 + §5.4 选择器登记收口；resource-operation.md §5 表 ✅ 化 + §8 六项全收口；type-definition.md §8 第 4 项 ✅；service-interface-mapping.md §7.6 ✅；project-rules §7.4 bigint 序列化策略。
- 测试：后端受影响单测 47 项（17+11+14+3+2）+ 快照 7 项 + WireFormat 2 项（合计 56）+ PgIT 3 项全绿；前端 vue-tsc 干净 + vitest 216 项全绿 + 变更文件 eslint 干净。
- 回归：access-service mvn test 全量绿；git diff --check 干净。

## 完成记录

- 2026-08-29 收口：五项用户决策（键形态/门禁三补/选择器+预置都做/线 string+数值控件/[update 删 code 字段等修法唯一项直接修]）全落地；执行中发现并修复两处既有缺陷（selectByResourceTypeAndCode XML 参数名错配、BaseMapper.update 忽略 null 致 extraClear/移顶层失效——后者原实现同病，均由 PgIT 真库暴露）；move 防环/跨类型校验为原实现缺失项（mock 与前端设计早有，属事实性补齐）。
- 2026-08-30 外部复评二轮收口（1P1+1P3）：P1 上一轮批量解析重构引入的笛卡尔二元组误删（typeCode→codes 分组被过早拍平，请求 ROLE:VIEW+USER:DELETE 会连带软删 ROLE:DELETE/USER:VIEW；资源侧三元组保留分组无此问题）——恢复分组构造精确二元组 + 双类型稀疏组合回归锁（旧实现下 size 4≠2 失败）；P3 T-PERM-044 扩四树后残留「三棵树」表述五处收口（044 卡标题/正文三处、role-manage/api-contract 括注、045/046 交叉引用）。
- 2026-08-30 外部复评收口（1P1+3P2 全属实全处置）：P1 编辑高位操作权限静默改写位字段（Number 往返 2^62→4611686018427388000 且 edit 无条件重提交）——hook 位字段脏检查（null=不更新）+ OperationForm 超精度值只读字符串展示（安全值仍数值控件）；P2 批量业务键解析按类型循环查询——两处改「batchResolveTypeValues 一次 + 跨类型单查 + 复合键内存过滤」（操作侧复用既有 selectByTenantResourceTypesAndOpCodes，资源侧新增镜像 selectByTypesAndCodesAndCodeTypes），测试适配并补超集行不混入断言；P2 MappingForm 资源树异步竞态——请求序号守卫丢弃过期响应；P2 resource_entity move 并发成环窗口——用户决策扩入 T-PERM-044 四树统一加固（任务卡已知限制同步登记）。
- 2026-08-29 双轨评审收口（代码轨+文档轨，逐条核实后处置）：P2①create 侧 codeType 归一对称修复（带空白 codeType 的行此前创建后无法经业务键寻址；mock 同步 trim 归一，回归锁用例）+缓存失效缺口登记 T-PERM-047（用户决策立独立任务）；文档轨 P1：权威 DDL 预置注释「当前应用亦无该生成逻辑」已被本实现证伪，删改；P2：api-contract last_reviewed 编辑事故重复段去重、type-definition.md last_reviewed 日期未升级修正；P3：WireFormat 请求用例改真绑定 DTO（原 readTree+asLong 恒真）、BigInt 排序比较器补相等 0（mock 全量口径跨类型同位值稳定序）、hasBit 非十进制字符串宽容守卫、mock remove data 形状对齐 PermResult<Void>（null）、预置 4 条改 insertBatch 对齐仓库先例、任务卡计数表述与 §14.5 措辞收窄、resource-operation §8 小节标题收口态、AGENTS.md 待做补列 040/041。拒绝项：update/move 先键解析后门禁（既有次序+写操作语义，错误差异探测面有限）、本页补 spec（Phase 1 起零覆盖，独立测试债非本批回归面）。
