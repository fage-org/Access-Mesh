---
doc_type: task
id: T-ACCESS-101
title: 文件能力收口批（fileUrl 字段语义与下载文件名编码）
status: done
plan: docs/archive/2026-10-17/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
  - docs/design/schema/access-service.sql
depends_on: []
blocks: []
acceptance:
  - "FileResp 删除无消费价值的 fileUrl 字段，明确鉴权 POST 下载为唯一访问方式（含前端 Blob 读取形态说明）"
  - "中文文件名下载响应头按 RFC 5987/UTF-8 编码（filename*），现代浏览器显示原名；不依赖容器默认 Latin-1 丢字行为"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-17
---

## 背景

P2×2 批（外部持续审查 2026-10-12 发现，2026-10-13 外部核实定级、2026-10-14 主代理复核成立）：

- **fileUrl 无通道**（2026-10-14 补证）：`FileAppServiceImpl.java:322` 固定生成 `/files/` 相对地址，`FileResp` 注释标「文件访问 URL」；但服务内零 `/files/**` 资源处理器、零静态资源注册，Gateway 仅两条路由，前端 nginx 对未知路径回退 index.html——标准部署下该字段不可用，API 接入者按名称消费必然落空；实际访问通道是 FileController 的鉴权 POST 下载。
- **中文文件名**：上传中文名文件后，下载响应头 Content-Disposition 按默认字符集编码，Tomcat 侧丢弃不可编码头，浏览器拿到无名/乱码文件（已收窄定性：非下载失败，是响应头文件名丢失）。

## 范围

FileResp 删除 fileUrl 并收敛访问文档；下载响应头文件名编码；契约对应能力章回写。

## 当前口径

当前没有接入方，无外部字段兼容要求；FileResp 删除 fileUrl，数据库历史列暂不删除。不为兑现 fileUrl 新增公开静态文件入口；浏览器展示需求沿既有 POST 下载协议读字节生成 Blob URL。文件名编码用标准 filename* 参数。不扩建文件服务或对象存储。

## 验收对照

- [x] FileResp 删除 fileUrl，停止生成无效地址，保留历史数据库列；契约说明鉴权 POST 与 Blob 读取。
- [x] 下载头含 UTF-8 filename* 与 ASCII filename 回退，控制字符和引用转义保持。

## 非目标 / 遗留

文件存储持久卷问题由 T-ACCESS-099 承接；多实例共享存储维持非目标。标准部署的文件路由/授权种子与前端消费面另由既有 Q-078 跟踪，本卡不改变其未定产品边界。

## 完成记录

2026-10-17：`mvn test -T 1C` 全 reactor 通过，包含 E2E 与 heavy；本次生成的 Surefire XML 共 2860 个 testcase，失败/错误/跳过均为 0（按 XML 实际 testcase 计数，避免嵌套测试汇总行差异）。
文件名回归旧行为失败、修正后 FileAppServiceImplTest 26 项通过；FileServiceSecurityPgIT 11 项与 schema 容器验证通过。

代码轨核对响应无失效地址、UTF-8/ASCII 下载头、控制字符处理及既有文件权限门禁；文档轨核对契约、schema 历史列说明与 Q-078 既有边界。两轨无未解决 P0–P2 或待决策项；未引入无调用方机制。
