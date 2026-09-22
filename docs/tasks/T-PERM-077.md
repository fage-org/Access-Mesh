---
doc_type: task
id: T-PERM-077
title: "操作继承掩码缺省值契约对齐"
status: done
plan: docs/plans/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#operation-default
  - docs/design/access-service-api-contract.md
  - docs/design/schema/access-service.sql
  - docs/design/extension-guide.md
depends_on:
  []
blocks: []
acceptance:
  - "自定义类型新增EXPORT位16，省略inheritMask与显式0均成功且结果一致，非法掩码仍拒绝。"
  - "PgIT实际INSERT不再写非法NULL，同事务授权根补种并可首次转授，失败不留半成品。"
  - "采用唯一入口缺省归一化，不扩大为全仓ORM默认策略改造。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-22
---

# T-PERM-077 操作继承掩码缺省值契约对齐

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F013；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- OperationCreateReq到OperationAppServiceImpl及Mapper的inheritMask缺省处理、调用方与授权根联动。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#operation-default)。§2.6 已转已实施（2026-09-22），实施口径与拍板见该节。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

**验收③条「非法掩码仍拒绝」落地口径（2026-09-22 用户拍板，registry 同日行）**：掩码语义看位不看正负（Java Long 有符号只是表示形态），**不做符号校验**、不新增数值校验——「仍拒绝」按既有拒绝面锁定（`binaryBit` @NotNull 400、code/资源类型码大写 @Pattern 400、同类型同码/同位唯一索引拒绝），掩码含哪些位属管理员定义自由度。

## 完成记录（2026-09-22）

- **核实**：F013 实证——`OperationAppServiceImpl.createOperation` 对可选 `inheritMask` 直写 null，flex insert 全列显式 NULL 覆盖 DDL `NOT NULL DEFAULT 0`，省略掩码的合法请求以 NOT NULL 违例失败（信封 99999）；同请求显式 0 成功。数值级校验三层（DTO/服务层/DDL CHECK）现状全无——设计稿「保留传入非法位值的验证」无可保留对象，按拍板不新增（见验收对照口径）。
- **实现**：唯一创建入口 `createOperation` 归一 `null→0L`（响应回读归一值）；同款调用方核对——类型创建 CRUD 预置种子（`TypeDefinitionAppServiceImpl`）与 DDL 种子均显式传掩码、update 通道 `inheritMask` null=不更新语义维持，均不受影响；无 ORM 策略层改动。
- **回归锁**：`OperationAppServiceImplTest` 归一锁（省略与显式 0 落库实体/响应等价）旧实现下实证红（实体 null → 响应 NPE）；`CustomResourceTypeSlicePgIT` 主链 EXPORT 改省略掩码——真 INSERT 落 0 + 同事务补种 + 首次转授全链路，旧实现下实证红（信封 99999）；锁⑩补种失败（所有者停用 20003）整单回滚零残留（操作行零残留、种子仍 CRUD 四位）；`OperationCodeCaseValidationTest` 补 binaryBit 必填拒绝面锁（掩码归一只放开 inheritMask 省略）。
- **用户拍板（当轮 registry 登记）**：掩码值不做符号校验（看位不看正负）——验收「非法掩码仍拒绝」按既有拒绝面锁定，不新增数值/位域子集校验。
- **设计回写**：iam-task-closure §2.6 转已实施+实施口径；契约 §12.1 补「操作创建掩码缺省归一」段与 frontmatter；extension-guide §3.5 追加操作条目补缺省口径；CHANGELOG [Unreleased] Fixed；registry 2026-09-22 收口行；schema 无变更（`DEFAULT 0` 语义即归一目标，零回写面）；plan/看板状态随收口同步。
- **双轨评审处置（代码轨 P0-P3=0、文档轨 P2×1+P3×3）**：文档轨 P2-1——iam-task-closure §2.6 原稿「保留传入非法位值的验证」与拍板矛盾（无可保留对象）→ 改写为经核实口径；P3-1——iam-task-closure/extension-guide 两册 frontmatter last_reviewed 漏追加本批条目 → 补齐；P3-2——完成记录「plan/看板状态同步」超前声称 → 改「随收口同步」；P3-3——「用户拍板」句式与 T-PERM-076 收口卡同款（该卡经双轨+外评保留），沿先例维持现状。
- **claude 外评处置（deepseek-flash[1M]，2026-09-22；P0-P1=0、P2×1+P3×2、过度设计=0，代码面零缺陷）**：P2——卡转 review 时看板/计划快照未同步（生命周期 skill L182 无推迟例外）→ 属实直修（两处转 👀 随收口转 ✅）；P3-1——OperationController create javadoc「需指定…和继承掩码」与新行为相反 + PgIT 类 javadoc 锁清单漏锁⑩ → 直修（类推扫描确认全仓唯一必填表述残留）；存量观察② OperationUpdateReq javadoc 补「null=不更新」一并修；P3-2——任务卡背景节「任务尚未实施」句与实施态自相矛盾 → 沿建卡模板惯例维持（T-PERM-076 已 done 卡同款句，批量清理属独立清扫批次，未立项）。
- **收口验证**：全量回归（收口形态 `-T 1C` 含 E2E/heavy）全模块 SUCCESS、0 失败 0 错误 0 跳过；专项清单六项外评全过（插入点穷举归一唯一漏点/更新语义未动/回滚链路成立/双轨红锁实证/既有面零改动/文档一致）。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。
