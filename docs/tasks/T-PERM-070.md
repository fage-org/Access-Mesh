---
doc_type: task
id: T-PERM-070
title: 前置·公共服务认证模块（per-service 静态凭证）
status: done
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
  - "TLS 信任域模型（2026-09-20 拍板修订：启动声明式护栏）——SDK 配置凭证（perm.credential-id/secret）必须显式声明 perm.allow-insecure（三态：true=单信任域明文 hop 可接受/false=跨边界期望 TLS，均有效声明；缺省拒启）；纯声明不校验实际地址（实仓服务发现形态无静态 URL 落点，设计稿「三处配置校验」假定直配 URL 形态与实仓不符）；Gateway→access-service 内网 hop 不校验"
  - "SDK 凭证头注入拦截器落 perm-common（client/registration 两 starter 共用）；上线序=服务端先行向后兼容"
  - "契约登记：契约总册凭证章（/api/access/service-credential/* 端点+认证协议）+ 凭证无效/过期/停用 403 新错误码排号 + 端点门禁权限码（service-config 管理面同族）登记"
  - "service-credential/* 新端点入 bootstrap 固定图 API 行（授权行按门禁档位；空库死锁防护，T-PERM-031 先例）"
  - "回归锁：凭证头+自报头并存以凭证为准、半头/错凭证 403 不降级、SDK 直连调管理端点拒绝、经 Gateway 两步同步链路成功"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-20
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

## 实施记录（2026-09-20）

**五项拍板**（AskUserQuestion 两轮，registry 同日行）：三码细分+20068 / infrastructure.credential 落位 / update=status+expiresAt / sc-·sk- base64url 无上限 / TLS 启动声明式护栏（三态）。

**落地面**：

- schema：`service_credential` 表（第 34 张；credential_id 全局唯一 uk WHERE delete_flag=0 + (tenant_id,service_code,status) idx）
- common：`M2mCredentialEndpoints` 白名单单源（method+精确路径三端点，071 manifest 预留；Gateway 与服务端共同消费）
- access-service `infrastructure.credential`：实体/Mapper(+XML)/DomainService（issue 生成与 BCrypt/verify 四态/changeStatus/changeExpiresAt/softDelete）/AppService（门禁 SERVICE:MANAGE·VIEW 同族）/Controller（四 POST 端点）/DTO 六件
- 认证链：`ServiceAuthArbiter`（order=1 重构，删 InternalApiSecretInterceptor；五形态状态表+凭证优先+禁止降级+白名单强制）；HeaderSignatureInterceptor 凭证路径放行；RequestContextInterceptor 凭证路径只认 principal（service 上下文凭证行派生）；错误码 20065~20068
- Gateway：`M2mCredentialFilter`（-75 M2M 识别→skipAuth 仅透传）；`InternalSecretFilter` 收窄无凭证头兜底注入；凭证头不入清洗列表
- SDK：perm-common `FeignCredentialInterceptor`（perm.credential-id/secret 成对 fail-fast；perm.allow-insecure 三态护栏缺省拒启）；client starter @Import 装配（071 registration starter 直接复用）
- bootstrap：service-credential/{create,update,remove,list} 四行入固定图（业务门禁 SERVICE:VIEW/MANAGE 已在图零新增）
- 契约总册 §24 新章（端点+状态表+白名单+错误码+SDK 配置+上线序）；gateway.md M2M 凭证放行链节

**双轨评审处置（2026-09-20）**：代码轨 P0-P2=0、P1×2+P3×9；文档轨 P0×5+P2×2+P3×8——逐条亲核全属实（P1-1 SDK 宽注入面击穿 auth/check 族/五处既有回归锁未随批更新/PgIT 缺 @Tag 落错轨道并实证 BadSqlGrammar 串扰/BootstrapPgIT 三处计数/schema-架构-capability 文档漂移等），全部直修；两项修法拍板（registry 同日行续）：①SDK 注入面收窄=**精确镜像三端点**（副本+注释+双侧测试锁，071 扩白名单同步两处）；②E2E 验收第 9 条锁强度=**补真实 sync 成功用例**（建 E2E_CRED_RES SYNC 类型 fixture→凭证 UPSERT 真实成功信封→DELETE 同步→清理，fixture 为 071 manifest E2E 前置基建）。@Future/issue fail-fast 化/损坏哈希归一/containsKey 语义对齐/feign-core 版本交 BOM/旧口径清扫（T-PERM-035 三处+runbook 计数+AGENTS 权威表补行）等事实项全直修。

**claude 外评处置（2026-09-20，模型 deepseek-flash[1M]）**：P0-P2=0、P3×3、过度设计=0，专项四组全过（双轨处置复核/写读入口/五形态逐分支/既有入口）；P3-1 serviceCode 注册侧补同宽校验、P3-2 allow-insecure 拍板收紧二值白名单、P3-3 契约注释精度直修，存量×3 处置见 registry 同日行；定向复跑全绿。

**测试**（单测 40+容器 3+E2E 1）：ServiceAuthArbiterTest 八形态矩阵（含凭证+自报头+密钥并存以凭证为准、失败禁止降级、白名单外 403）；M2mCredentialEndpointsTest 单源三端点+无通配负向；两拦截器测试各加凭证路径用例（自报头忽略/伪造用户头不拒）；DomainServiceImplTest 十二例（验证顺序锁=错误 secret 掩蔽停用态、格式锁、冲突重试、rotated_at 仅停用记）；AppServiceImplTest 九例（门禁族/死凭证防线/空 patch）；M2mCredentialFilterTest 五例（半头/非 M2M/方法不匹配不 skipAuth）；InternalSecretFilterCredentialNarrowingTest 三例；FeignCredentialInterceptorTest 六例（注入/不覆盖/半配 fail-fast/护栏三态）；ServiceCredentialPgIT 三例（真实 DB 全链 issue→verify→仲裁→上下文绑定、三态生命周期、跨租户全局唯一）；ExampleProtectedApiE2EIT step7（真实 Gateway 链：签发→M2M 认证通过→半头 401→错凭证 403〔body 20065〕→管理端点不旁路 401→停用后 403〔20067〕→清理）。
