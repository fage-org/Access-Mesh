---
doc_type: task
id: T-PERM-110
title: 认证与登录安全细节批（敏感日志/授权页切换签码/密码长度上限/等成本比对）
status: proposed
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/frontend/login.md
depends_on: []
blocks: []
acceptance:
  - "common GlobalExceptionHandler 对 MVC 请求解析错误的日志不再携带可能含密码/token 的原始错误信息（含尾随 token 穿透用例）"
  - "OAuth2 同一授权页切换 query 后不再给旧应用签发 code（oauth-consent 页对 client 变更换取新 preview/请求）"
  - "密码校验（CredentialPasswords 共用校验器）与离线工具 tools/ops/PasswordHash.java 同步字节边界：超 BCrypt 72 字节输入先长度后哈希、给出明确错误；历史密码登录验证保留（不回溯旧密码）"
  - "租户/平台登录未知账号分支执行等成本 dummy 哈希比对（0.5ms vs ~60-80ms 时间差异消除），错误码与计数行为不变"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-14
---

## 背景

P2×4 批（外部持续审查 2026-10-12 发现，2026-10-13 外部核实、2026-10-14 主代理复核证据成立）：

- **敏感日志**：`common/.../exception/GlobalExceptionHandler.java:144-148` 对 MVC 请求解析错误直接 WARN `e.getMessage()`，解析错误信息可能包含出错 token/密码片段（AOP「不记录请求参数」覆盖不了进 Controller 前的这个通道）。
- **授权页切换签码**：`frontend/src/views/oauth-consent/index.vue:21-29` 只在 onMounted 读一次 route.query 并保存局部 request/preview，后续决定仍用旧值——OAuth2 同一授权页切换 query（换 client_id）后给旧应用签 code。
- **密码长度**：`CredentialPasswords.java:16-18` 只按 String.length() 限 8-32（字符数），租户 `UserAppServiceImpl.java:383-398` 与平台 `PlatformAuthAppServiceImpl.java:93-98` 共用后调 BCrypt——26 个字符的汉字+字母密码 UTF-8 可达 74 字节，超 BCrypt 72 字节参与上限、尾部差异被忽略；离线恢复工具 `tools/ops/PasswordHash.java:15-19` 同形态（仅字符数检查）。
- **时间侧信道**：`AuthAppServiceImpl.java:184` 与 `PlatformAuthAppServiceImpl.java:57` 均为短路表达式——账号不存在时不执行 BCrypt（实测缺账号 ~0.2-0.5ms、错密码 ~60-80ms），错误码已同码（10005）但工作量差异保留账号存在性线索。

## 范围

登录/改密/OAuth2 授权页四点与离线恢复工具 tools/ops/PasswordHash.java；含配套回归锁。

## 当前口径

未知账号等失败路径用预生成 dummy 哈希做一次同成本比对后仍拒绝；其余三项按各自最小修法（日志脱敏面、client 绑定重核、长度先检）。保持现有错误码、验证码消费、锁号计数；不用固定 sleep/随机延迟模拟一致性，不重启验证码产品或登录事务改造（Q-067 独立）。

## 验收对照

见 frontmatter `acceptance`；时间侧信道用例以调用次数（0→1 dummy 比对）为断言锚，不以毫秒数为门。

## 非目标 / 遗留

2026-10-06「密码校验先行、错误码收敛」定案保留；不声称消除全部远程探测面（验证码/限流是部署侧叠加防护）。
