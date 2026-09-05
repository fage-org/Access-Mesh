---
doc_type: task
id: T-PERM-053
title: service-config 同步 ApiItem.operationCode 无效字段删除（前后端同批锁步）
status: done
plan: docs/plans/design-audit-followup-plan.md
domain: permission-center
design_refs:
  - docs/design/permission-center/api-contract.md#§6.3
  - docs/design/schema/access-service.sql
depends_on: []
blocks: []
acceptance:
  - "后端删字段：ServiceConfigSyncReq.ApiItem 删除 operationCode——resource_api_mapping 表无对应列、MappingSyncHandlerImpl 全文零消费，DTO 仅 @NotBlank+格式校验后静默丢弃（契约性空壳）；接口权限模型=「API 资源实例 × ACCESS 单操作」，操作码不属于该模型（敏感度区分靠资源实例拆分），定案删除而非 @Pattern(\"ACCESS\") 收紧（保留恒定值僵尸参数，且传错值从静默成功变 400 同为行为变化）"
  - "前端同批清理（锁步前提，复评审核实唯一活跃调用方）：frontend 服务接口配置页——api/service-interface.ts SyncApiItem 类型删 operationCode 字段、views/system/service-interface/utils/types.ts 示例 payload（operationCode: \"VIEW\"）与 parseSyncGroups 必填校验（缺失即报错）同步删除；example-service 零调用（已核），无清理对象"
  - "兼容性口径（2026-09-05 复评审修正原「零破坏」误判）：全局 ObjectMapper 来自 common cacheObjectMapper（裸 new ObjectMapper()，未 disable FAIL_ON_UNKNOWN_PROPERTIES=严格模式，@ConditionalOnMissingBean 顶掉 Boot 宽容版，application.yml 2026-08-14 评审注释已记录）——**仍携带 operationCode 的旧请求体将 400**；前后端必须同批发布（产品内部、调用方唯一且可控），不做 mapper 放宽"
  - "契约回写：api-contract §6.3 同步请求示例删行，注明字段退役（2026-09-05）与「接口权限无操作粒度」模型口径"
  - "回归锁：锁步后前端表单不含 operationCode 可提交成功；仍含 operationCode 的请求体 400（严格 mapper 行为锁定，防 mapper 配置漂移反向依赖）"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-05
---

# T-PERM-053 service-config 同步 ApiItem.operationCode 无效字段删除（前后端同批锁步）

> 状态：done（2026-09-05 收口；proposed 于同日设计体检 P2-3 问题一，定案=删除；同日复评审修正兼容性前提并扩入前端锁步面）
> 依赖：无

## 背景

同步接口 ApiItem 的 `operationCode` 必填且格式校验，但既不落库（表无列）也不参与鉴权（运行时固定 `API:ACCESS`）——接入方合理推断「接口权限可按操作分级」，实际语义从未生效且无任何报错提示，属契约撒谎。

## 设计口径（2026-09-05 定案）

- **删除字段，前后端同批锁步**：唯一活跃调用方是自家前端服务接口配置页（后端单删会让该页同步功能立即 400）；严格 mapper（见 acceptance 第 3 条）使「未知字段忽略」不成立，靠锁步发布而非放宽 mapper 解决。
- 「这条路由只许管理员用」的正确表达 = 把敏感路由单独映射一个 API 资源实例、只给管理员角色授 ACCESS——粒度来自资源拆分，不来自操作码。
- 非 RESTful、不引入 @RequestParam（照常 POST + JSON Body）。

## 范围

- 后端 DTO 删字段 + 前端 service-interface 页同批清理 + 契约回写 + 双侧回归锁。

## 完成记录

- 2026-09-05 实施：后端 `ServiceConfigSyncReq.ApiItem` 删 operationCode（含 Javadoc 退役注记），既有测试 `MappingSyncHandlerImplTest`/`ServiceSyncAppServiceImplTest` 构造参数同步（4 处）；前端锁步清理 `api/service-interface.ts` SyncApiItem 类型、`utils/types.ts` 示例 payload 与 parseSyncGroups 必填校验、`SyncForm.vue` 帮助文案（操作码字样）；契约 §6.3 示例删行 + 退役规则条目 + last_reviewed。回归锁双侧新增：后端 `ServiceConfigSyncOperationCodeRetiredTest`（全量 Context + MockMvc + SERVICE 凭证身份，RetiredSyncTaskEndpointTest/SecurityMatrixIT 先例）——新载荷 200 且字段全量到达业务层（ArgumentCaptor 断言）、旧载荷 400 且 `verifyNoInteractions`（两用例在旧实现下均失败，锁与删除语义同向，mapper 漂移为宽容模式时负向用例失败）；前端 `views/system/service-interface/utils/types.spec.ts` 4 用例（无 operationCode 解析通过、示例 payload 不含该字段、仍含该字段时前端放行=删除而非反向校验、其余必填校验语义保持）。
- 2026-09-05 双轨评审收口批次（代码正确性与安全边界 + 规范符合性与文档一致性两轨并行只读，2 处实证残留双轨交叉命中、经代码级复核属实，四项处置用户定案全采纳）：gateway `ExampleProtectedApiE2EIT` 第③步 sync 载荷删 operationCode 残留行（T-PERM-052 迁入该通道时带入；主任务回归只跑 access-service + 前端、漏掉 gateway 容器轨，验收 2「唯一活跃调用方=管理前端」原表述漏计该测试调用方，本行即更正；同文件 apply-grant-plan recordKey 的合法 operationCode 不动）；前端设计文档 service-interface-mapping.md §3.2 同步必填清单删 operationCode + last_reviewed 注记；负向回归锁补信封码 90001 断言（`GlobalErrorCode.VALIDATION_FAILED`，400 归因钉死 HttpMessageNotReadable 通道）；acceptance 第 4 条「与字段表」措辞订正（§6.3 实无字段表）。
