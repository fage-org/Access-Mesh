---
doc_type: task
id: T-PERM-071
title: 独立依赖声明与可选 SDK 协调
status: done
plan: —（无所属计划；自动授权实施序列）
domain: access-service
design_refs:
  - docs/design/dependency-auto-grant.md#architecture
  - docs/design/dependency-auto-grant.md#data-model
  - docs/design/dependency-auto-grant.md#integration
  - docs/design/dependency-auto-grant.md#compiler
  - docs/design/access-service-api-contract.md
  - docs/design/schema/access-service.sql
  - docs/design/frontend/resource-dependency.md
  - docs/design/extension-guide.md
depends_on:
  - T-PERM-070
  - T-PERM-078
  - T-PERM-074
blocks: []
acceptance:
  - "declaration/manifest_sync 与编译图落地，RESOLVED/REJECTED、同键并集聚合、反向索引和 dirty 重判符合采用设计；不建 support 表。"
  - "资源单条 sync 与按类型 scope FULL 独立可用；仅资源接入无需 manifest/registration starter/空依赖；更新依赖无需重传全部资源。"
  - "manifest 独立 FULL：静态 JSON/动态 Provider、完整空依赖清单、语义 hash 规范化与四条件幂等、description 更新、逐项失败诊断；不得把 HTTP 200 当全部成功。"
  - "编译源/目标同 owner、资源与操作存在性、自依赖/联合图环校验；资源 status 停用不等于缺失、不据此拒编或移除贡献，来源服务启用门禁保持；MANIFEST 为唯一声明写入来源，无 ADMIN_UI 或跨 owner/内部目标 override。"
  - "SDK 复用现役凭证，独立 manifest 发布与可选协调；资源 FULL 与 manifest FULL 按各自 scope 校验发布源递增代次，旧代次在写入/缺失删除前拒绝，重试沿用代次及快照；不以 git revision、资源项 syncVersion 或本机 now() 替代发布排序。"
  - "按 M1 验证混用 FULL scope 的增量/FULL 共同排序与双向防护，纯增量旧协议保持；覆盖跨键乱序、代次并发与同代次冲突、部分失败重试、dirty 重判及存量客户端迁移；动态刷新和前置部分失败按其最终协议验收。"
  - "资源 FULL 接受明确完整空 items=[]，身份/所有权/代次通过后仅清本同步范围，其他维护来源与其他 scope 保留；旧代次空清单零副作用拒绝，缺失/null 非法，读取失败/分页未完/Provider 异常不得提交空快照；自动授权回收由 072 挂接。"
  - "batch-sync、DEPENDENCY:SYNC、autoGrant 全字段链及 bootstrap/前端 perms/routes/mock/SDK/契约锁步退役；交付目标库只读盘点与原表保全迁移，真实旧 schema 夹具验证各来源/软删行完整保留，有效历史 AUTO_DEP 在修改前中止；不得将测试库报告当部署存量结论。"
  - "manifest M2M 精确白名单与服务端所有权校验闭合，不新增用户 API 固定图授权行；关闭 create/update/remove 服务端写入口并同步清理前端调用与固定图引用，验证不能直接请求绕过；读门禁保持。"
  - "编译接入共同资源树锁前，统一混合类型删除现有 RESOURCE_ENTITY→ABSTRACT_ROLE 反序及同模式调用点，按目标全序验证与组织/角色写入的确定性交错。"
  - "真实接口验收分别覆盖仅资源接入、仅依赖更新、可选两步协调失败重试；编译并发协议沿 M4，正式字段/错误码与前端设计回写完成。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-21
---

# T-PERM-071 独立依赖声明与可选 SDK 协调

## 背景

自动授权简化方向已采纳，[设计稿](../../../design/dependency-auto-grant.md)为实施依据。资源同步保持独立；无需自动授权的接入方不承担依赖清单成本。

## 当前口径

T-PERM-078 已完成设计 §16 的实施协议校准，T-PERM-074 已闭合同步失败记账问题。来源不再落逐路径 support，API 派生仍由 T-PERM-054 承接且维持暂缓。本卡已完成：全部组件交付、整卡回归（含 E2E）与双轨/外评处置完毕，2026-09-21 随用户确认转 done；风格清理转出 T-PERM-079，物化与解释归 072/073。

## 范围

声明数据层、编译器、独立 manifest 通道、SDK 发布/可选协调、管理写入口关闭及存量迁移、契约和前端兼容收口。管理只读边界按设计 §5，旧 autoGrant=false 边不得静默转为自动授权声明。资源 sync/full-sync 原有独立使用方式保持，相关协议修正按 M1 已定内容实施；依赖物化回收挂接归 072。

## 验收对照

