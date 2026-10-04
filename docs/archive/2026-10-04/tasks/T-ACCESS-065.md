---
doc_type: task
id: T-ACCESS-065
title: 树过滤完整性与状态口径逐子项定案修复
status: done
plan: docs/archive/2026-10-04/pending-problems-clearance-plan.md
domain: access-service
design_refs:
  - docs/design/access-service-api-contract.md §8（org 父名）+ §10.5（角色树）+ §12（资源树查询语义）
depends_on: []
blocks: []
acceptance:
  - "角色/资源树过滤后缺父子支提升为展示根，保留真实 parentId，不补回被过滤节点；组织树既有根过滤语义保持"
  - "资源树 enabledOnly 缺省 false，管理显示全部状态，授权/映射/依赖选择器显式 true"
  - "OrgResp 提供允许展示的 parentOrgName，批量组装；前端信息卡/表单直接消费，根与不可见父级分开显示"
  - "各子项的旧形态证据、定向验证和前端四项验证通过，契约 §8/§10.5/§12.1 已回写"
design_writeback:
  required: true
  status: done
last_updated: 2026-10-04
---

# T-ACCESS-065 树过滤完整性与状态口径逐子项定案修复

## 背景

承接 [Q-038](../../../pending-problems.md#q-038)（合并 Q-039/Q-019）：三子项——① TreeBuilder（infrastructure/util/TreeBuilder.java:57-67）只从 parentId==null 起建树，父被状态过滤而子保留时子支不可达（实例裁剪已规避，状态/类型过滤仍可触发）；② 资源树固定 status=1 而管理列表不滤状态——停用资源管理列表可见、授权树不可见；③ user/index.vue 信息卡与表单从过滤后 orgTree 查父名，父被 orgType 筛选或权限排除时显示「未知」，OrgResp 未提供 parentOrgName。数据和权限过滤后的展示不完整，未发现越权路径。

## 范围

三子项定案+实施；公共 TreeBuilder 消费面盘点（各消费树覆盖核对）。

## 当前口径

① 角色/资源树将过滤后父节点缺失的子支提升为展示根，保留真实 parentId，不补回被过滤父节点（2026-10-04 确认）。组织树独立的「根被过滤则空树」契约保持，此子项不扩展改变它。

② 资源树采用可选 enabledOnly，缺省/false 与列表一致显示全部状态，管理页沿用状态标识；授权、接口映射和依赖选择器显式 true，保持原来仅启用可选（2026-10-04 确认）。

③ OrgResp 增加 parentOrgName，树/分页批量组装，前端信息卡与表单直接使用，不再依赖过滤后树查名；只补当前允许展示的父级名称，不扩大组织可见范围（2026-10-04 确认）。

## 非目标 / 遗留

- 实例裁剪链路（已规避该维度，验证即可）。

## 验收对照

- [x] 角色/资源树过滤后缺父子支提升为展示根，保留真实 parentId，不补回被过滤节点；组织树既有根过滤语义保持
- [x] 资源树 enabledOnly 缺省 false，管理显示全部状态，授权/映射/依赖选择器显式 true
- [x] OrgResp 提供允许展示的 parentOrgName，批量组装；前端信息卡/表单直接消费，根与不可见父级分开显示
- [x] 各子项的旧形态证据、定向验证和前端四项验证通过，契约 §8/§10.5/§12.1 已回写

## 完成记录

2026-10-04：定向、代码轨与文档轨核对完成；最终 `mvn test -T 1C` 2468 项零失败/错误/跳过，含 E2E 与 heavy。前端 487 项及 typecheck/lint/build 通过。[定向证据](evidence/T-ACCESS-065/verification.md)，[最终验收](evidence/pending-problems-clearance/final-audit.md)。
