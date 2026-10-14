---
doc_type: plan
title: 架构与使用场景持续审查问题修复
status: active
domain: cross-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/service-authentication.md
  - docs/design/dependency-auto-grant.md
tasks:
  - T-GW-014
  - T-ACCESS-098
  - T-ACCESS-099
  - T-PERM-109
  - T-ACCESS-100
  - T-PERM-110
  - T-FE-068
  - T-FE-069
  - T-ACCESS-101
  - T-PERM-111
  - T-PERM-112
  - T-ACCESS-102
  - T-ACCESS-103
  - T-ACCESS-104
acceptance: "33 项核实成立缺陷（P1×3、P2×27、P3×3）全部被任务验收覆盖或显式登记待拍板；密钥出站与 CIDR 退化放行优先收口；全量回归绿（收口形态含 E2E 与 heavy）"
last_updated: 2026-10-14
---

## 目标

修复 2026-10-12~14 外部持续架构与使用场景审查发现、并经外部模型核实（2026-10-13）与主代理逐条代码级亲核（2026-10-14）双重确认成立的 33 项缺陷。范围覆盖密钥出站边界、SDK 宿主侵入、条件判定正确性、前端时序与可用性、引擎可观测、示例正确性与文档流程对齐七类。

## 非目标

- 五项需产品拍板的设计边界（导出领取、同码重建凭证、内置登记自停、验密容量、功能角色多关系口径）不在本计划——已登记待解决问题清单 Q-073~Q-077，拍板后按需转出。
- 三项裁剪评估（模板 SSO 残留、虚构消息、iframe 缓存模板）登记 Q-070~Q-072，随评估结论处理。
- 已接受边界不重开：显式 Gateway 拓扑、不做时区处理、单实例本地盘、四类条件模型、M5 手动预览语义等。

## 准入条件

- 用户确认启动本计划及优先序（默认建议：T-GW-014 → T-ACCESS-098 → T-ACCESS-099 → T-PERM-109，随后按批次推进）。
- Q-077（功能角色多关系口径）若影响前端角色撤回行为，其拍板不阻塞本计划其他任务。

## 任务清单

状态快照见 [任务看板](../tasks/README.md)。

| ID | 标题 | 优先级 | 状态 |
|---|---|---|---|
| [T-GW-014](../tasks/T-GW-014.md) | Gateway 内部密钥出站收窄 | P1 | ✅ |
| [T-ACCESS-098](../tasks/T-ACCESS-098.md) | SDK 凭证拦截器端点匹配与装配边界收口 | P1 | ✅ |
| [T-ACCESS-099](../tasks/T-ACCESS-099.md) | 预览 Compose 文件持久化卷 | P1 | ✅ |
| [T-PERM-109](../tasks/T-PERM-109.md) | 条件 CIDR 参数校验收口 | P2（安全面，建议首梯队） | ✅ |
| [T-ACCESS-100](../tasks/T-ACCESS-100.md) | SDK 公共依赖宿主侵入收口 | P2×4 族 | ✅ |
| [T-PERM-110](../tasks/T-PERM-110.md) | 认证与登录安全细节批 | P2×4 族 | ⚙️ |
| [T-FE-068](../tasks/T-FE-068.md) | 前端迟响应与竞态批 | P2×4+P3 族 | ⚙️ |
| [T-FE-069](../tasks/T-FE-069.md) | 前端选择器与表单可用性批 | P2×5 族 | ⚙️ |
| [T-ACCESS-101](../tasks/T-ACCESS-101.md) | 文件能力收口批 | P2×2 族 | ⚙️ |
| [T-PERM-111](../tasks/T-PERM-111.md) | explain 与声明诊断修正批 | P2×2+P3 族 | ⚙️ |
| [T-PERM-112](../tasks/T-PERM-112.md) | 互斥审计请求关联与主体补全 | P2 | ⚙️ |
| [T-ACCESS-102](../tasks/T-ACCESS-102.md) | example-service 子报表真实父关系核对 | P2 | ⚙️ |
| [T-ACCESS-103](../tasks/T-ACCESS-103.md) | 文档与流程对齐批 | P2×3+P3 族 | ⚙️ |
| [T-ACCESS-104](../tasks/T-ACCESS-104.md) | 无权删除全拒反馈修正（角色与资源批删链） | P2 | ⚙️ |

## 归档条件

全部任务 done（或 cancelled 并登记去向）；各项缺陷对应回归锁进仓；设计文档回写完成；与本计划相关的待解决问题条目（Q-070~Q-077 中转出项）完成收敛或明确仍开放。

## 当前进度

2026-10-14 立项并经外部评审修订：33 项缺陷已全部映射到 14 张任务卡（33 = 35 项发现 − 1 项核实撤销〔OAuth2 JSON 线协议与全仓 POST+JSON 契约一致，非缺陷〕 − 1 项降级为裁剪候选〔iframe 缓存 hash 刷新，Q-072〕）。同日用户确认按默认优先序启动（T-GW-014 → T-ACCESS-098 → T-ACCESS-099 → T-PERM-109，随后按批次推进），计划转 active。首梯队四张已全部收口（2026-10-14）：T-GW-014（网关密钥出站按路由目的收窄，红跑 2/4）、T-ACCESS-098（SDK 拦截器完整值匹配 + per-client 装配隔离，红跑 6 项）、T-ACCESS-099（compose 命名卷 + 真实容器重建后下载逐字节一致；发现文件能力三层不可达登记 Q-078）、T-PERM-109（CIDR 参数校验单源收口，红跑判定器 6 + 写入口 4）；四张均 e2e reactor 回归 0 失败。P1×3 与安全面首梯队完成，余 10 张 P2/P3 批次卡按看板序推进。T-ACCESS-100 已收口（2026-10-14）：common 零注册外溢+Web 栈 optional+SDK 日志零参与（探针 4 锁红跑转绿）；过程中实证缓存装配注册外溢可炸宿主启动（第五面，随整文件退出收口）与 E2E 共享类路径泄漏/排除校验互斥（定案「注册以排除」）；全量 -T 1C 三轮（r1/r2 为修复轮）终态 2817/0/0 含 E2E。余 9 张批次卡按看板序推进（下一张 T-PERM-110）。
