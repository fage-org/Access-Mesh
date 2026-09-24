---
doc_type: task
id: T-API-004
title: "可编辑字段显式清空协议贯通"
status: done
plan: docs/archive/2026-09-24/iam-task-closure-plan.md
domain: cross-service
design_refs:
  - docs/design/iam-task-closure.md#clear-fields
  - docs/design/access-service-api-contract.md
  - docs/design/org-user-permission-contract.md
  - docs/design/frontend/type-definition.md
  - docs/design/frontend/service-interface-mapping.md
  - docs/design/project-rules.md
depends_on:
  []
blocks:
  - T-ACCESS-055
acceptance:
  - "非空→显式清空→刷新仍空，未传/null保持原值，设置新值成功；各目标字段按矩阵有适用测试。"
  - "U006明确值+clear冲突、false以及type extra服务端键边界，清空不能破坏所有权/授权根。"
  - "JSONB/唯一手机号等真实数据库行为有PgIT证据；已有正常extraClear消费者不回退。"
  - "接口总册为字段唯一详细来源，前后端/SDK涉及面锁步；外部消费者迁移依实际盘点决定。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-24   # 收口：双轨处置完毕+全量含 E2E BUILD SUCCESS（11 模块 0 失败）
---

# T-API-004 可编辑字段显式清空协议贯通

## 背景

承接[评审证据](../../../archive/2026-09-20/comprehensive-review.md)的 F009；基线与静态/动态证据强度见该记录。实施与验证见完成记录。

## 范围

- 用户phone/email，类型description/extra，服务basePath/description/extra，接口映射extra的写读矩阵。
- DTO xxxClear、领域值冲突规则、强制NULL写入与前端构造同批同步，类推相同模板。

## 当前口径

