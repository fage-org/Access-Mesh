---
doc_type: task
id: T-API-003
title: check 族三端点结果记录全量回传（推翻 T-API-002 check 族裁剪）
status: done
plan: docs/archive/2026-09-12/permission-query-unification-plan.md
domain: cross-service
design_refs:
  - docs/design/permission-center/api-contract.md#§6.1
  - docs/design/permission-center/api-contract.md#§6.2
  - docs/design/permission-center/api-contract.md#§6.6
  - docs/design/permission-center/core-flows.md#§15
depends_on: []
blocks: []
acceptance:
  - "三端点同口径全量回传：/auth/check、/auth/batch-check、/auth/check-interface 响应恢复结果记录（matchedRoleIds/matchedPermissionIds 与 matchedResources[].resourceId 回传线格式；PermResult 内部已持有数据面）；Query* 六字段裁剪维持不变（推翻范围仅 check 族）"
  - "双副本 DTO 同批改：access-service permission/dto/resp 与 perm-sdk/perm-common dto/resp 的 AuthCheckResp/BatchAuthCheckResp/CheckInterfaceResp 两份同形；perm-common 先 install 再编译下游（SNAPSHOT 陷阱）"
  - "回归锁改写：CheckFamilyWireShapeTest 的 RETIRED_ID_FIELDS 防回潮负向锁、containsExactly 快照、双副本同形锁按新线格式改写；PermissionFeignClientContractTest 契约快照更新"
  - "文档回写：api-contract §6.1/§6.2/§6.6 已删除字段条目改写为回传口径 + §5.7 注记更新；core-flows §15 检查点改写；decision-registry 已推翻节迁移收尾核对；T-FE-043 登记项（来源类展示走业务键）随重设计处置"
  - "e2e/gateway 断言面核对（现仅断言 allowed 信封，预期零影响）；收口回归 mvn test -T 1C"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-10
---

# T-API-003 check 族三端点结果记录全量回传

> 状态：done（2026-09-10 实施收口；2026-09-09 grill Q10 + D1 定案：推翻 T-API-002 的 check 族裁剪，三端点一并回传）
> 依赖：无（建议 T-PERM-057 之后实施以避免 check 管线二次触碰；非硬依赖）

## 背景

2026-09-06 T-API-002 定案裁剪 SDK 直连端点内部 id（check/batch-check/check-interface 的 matchedRoleIds/matchedPermissionIds 与 matchedResources[].resourceId）。2026-09-09 统一引擎定案（消费方模型：调用方根据结果记录自行判定）推翻该裁剪的 check 族部分，三端点同口径恢复全量回传；Query* 响应族六字段裁剪不在推翻范围。

## 范围

- 三端点响应 DTO（双副本）恢复结果记录字段；契约测试与负向锁按新口径改写；文档回写。
- registry 处置核对：2026-09-06 行的 check 族部分已移入「已推翻」节（2026-09-09 登记）；被取代关系的正文记载见本卡背景段（原裁剪任务卡 docs/tasks/T-API-002.md，历史完成事实不改）。本卡落地后核对新口径行为与登记一致。

## 非目标 / 遗留

- Query* 响应族（query-resources/query-scopes 等）线格式不变。
- 权限视图/排查端点（T-PERM-059 删除面）不含在内。

## 实现记录（2026-09-10 收口）

- **双副本 DTO**（六文件同形）：AuthCheckResp 于 reason 后回位 `matchedRoleIds/matchedPermissionIds`；BatchAuthCheckResp.AuthCheckItemResult 于 reason 后回位两字段；CheckInterfaceResp.MatchedResource 首位回位 `resourceId`、尾部回位两 matched 字段——组件位置与 deny/USER_NOT_FOUND 分支空列表语义均为 T-API-002 裁剪前原样（`d09513797~1` 为回线依据）。
- **组装层恢复**：PermResultUtils.toAuthCheckResp（allow 时从 PermResult 提取）、toCheckInterfaceResp（per-resource distinct 提取 + resourceId=resMap 兜底 entry.getKey()）、PermissionCheckAppServiceImpl.batchCheck 逐项填充与 USER_NOT_FOUND 空列表分支。PermResult 数据面经 T-PERM-057 收编后仍持有 matched 便捷方法与 resourceMap，零引擎改动。
- **回归锁分族改写**：CheckFamilyWireShapeTest——check 族改 containsExactly 正向快照（新线格式）、retiredIdFieldsMustNotResurface 负向锁收窄至 Query\* 五 record（RETIRED_ID_FIELDS 名单不变，check 族移出检查范围）、双副本同形锁原样适配；PermissionFeignClientContractTest check 族快照更新；PermissionCheckAppServiceImplTest 补 matched 填充真锁（check/batchCheck/checkInterface 三处，裁剪态下断言失败）。
- **消费面核对**：Gateway fallbackCheckInterface/PermissionClient 只读 allowed/reason/matchedResources.size()（size 与条目内新增字段正交）、e2e 模块零引用 check 族 DTO、前端 check 族零消费（permission-query.ts 的 matchedPermissionIds 属 permission-view/explain，T-PERM-059 面）——恢复字段零影响实证。
- **文档回写**：api-contract §5.7 SDK 入口清单注记改分族口径 + §6.1/§6.2 响应示例与注记改写 + frontmatter；core-flows §10 目标句（入参不依赖/响应面分族）+ §15 SDK 可接入行 + frontmatter；T-FE-043 契约面登记句更新；registry 实施定案行登记（2026-09-10）。注：验收条目/design_refs 预设的 §6.6（query-resources）经核对无需改动——该节是 Query\* 语境，T-API-002 裁剪注记维持原样即为定案要求（§6.7 query-scopes 同）。
- **验证**：perm-common 先 install（SNAPSHOT 陷阱）；access-service 单测轨道 1119 全绿；收口全量回归 mvn test -T 1C 含 E2E。
- **评审处置定案**：双轨评审 P0-P2 代码问题零；MatchedResource.resourceTypeCode 恒 null 存量漂移（裁剪前已存在、契约示例展示 "API"）用户拍板维持现状、随 T-PERM-059 一并处置（registry 2026-09-10 T-API-003 行登记）；plan 快照同步/任务卡两处补注/Query\* 两 DTO javadoc §15 引用句对齐四项事实性修正已落地。

