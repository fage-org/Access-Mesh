---
doc_type: task
id: T-ACCESS-132
title: 租户与管理台使用验收
status: in-progress
plan: docs/plans/candidate-deployment-acceptance-plan.md
domain: cross-service
design_refs:
  - docs/design/tenant-lifecycle.md
  - docs/design/browser-session-authentication.md
  - docs/design/frontend/file-management.md
depends_on:
  - T-ACCESS-131
acceptance:
  - 正常平台运营入口开通隔离租户并完成客户首次登录改密
  - 浏览器完成组织用户角色和文件使用链且权限反例成立
  - 租户与平台会话边界、刷新直达及审计取证符合现行契约
  - 问题与证据可追溯且设计和使用文档反映实际交付范围
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-18
---

## 背景

空库健康不等于租户可以使用。以 T-ACCESS-131 的正式部署为入口，复用[租户运营手册](../ops/tenant-operations.md)、[登录设计](../design/frontend/login.md)与已有文件验收资产，验证当前版本的用户可达链路。

## 范围

经平台运营页面开通两个验收租户，记录实际 tenantId/tenantCode，禁止假定租户 1 或跨租户复用账号。完成首管理员初始密码交付、首次改密、重新登录；凭据不进入截图、日志摘要或版本库。通过正常页面创建默认树普通组织、普通用户和 BASIC_ROLE 并绑定，为后续接入准备独立身份。

在浏览器中走管理台登录、菜单导航、刷新、直达 URL、退出和重新登录；核对 HttpOnly/CSRF 接线、租户与平台身份隔离及同一浏览器跨标签会话切换，复用现有行为，不另开会话改造。平台停用一个验收租户后检查访问拒绝，恢复后按契约验证可用，不自行假定恢复会自动复活旧会话。

管理员完成文件分类上传、查询、详情、下载、删除；下载校验文件名、内容摘要及错误提示。使用普通角色验证默认无权、仅 document 分类 VIEW 委派、未授予分类与跨租户读取拒绝。为 T-ACCESS-134 保留未删除文件及其内容摘要。业务数据通过 UI 或公开 API 准备，不用手工 SQL 填路由或授权。

## 当前口径

当前已通过平台页面开通 qa-132-a 与 qa-132-b，并核对列表刷新和开通审计；租户 A 停在首次登录接管点，等待用户在浏览器完成验证码登录、强制改密及重新登录。进度与待验范围见[验收记录](evidence/candidate-deployment/T-ACCESS-132/verification.md)。任务保持 in-progress，不以平台开通替代后续租户使用验收。

本卡要求真实浏览器和 Gateway 接线证据，不能以 mock 或服务直连成功代替。失败证据同时记录 HTTP 状态、响应信封、requestId（实际存在时）与页面表现；按[部署取证说明](../ops/deployment.md)核对登录日志及可归属租户的操作日志，不要求契约未承诺的故障零丢失。

执行时逐项登记操作者、租户、动作、预期、实际、时间和产物版本，存入计划共用证据目录。按[测试技能](../../.agents/skills/access-mesh-testing/SKILL.md)先复用既有证据；发现缺陷时定向补有效行为锁，禁止把浏览器走查机械复制为另一套巨型 E2E。实际会话与页面交付状态回写 design_refs；步骤修正写入 quickstart 或运营手册。

## 验收对照

- [ ] 两租户正常开通、首次改密和重新登录完成，测试身份明确。
- [ ] 组织、用户、角色及文件主线通过真实页面和 Gateway 验证。
- [ ] 分类委派、跨租户拒绝、租户停用恢复与会话边界按当前契约通过。
- [ ] 取证可关联，恢复用文件样本保留，文档回写完成。

## 非目标 / 遗留

不扩建文件平台，不引入独立强制下线或验证码替代通道，不承诺全页面排列组合覆盖。Q-068 与其他原有暂缓事项保持不变。
