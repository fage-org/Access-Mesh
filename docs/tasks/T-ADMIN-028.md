---
doc_type: task
id: T-ADMIN-028
title: "OAuth2 授权码客户端关联校验"
status: done
plan: docs/plans/iam-task-closure-plan.md
domain: access-service
design_refs:
  - docs/design/iam-task-closure.md#oauth
  - docs/design/access-service-architecture.md
  - docs/design/access-service-api-contract.md
depends_on:
  []
blocks: []
acceptance:
  - "同租户A/B客户端的授权码不可交叉兑换；合法A兑换保留scope/audience/tenant关联。"
  - "PKCE、redirect、过期、重复兑换及失败code消费语义经适用反例核实，敏感值不写日志。"
  - "现有测试夹具可重现旧实现缺口；若工具限制动态步骤，记录阻塞并保留任务未验收状态。"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-22
---

# T-ADMIN-028 OAuth2 授权码客户端关联校验

## 背景

承接[评审证据](../archive/2026-09-20/comprehensive-review.md)的 F002；基线与静态/动态证据强度见该记录。任务尚未实施，已有测试通过不代表该问题已解决。

## 范围

- OAuth2AppServiceImpl 授权码签发/兑换、现有客户端认证、PKCE及错误处理的关联校验。
- 复用本地OAuth2测试夹具形成跨客户端负向行为锁；不操作真实第三方账号。

## 当前口径

方案唯一入口：[IAM闭环方案](../design/iam-task-closure.md#oauth)。该方案为draft；沿用其推荐方向编排，涉及现行定案变化时先解决本卡待决项并回写权威来源，再实施。

**待决与启动核实**：无独立设计取舍；实现先核实证据，按推荐最小方案与现行约束执行。

## 验收对照

唯一验收清单见 frontmatter `acceptance`；设计回写、状态同步和验证按项目生命周期收口要求执行。

## 非目标 / 遗留

本卡只覆盖上述闭环，不自动扩展相邻产品能力；已有暂缓事项仍沿原任务。新发现且不能在本卡收敛的独立事项按项目生命周期登记，禁止把未知结果写为完成。

## 完成记录（2026-09-22）

- **核实**：兑换链路（`OAuth2AppServiceImpl.tokenByAuthorizationCode`）原有校验=client/secret、grant type、redirect_uri、PKCE、授权码一次性，从不比对授权码记录 clientId——B 凭自身合法凭据 + 原样回传 A 的 redirectUri（无 PKCE 码无需 verifier）可兑换 A 的授权码，令牌以 B 的 clientId/audiences 签发；refreshToken 路径早有同型检查。全仓授权码消费入口唯一（本类 + `OAuth2Controller` 薄透传）。
- **实现**：授权码加载并登记租户上下文后、redirect/PKCE 校验前比对 `codeData.clientId` 与已认证客户端，不匹配拒绝 `OAUTH2_CODE_INVALID`（10905，沿用既有信封，无新错误码/无新认证中间层）；失败消费授权码沿 redirect/PKCE 烧码既有口径（消费时点不变）；失败审计经 `recordOauth2Failure` 写 status=0、租户取授权码登记租户（failReason 不含 secret/code）。
- **回归锁**：`OAuth2AuthCodeClientBindingTest`（跨客户端兑换拒绝〔同租户/跨租户/反向，旧实现下实证红——HEAD 版本替换取证〕+ 合法兑换 scope/audience/tenant 关联保持 + 失败审计租户判别〔兑换方注册租户与授权码租户相异形态〕）；`OAuth2CodeExchangeNegativeTest`（redirect 不匹配 / PKCE 缺与错 verifier / PKCE 失败烧码后正确 verifier 重试拒 / 未知过期码 / 成功后重放 / 正确 verifier 正向路径——验收第 2 条的既有负向语义此前全仓零行为锁，本卡补齐）。
- **设计回写**：契约总册 §6.1.1（码记录绑定项半句）/§6.1.2（客户端关联校验段）/§6.1.3（refresh 侧既有绑定校验补记）；iam-task-closure.md §2.2 转已实施；`access-service-architecture.md` 判定无需改动（其 OAuth2 叙述不涉兑换校验链，双轨评审核实）；CHANGELOG [Unreleased] Fixed 补条目（用户拍板，定案见 decision-registry 2026-09-22 行）。
- **评审处置**：双轨评审 P0-P2=0；P3 级发现全为事实性修正直修（含随批存量清扫：architecture.md 认证中心措辞对齐 2026-09-16 如实口径、registry 断表空行、login.md 计数措辞去计数化）；过度设计可裁剪项=0。无遗留。
- **claude 外评处置（2026-09-22，模型 deepseek-flash[1M]，read-only plan 模式）**：P0-P2=0、P3×2 全采纳——①契约 §6.1.1「兑换时逐项校验」改为精确面（比对 clientId/redirectUri/PKCE，用户/租户/scope 取自码记录）；②跨客户端拒绝失败审计 failReason 追加签发方 clientId（用户拍板，失败行自证码归属；审计断言随改）。随批存量清扫：DTO status 注释与 DDL 反转/漂移五文件（Oauth2ClientCreateReq〔含缺省值口径〕/UpdateReq/Resp、DictTypeCreateReq〔含缺省值口径〕/DictTypeResp、NoticeResp 三态枚举）+ `Oauth2ClientAppService` javadoc「客户端凭证模式」残留对齐如实口径。存量观察②③登记 Q-029/Q-030（委托令牌不随用户禁用即时失效、authorize 不比对客户端租户）。定案与明细见 registry 同日处置行。