当前已实现混合类型批删锁序修正：统一先 ABSTRACT_ROLE 后 RESOURCE_ENTITY，保留单类型入口、门禁和锁内重读。`TypeDefinitionAppServiceImplTest` 通过（2026-09-21），新增混合顺序用例在旧实现下失败；`MixedTypeDeletionLockPgIT` 通过，使用真实事务与 Redis 锁镜像组织投影锁序，不冒充完整组织 API 验证。manifest 的规范化/纯编译、声明与服务发布状态事务及凭证 HTTP 入口已实现；管理四个写路由及前端写表单/API 已移除。资源增量/FULL 共同代次、原快照重试、一次性切换、完整空清单及分批清理已接入原事务与资源树锁。旧 DTO/写方法、autoGrant 全链及 DEPENDENCY:SYNC 种子已退役，迁移脚本与运行手册已提供。资源删除、类型与操作定义变更的编译生命周期及 dirty 已接入共同锁与原事务；可选 registration starter、静态 JSON 启动发布、动态 Provider 固定快照、显式资源前置与多租户目标已实现；示例 profile 默认关闭。

当前组件验证（2026-09-21）：compiler/normalizer 核心单测通过；PermissionManifestPgIT 的真实凭证 HTTP、同代次/旧代次、部分失败重试、空 scope 隔离、末步故障回滚、并发与 4,370 目标清单均通过。大清单参数上限、stale 明细与非 ASCII canonical 指纹均有旧实现失败证据。SQL/Mapper 和 HTTP 契约架构检查通过；前端 typecheck、定向 lint 与只读 API 用例通过。本地双轨发现已处置并复核，未把这些组件验证写成整卡完成。

资源共序组件的定向验证（单测与数据库测试双轨）覆盖跨键乱序、同代次冲突、部分失败重试、微秒等版确认、真实凭证 HTTP 空数组/缺字段、事务回滚与等待增量提交后拒绝旧 FULL。65,540 行真实范围清理覆盖参数上限；JSONB 数值有效值比较有旧实现失败证据，科学计数法等值与高精度不等值均已锁定。本地双轨复核无未处置问题；契约 §19.1/§19.2 与 full-sync 运维手册已回写。退役与迁移验证另覆盖只读门禁、HTTP 路由、操作码校验、bootstrap、H2/PG schema 及 manifest 真实写入；旧表夹具验证整表/审计字段保全、有效 AUTO_DEP/未知来源中止、形状/目标冲突与末步 DDL 失败回滚。空旧库和含旧边库均能在迁移后通过实际 AppService 发布新声明，旧边不生效；迁移验证不代表已操作任何部署环境。

编译生命周期验证覆盖资源中间节点删除、原清单恢复、启停保持图身份、FULL 删除隔离、末步故障原子回滚、操作位/继承位修改和类型引用回收。提交闸门验证资源/操作删除先持锁而 manifest 重发等待的交错；重发只读取已提交的新事实。架构边界检查与原 manifest/迁移发布回归通过。

SDK 定向测试与公共 R 泛型解码测试通过，生产 Spring Feign HTTP 验证显式租户凭证不被默认配置覆盖。真实 access-service HTTP 验证 FULL/增量前置部分失败后原输入重试、独立清单更新及多租户隔离；同代次成功增量重试使用 PUBLICATION_UNCHANGED，真正旧代次仍阻断。静态启动的文件缺失、完整内容解析和部分失败均拒绝，未把 HTTP 200 当完成。

见 frontmatter acceptance。以同服务月报/模板为最小业务闭环，同时用无依赖的纯资源接入证明未引入强制依赖。新端点/形状只在正式契约登记，不由任务卡重定义。

## 非目标 / 遗留

物化与解释分别归 072/073；跨系统依赖、类型级种子、注解、异步队列不进入本卡。M3 管理只读与迁移保全边界按设计 §5/§10.1，M1/M4 按 §4.4.1/§8 实施；对未知部署库不自动执行迁移或清理。

## 完成记录

2026-09-21 完成（实现六连 be96a293f～05e3e26ad + 收口 e1678f300～a019bfdba）。声明数据层与编译器、独立 manifest M2M 通道、资源/清单发布代次共序、管理写入口退役与原表保全迁移、SDK registration starter 与示例全部交付；设计回写闭合（dependency-auto-grant 四节、契约 §2.5/§12.3/§19.1/§19.2/§19.2.1/§19.10、schema 三新表、frontend/resource-dependency 只读化、extension-guide 状态行）。

- 全量回归 `mvn test -T 1C`（含 E2E）11 模块全绿，1842 项 0 失败 0 错误，E2E 对最终代码重跑通过；日志整文件落盘。
- 本地双轨评审处置完毕：P3×1 + P2×6 + P3 文档×5 全直修；两项拍板（manifest 继承旧密钥边界、sync_key 保留）登记 registry 同日行。
- 四任务内外评审（内部四子代理分任务 + claude deepseek-flash）：验收达标、P0-P2=0；claude P3×3 全处置（MANIFEST_UNCHANGED 契约登记、迁移「只保全不清理」取舍注记、规模上限拍板豁免登记）；零行为风格小修直修，结构性风格重构转出 T-PERM-079（卡为唯一清单源）。
- 未对任何部署环境执行迁移；迁移验证仅限测试夹具（旧 schema 夹具保全/中止/回滚用例）。
