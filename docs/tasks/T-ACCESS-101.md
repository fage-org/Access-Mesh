---
doc_type: task
id: T-ACCESS-101
title: 文件能力收口批（fileUrl 字段语义与下载文件名编码）
status: proposed
plan: docs/plans/scenario-audit-remediation-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md
depends_on: []
blocks: []
acceptance:
  - "FileResp 的 fileUrl 字段不再以「访问 URL」语义交付无通道地址：收敛说明并明确鉴权 POST 下载为唯一访问方式，评估废弃无消费价值的 fileUrl（含前端 Blob 读取形态说明）"
  - "中文文件名下载响应头按 RFC 5987/UTF-8 编码（filename*），现代浏览器显示原名；不依赖容器默认 Latin-1 丢字行为"
design_writeback:
  required: true
  status: pending
last_updated: 2026-10-14
---

## 背景

P2×2 批（外部持续审查 2026-10-12 发现，2026-10-13 外部核实定级、2026-10-14 主代理复核成立）：

- **fileUrl 无通道**（2026-10-14 补证）：`FileAppServiceImpl.java:322` 固定生成 `/files/` 相对地址，`FileResp` 注释标「文件访问 URL」；但服务内零 `/files/**` 资源处理器、零静态资源注册，Gateway 仅两条路由，前端 nginx 对未知路径回退 index.html——标准部署下该字段不可用，API 接入者按名称消费必然落空；实际访问通道是 FileController 的鉴权 POST 下载。
- **中文文件名**：上传中文名文件后，下载响应头 Content-Disposition 按默认字符集编码，Tomcat 侧丢弃不可编码头，浏览器拿到无名/乱码文件（已收窄定性：非下载失败，是响应头文件名丢失）。

## 范围

FileResp 字段语义/文档收敛（或废弃评估）；下载响应头文件名编码；契约对应能力章回写。

## 当前口径

不为兑现 fileUrl 新增公开静态文件入口；浏览器展示需求沿既有 POST 下载协议读字节生成 Blob URL。文件名编码用标准 filename* 参数。不扩建文件服务或对象存储。

## 验收对照

见 frontmatter `acceptance`；中文文件名用例覆盖原名显示与旧客户端 ASCII 回退。

## 非目标 / 遗留

文件存储持久卷问题由 T-ACCESS-099 承接；多实例共享存储维持非目标。
