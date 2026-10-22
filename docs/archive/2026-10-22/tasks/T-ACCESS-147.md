---
doc_type: task
id: T-ACCESS-147
title: 资源关系发布与候选查询及 SDK 接线
status: done
plan: docs/archive/2026-10-22/resource-association-plan.md
domain: cross-service
design_refs:
  - docs/design/resource-association.md#ownership
  - docs/design/resource-association.md#publication
  - docs/design/resource-association.md#ui
  - docs/design/access-service-api-contract.md
  - docs/design/service-authentication.md
  - docs/design/extension-guide.md
depends_on:
  - T-ACCESS-146
acceptance:
  - "业务服务可建立专属归属、维护合法主资源的通用引用；不能修改 MANAGED 实例或他方主资源关系，跨租户与伪造 sourceService 负向通过。"
  - "重复/乱序/依赖缺失/批量失败和 FULL 缺失清理符合正式契约，清理不会误删通用实例或其他服务关系。"
  - "候选查询同时遵守 SUB_PERM、专属实例归属与通用整类引用；A/B 同名字段分开，已引用通用类型的全部有效实例均可作为候选且可单独授权；未声明类型不改变既有可见性。"
  - "主资源不存在、无关系、无权限与读取失败区分；树展开及分页/批量预算不突破关联过滤。"
  - "perm-common / client / 必要 registration 消费点、接口注册和 DTO 对账同步；上游 API 变更先 install 刷新 SNAPSHOT 再验证下游编译。"
  - "契约、服务认证和接入指南回写；未启动新凭证体系或新的业务网页。"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-22
---

## 背景

专属资源来自业务系统，通用实例来自 IAM，业务系统只能维护自己主资源的引用；现有资源同步不可被放宽来绕过 MANAGED 维护权。

## 范围

接入专属归属发布、通用关系维护、按主资源查询候选与引用来源；补契约 DTO、SDK、服务身份/接口登记和必要管理只读响应。增量/FULL 复用定稿的现有发布机制，避免新增平行基础设施。

## 当前口径

实例变更和关系变更权限分离；可信租户/服务身份来自现有认证，关系调用不授予角色权限。通用引用以整个类型为目标，不接受实例子集；专属归属以实例为目标。接口必须按主资源所属来源限定，单/FULL 清理不越权。

详细规则以[资源关系设计](../../../design/resource-association.md)及上列 design_refs 为准。候选/只读查询、SDK 与发布接入已实现，定向验证与本地双轨核对通过；页面组件与运行时判定仍由对应下游任务完成。

## 验收对照

- [x] 业务服务可建立专属归属、维护合法主资源的通用引用；不能修改 MANAGED 实例或他方主资源关系，跨租户与伪造 sourceService 负向通过。
- [x] 重复/乱序/依赖缺失/批量失败和 FULL 缺失清理符合正式契约，清理不会误删通用实例或其他服务关系。
- [x] 候选查询同时遵守 SUB_PERM、专属实例归属与通用整类引用；A/B 同名字段分开，已引用通用类型的全部有效实例均可作为候选且可单独授权；未声明类型不改变既有可见性。
- [x] 主资源不存在、无关系、无权限与读取失败区分；树展开及分页/批量预算不突破关联过滤。
- [x] perm-common / client / 必要 registration 消费点、接口注册和 DTO 对账同步；上游 API 变更先 install 刷新 SNAPSHOT 再验证下游编译。
- [x] 契约、服务认证和接入指南回写；未启动新凭证体系或新的业务网页。

验证层：复用同步协议、服务认证及 API 测试；版本/事务落库用 PG，SDK 字段/序列化和真实下游构建验证适配。

## 非目标 / 遗留

不向普通管理员开放业务关系编辑，不放开资源实例同步所有权；权限引擎与 UI 消费分别由 T-ACCESS-148/T-FE-080 承接。

## 完成记录

写侧与来源协议沿用 T-ACCESS-146 的实际证据，不重复建立发布设施。候选按 SUB_PERM、关系与可见集合先过滤再分页；类型只读查询支持实际主资源与父 ALL 两类上下文。管理 API、SDK 单源请求、显式凭证重载、接口登记与前端字段对账已同步。验证与双轨结论见[证据](evidence/T-ACCESS-147/verification.md)。
