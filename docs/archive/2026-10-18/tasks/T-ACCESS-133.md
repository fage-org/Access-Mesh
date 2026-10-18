---
doc_type: task
id: T-ACCESS-133
title: 首次业务接入与授权闭环验收
status: done
plan: docs/archive/2026-10-18/candidate-deployment-acceptance-plan.md
domain: cross-service
design_refs:
  - docs/design/extension-guide.md
  - docs/design/services/example-service.md
  - docs/design/service-authentication.md
  - docs/design/access-service-api-contract.md#operation-admission-protocol
depends_on:
  - T-ACCESS-132
acceptance:
  - 依公开文档从未登记状态完成 example-service 的真实首次接入
  - 普通用户授权前拒绝、授权后允许、撤销拒绝、重授恢复均有证据
  - 网关准入与实际对象最终检查、租户凭证和防伪造身份边界分别得到验证
  - 接入文档可复现并明确自动执行与独立使用者验证的证据区别
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景

现有 example-service 已有真实 Gateway 接入和报表最终检查，已有 ExampleProtectedApiE2EIT、ExampleBusinessFinalCheckE2EIT 可复用。本卡验证固定产物在独立环境下能否依公开步骤首次接入，不重做这些能力。

## 范围

从服务未登记、凭证未签发的状态开始，按[接入指南](../../../design/extension-guide.md)和[快速开始](../../../quickstart.md)，由租户管理员创建业务类型、操作及实例，登记 example-service、声明 sync-v2 FULL 接口，并签发所属租户的 per-service 凭证。核对 Gateway 显式转发路由、服务发现与接口准入映射分别生效；不把 sync-v2 当作转发路由创建器。

将凭证按实际 tenantId 注入共享示例实例，再启动 T-ACCESS-131 固定的 example-service 产物。用户管理与授权写入使用用户身份，查询与同步使用服务身份。工具调用使用原生登录取得的 Bearer；网页使用现行 Cookie/CSRF 链路，不要求从浏览器存储读取 HttpOnly 令牌。沿本路径发现的文档漂移直接修正并验证，接口形状仍引用契约总册。

使用没有管理员兜底权限的普通用户完成 hello 准入与报表实际对象检查；在实例 A 获授权、实例 B 未授权的前提下，验证能进入路由不代表可以读取 B。覆盖授权前拒绝、授予 A 后允许、撤销 A 后拒绝、重新授予恢复；排除其他角色/继承授权影响，按现行撤权边界有界轮询并记录实际生效时间，不靠固定 sleep 宣称通过。

用另一个租户验证凭证缺失不回落默认身份；配置其独立凭证后验证同码业务资源与用户仍隔离。检查停用服务凭证后的拒绝及重新启用后的行为，另以缺失/伪造签名身份请求验证防直调边界；区分 Gateway 真 403、SDK 验签拒绝和业务最终检查信封，不能只按 HTTP 200 判成功。

## 当前口径

真实接入以现有 example-service 为消费方，不创建新业务数据库。沿用公开文档执行，遇到必须读源码、补隐式配置或由作者解释才能继续的步骤，记录原始卡点并修正文档后从对应起点重验。没有独立使用者参与时，证据标明为代理按文档走查，不能宣称新人独立接入已验证；独立人员参与不是本卡完成的额外前提。

任务完成时将已验证接入步骤、身份分工、失败形态与示例覆盖边界回写 design_refs；保留完整脱敏命令、请求结果及版本归属。若产品代码因阻断变动，按 T-ACCESS-135 版本追溯要求重新构建、部署和验证受影响链路。

## 验收对照

- [x] 服务从未登记状态依文档接入，凭证、路由和服务发现配置可复现。
- [x] 普通用户授权、撤销、重授主线通过，时间与授权前提明确。
- [x] 实例 B 越权、租户隔离、凭证缺失/停用及身份签名反例得到验证。
- [x] 接入卡点已处置，指南与设计回写，实际验证者身份如实记录。

## 非目标 / 遗留

不实施 T-PERM-036 的 query-scopes 到真实业务 SQL 链路，不恢复诊断页面，不新增 SDK 自动改写或注解能力，不把本卡当作全协议认证审计。

## 完成记录

2026-10-18 沿 T-ACCESS-131 固定镜像在原隔离栈完成两租户首次接入，启动 example-service 并配置租户独立凭证。原生用户身份执行管理写入，服务身份执行 FULL 同步与运行时查询；普通用户 qareader 的 EXAMPLE/report-1 VIEW 允许、report-2 拒绝，撤销与重授分别在首次 30ms/36ms 探测生效。租户缺凭证映射、凭证停用/恢复、主体与实际业务数据隔离、Gateway 身份清洗及 SDK 直连拒绝均已验证。

[验收证据](evidence/candidate-deployment/T-ACCESS-133/verification.md)保留 61 条脱敏 HTTP 观察、授撤轮询、直连反例、数据库只读终态、运行镜像与服务发现信息。临时执行命令及适用边界见证据；公开 [接入操作示例](../../../../example-service/examples/first-access.md)与 FULL 清单已重验。修正 quickstart 的浏览器取令牌提示，补齐报表固定资源码与错误分层，并完成 design_refs 回写。

代理执行，不宣称独立新人验收；无产品代码变动或镜像重建，本地代码轨与文档轨核对通过。完整回归仍由 T-ACCESS-135 承载。运行环境、凭证映射、服务登记、普通用户授权及原有文件样本保留给 T-ACCESS-134；example 运行期内存对象不纳入持久化要求。
