---
doc_type: task
id: T-ACCESS-118
title: 标准文件能力外评核实与修复
status: done
plan: —
domain: cross-service
design_refs:
  - docs/design/frontend/file-management.md
  - docs/design/access-service-api-contract.md#contract-section-17-1
  - docs/design/access-service-architecture.md
  - docs/ops/deployment.md
depends_on: []
acceptance:
  - 四组外评结论逐条核实，权限边界与过度设计候选有明确处置
  - 文件下载区分真实附件与错误，上传失败清理边界与既有契约一致
  - 同会话删除完成后正确刷新当前筛选，跨会话或卸载结果仍不生效
  - 反例、定向检查、收口回归和本地双轨核对完成，设计与待办同步
design_writeback:
  required: true
  status: done
last_updated: 2026-10-18
---

## 背景与范围

对 `a1e98c720` 的四组 gpt-6.1-sol max 只读并行评审，核实了合法 JSON MIME 附件误判、上传元数据失败后的物理残留，以及删除期间切换筛选的刷新遗漏。原计划与三张任务卡保持历史归档，本卡承接后续修复。

实现边界以 design_refs 为准；已确认保留原 MIME、按附件头识别成功文件，文件与审计下载统一使用公共 HTTP 错误处理。上传仅清理本次明确失败/回滚的文件，结果未知或清理失败时保留告警。上传失败修复限制在本次文件生命周期，不扩展为目录、存储平台或后台回收系统。T-PERM-036 继续暂缓。

## 验收对照

- [x] 外评去重、代码与实证核实、必要决策完成。
- [x] 文件生命周期与前端响应/刷新边界修复。
- [x] 对应反例与回归通过。
- [x] 本地双轨核对、设计回写、验收归档与当前入口校准。

## 验收结果

外评核实、用户确认的修复/裁剪及完整回归已完成，详见[验收记录](evidence/T-ACCESS-118/verification.md)。
