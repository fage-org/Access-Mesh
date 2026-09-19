---
doc_type: task
id: T-PERM-070
title: 前置·公共服务认证模块（per-service 静态凭证）
status: proposed
plan: —（无所属计划；T-PERM-035 实现序列前置卡）
domain: permission-center
design_refs:
  - docs/design/service-authentication.md
  - docs/design/access-service-api-contract.md（凭证章新登记）
blocks: []
acceptance:
  - "service_credential 表（credential_id 全局唯一 uk、BCrypt secret_hash、多凭证并存轮换）+ 管理端点 create/update/remove/list（全 POST；create 回显明文 secret 仅一次）"
  - "单一认证仲裁器（五形态状态表 + 禁止降级 + 凭证优先）落 order=1 位；ServicePrincipal 不可伪造 attribute；RequestContextInterceptor 凭证路径只认 principal、用户/旧密钥路径行为零变化（order=1/2/3 输入输出按设计稿 §3.2）"
  - "服务端凭证端点白名单：authMethod=CREDENTIAL × 精确路径，清单单源落 common 模块（Gateway 与 access-service 共同消费）"
  - "Gateway：凭证头透传/清洗、InternalSecretFilter 收窄为无凭证头兜底注入、M2M 放行链（精确路径清单 skipAuth，禁整体白名单）+ 测试矩阵（成功/缺头/半头/错凭证/管理端点不旁路）"
  - "TLS 信任域模型：跨边界 hop secure scheme 校验 + HTTP 拒启护栏（ForwardHeadersStrategyGuard 先例）；allow-insecure 显式键 + Spring profile（compose app 档与 E2E 放行）"
  - "SDK 凭证头注入拦截器落 perm-common（client/registration 两 starter 共用）；上线序=服务端先行向后兼容"
  - "契约登记：契约总册凭证章（/api/access/service-credential/* 端点+认证协议）+ 凭证无效/过期/停用 403 新错误码排号 + 端点门禁权限码（service-config 管理面同族）登记"
  - "service-credential/* 新端点入 bootstrap 固定图 API 行（授权行按门禁档位；空库死锁防护，T-PERM-031 先例）"
  - "回归锁：凭证头+自报头并存以凭证为准、半头/错凭证 403 不降级、SDK 直连调管理端点拒绝、经 Gateway 两步同步链路成功"
design_writeback:
  required: true
  status: pending
last_updated: 2026-09-19
---

# T-PERM-070 前置·公共服务认证模块

> 执行门禁说明：原 design-review §11 E4 暂缓属 T-PERM-035 自动授权能力；本卡为 2026-09-19 定稿设计（用户确认）的前置基础设施，随定稿一并解除暂缓。

## 背景

现状 M2M 信任=全局共享 X-Internal-Secret + 自报 X-Service-Code/X-Tenant-Id（无签名）——「持全局密钥可冒充任意服务任意租户」，且 SDK FeignInternalSyncInterceptor 要求每个接入方自行配置全局密钥（信任面放大实证）。凭证模块把失陷半径收窄为单服务、可轮换可吊销，并供 T-PERM-071 依赖声明通道与资源同步通道共用。

## 范围

按 [service-authentication.md](../design/service-authentication.md)（adopted）全文实施：存储/认证链/两类接入形态/生命周期/分期退役（阶段二端点扩展清单与退役判据一并落地为文档口径）。

## 非目标 / 遗留

- 用户侧认证（Sa-Token/OAuth2）零改动；签名制（nonce/验签）不做（registry 2026-09-19 定案）；不借道 OAuth2 client_credentials。
- internal-secret 阶段二退役（判据=仍依赖旧密钥的端点清零）为后续演进，不在本卡。

## 验收对照

见 acceptance；实现语义全部以设计稿为准，稿内锚点（拦截器/过滤器/SDK 先例）均已代码级核实。