方案唯一入口：[IAM闭环方案](../../../design/iam-task-closure.md#clear-fields)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：U006：新值与Clear并存推荐拒绝；type extra可清部分必须先界定。零消费者优先同批切换，有真实外部消费者才决定窄兼容。

## 实施记录（2026-09-24）

**U006 四项拍板（AskUserQuestion，全按推荐，定案见 decision-registry 2026-09-24 行）**：①新值与 Clear 同传**全端点 400 拒绝**（含 role/resource 既有两域同批对齐——旧「Clear 优先于 extra」静默丢值口径退役；前端公式从不同传真值+true；既有测试未锁 HTTP 同传形态——UserRoleWriteProjectionPgIT 一处服务层直调锁旧优先级防御分支，处置=保留断言改注为防御行为锁，零消费方回退）；②type extra **拒绝清空**（服务层对任何非 null `extraClear` 20044〔PERM_INVALID_PARAM〕拒绝；实施机制为显式占位字段+服务层拒绝（初判「严格 Jackson 未开」有误，claude 复核轮订正后用户拍板保留占位字段——价值=专门错误信息）），service extra 清空=撤销 syncTypes 同步白名单（fail-closed、可逆），mapping/perm 轨 abstract-user extra 清为 NULL；③目标字段（phone/email/description/basePath/extra）**空串一律拒绝 400**（`@Pattern("(?s)(?U).*\S.*")`——全串匹配语义，裸 `\S` 只匹配单字符串）；④范围=**六字段+perm 轨 abstract-user extra**；同型未实施字段（condition description、menu path/icon、OAuth2 client 族、org orgName〔Q-018 独立登记〕）登记 Q-043 沿 Q-015 先例不实施。

**后端**：七 DTO（UserUpdateReq/AbstractUserUpdateReq/TypeUpdateReq/ServiceConfigReq/ApiMappingUpdateReq 加字段+`@AssertTrue` 冲突锁+空白拒绝；RoleUpdateReq 与 perm-common ResourceUpdateReq 仅加冲突锁）+五服务实现（UpdateEntity 显式 NULL 列写入；type extraClear 拒绝；service 创建分支拒 clear；user phone 清空跳过唯一性检查）；perm-common 先 install 再编译（SNAPSHOT 纪律）。位置构造器消费方 60 处补尾参（脚本断言元数后写盘）。

**前端**：四页表单清空公式（原值非 null 且表单清空→`xxxClear=true`，role/resource 先例公式）——MemberTab（phone/email）、type-def（description；extra 原值非空且清空时提交前拦截提示）、service（basePath/description/extra 三字段，original 随行传入）、mapping（extra）；三 api 类型扩展。

**验证**：`ClearFieldProtocolValidationTest`（冲突×7/空白/单用放行/false 正交——旧实现下不编译或必红）+`UserWriteAppServiceUpdateGateTest` +2（清空跳过唯一性检查+实体置 null）+`ExplicitClearFieldsPgIT` 5/5 真库（UpdateEntity NULL 列写入/多用户 phone=NULL 共存不撞 uk_user_phone 部分唯一索引/type extraClear 与 service create 拒绝/JSONB NULL）+**红跑实证**（服务实现回退 HEAD：PgIT 5/5 红+单测 1 红，临时备份恢复）；前端 hook.spec +4（载荷断言含 xxxClear）+vitest 447/typecheck/lint/build 全绿。

**回写**：契约总册新增 §2.7 统一协议章+§7.4/§7.8/§10.3/§12.1/§12.2/§13.1 六端点章节+frontmatter；project-rules 新增 §7.6；iam-task-closure §4.3 转已实施；org-user-permission-contract §4.B；frontend 两册；CHANGELOG [Unreleased] Changed；Q-043 登记。

**claude 外评处置（2026-09-24，deepseek-flash[1M] headless plan，commit 16ec2a129 后；P0-P1=0、P2×1、P3×3、可裁剪×1；四条专项清单逐条执行，双轨上轮处置复核未发现新缺陷）**：**P2 派生 @AssertTrue is-getter 泄露线格式**——Jackson record 命名策略把 is-getter 识别为序列化属性（`"extraConflictFree":true` 随 Java SDK 序列化带出），服务端严格 mapper（`cacheObjectMapper` 裸 ObjectMapper，未知字段天然 400——ResourceSortOrderRetiredTest 在册锁）反序列化即拒，SDK `resource-entity/update` 对 Java 接入方恒 400（仓内前端手写 JSON+测试不经序列化掩盖；存量同根 AuthCheckReq/BatchAuthCheckReq 同款）→ 九张 DTO is-getter 全部加 `@JsonIgnore`（OperationPermission.getEffectiveBits 先例）+ 通用不变量锁「线格式键集==组件集+严格往返」（红跑实证：无 @JsonIgnore 下 2 红）；**P3 Unicode 空白绕过**——Java `\s` 默认 ASCII-only，全角空格/NBSP 视为非空白放行 → 正则补 `(?U)`（八处+文档四处）；**P3 save 预判存在性 oracle**——anyClearFlag 预读库判定在门禁前构成 serviceCode 存在性探测面（20044 vs 403 可区分），与 T-ADMIN-029「先门禁后存在性」冲突；双轨上轮 P3-3 的「上移」处置被推翻（T-PERM-067 值域先于门禁仅适用不触库的纯参数校验）→ 回移门禁后创建分支内并注释裁决；**P3 定案事实句错误**——「核实未开严格 Jackson」有误（严格 mapper 生效实证）→ registry/iam-task-closure/任务卡/代码注释四处订正；**可裁剪×1 用户拍板保留**——TypeUpdateReq.extraClear 占位字段（严格 mapper 下不加字段亦天然 400，保留价值=20044 专门错误信息可读性）；**存量同根同批修拍板**——AuthCheckReq/BatchAuthCheckReq is-getter 随批补 @JsonIgnore。验证：ClearFieldProtocolValidationTest 8/8（两把新锁红→绿）+ServiceConfigAppServiceImplTest 14/14+PermCommonReqContractTest 绿；全量含 E2E 重跑 BUILD SUCCESS。

**双轨评审处置（2026-09-24，代码轨 P1×1+P2×1+P3×3、文档轨 P1×2+P2×2+P3×2，逐条亲核全属实全处置）**：**P1 perm 轨门禁漏计 extraClear**（UserManageAppServiceImpl UPDATE 档条件不含 extraClear——DTO 冲突锁使 extraClear-only 请求 extra 必 null，整条门禁被跳过，无 UPDATE 权限者可清空他人 extra）→ 条件补 `|| extraClear` + 门禁锁 shouldRejectUpdateUserExtraClearOnlyWhenUpdateDenied（回退修复红跑实证 1 红）；**P1 错误码漂移**（文档八处 20004，实现=PERM_INVALID_PARAM 20044；20004 系 RESOURCE_NOT_FOUND）→ 文档全量改 20044；**P2 UserRoleWriteProjectionPgIT 同传段**（锁旧「Clear 优先」服务层防御行为+三处「既有测试只锁单用形态」失实句）→ 保留断言改注为服务层防御行为锁（DTO 只锁 HTTP 面，编程式直调不经 @Valid）+三处描述校准；**P2 §2.7 空白拒绝字面超范围**（role/resource extra 无空白校验）→ 补限定句「空白拒绝范围=六字段+perm 轨 extra，role/resource 仅对齐冲突拒绝随 Q-043 收敛」；**P3 五项直修**——前端 role-manage/resource-operation 两处旧「优先于 extra」注释、ClearFieldProtocolValidationTest 补 AbstractUserUpdateReq 冲突/单用/空白用例、service create 拒 clear 上移门禁前（T-PERM-067 值域先于门禁先例；anyClearFlag 显式括号防 && 优先级误拒更新分支）、三册 last_reviewed 补 2026-09-24、任务卡恢复非目标节挂 Q-043。全量回归首跑 1 失败=CustomResourceTypeSlicePgIT 直连载荷 `basePath:""` 被新空白校验拒（收紧按拍板生效）→ 载荷改省略。

## 非目标 / 遗留

本卡只覆盖 U006 拍板范围（六字段+perm 轨 abstract-user extra），不自动扩展相邻字段能力：condition description、menu path/icon、OAuth2 client 字段族同型未实施，org orgName 见 Q-018——统一登记 [Q-043](../pending-problems.md)，沿 Q-015 先例随下次触达对应域的任务按 §2.7 模板收敛；biz-domain description「空串=清空」历史形态维持不动。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

- ①非空→清空→真库仍 NULL、null/未传保持原值、设新值成功：ExplicitClearFieldsPgIT 五用例覆盖七端点目标字段（admin 轨 phone/email、perm 轨 extra、type description、service 三字段、mapping extra）；设新值维持既有单测（UserWriteAppServiceUpdateGateTest settingNewPhone…、TypeDefinitionAppServiceImplTest 等）。
- ②U006 冲突/false/type extra 边界：ClearFieldProtocolValidationTest 冲突×7 端点+false 单用放行+type extraClear 服务层拒绝（PgIT+单测）；所有权/授权根不受清空影响（type extra 不提供清空=20056/grantOriginRole 守卫零触达）。
- ③PgIT 真库证据：JSONB NULL（abstract-user/service/mapping extra）、uk_user_phone 部分唯一索引多 NULL 共见；已有 role/resource extraClear 单用消费零回退（RoleManageAppServiceImplTest/ResourceManageAppServiceImplTest 既有单用形态用例全绿——以 surefire 报告为准）。
- ④契约总册 §2.7+六端点章节为唯一字段详细来源；前端 api 类型/表单、SDK（perm-common ResourceUpdateReq 冲突锁）同批锁步；外部消费者盘点=前端公式从不同传真值+true、无真实外部调用方，零兼容层同批切换。
