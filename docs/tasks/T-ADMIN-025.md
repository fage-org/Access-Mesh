---
doc_type: task
id: T-ADMIN-025
title: 文件夹级授权（bizType 即文件夹实例，全链路 CREATE/VIEW/DELETE）
status: proposed
plan: docs/archive/2026-08-27/product-vertical-slice-plan.md
domain: admin-service
design_refs:
  - docs/design/services/admin-service-api-contract.md
  - docs/design/access-service-architecture.md
  - docs/design/schema/access-service.sql
depends_on: [T-ADMIN-023]
blocks: []
acceptance:
  - "文件夹实例产生：bootstrap 预置 default/avatar/document/image 四文件夹 ADMIN_FILE resource_entity 投影（空库即可授权）+ 上传新 bizType 时惰性登记投影（首次出现即成为可授权实例），无需管理界面"
  - "门禁全链路升级为文件夹实例级：upload=CREATE（目标文件夹）、detail/download=VIEW（文件所属文件夹）、delete=DELETE（按文件夹批量，resourceCode 从文件 ID 迁移为 bizType）、page 按可见文件夹过滤（scopeAll 命中全量返回，否则批量解析可见文件夹集合 + SQL bucket_name IN 过滤，无可见文件夹返回空页）"
  - "契约回写：admin-service-api-contract §4.7 门禁档位从类型级过渡升级为文件夹实例级（§4.7 已预留 VIEW 档位说明衔接）；架构文档 §15 演进方向更新；schema 注释同步"
  - "单测 + PG 用例：文件夹级授权放行/拒绝（含 fail-closed：无投影 bizType 拒绝）、page 过滤正确性、上传惰性登记、bootstrap 预置四文件夹"
design_writeback:
  required: true
  status: pending
last_updated: 2026-08-25
---

# T-ADMIN-025 文件夹级授权

> `plan` 字段（product-vertical-slice-plan.md）仅为来源溯源：本任务**不在该计划 `tasks:` 闭包内**
> （不阻塞 T-ACCESS-026 与该计划按 18 项收口），推进与收口按任务卡生命周期独立进行，必要时另立计划承载。

## 背景

T-ADMIN-023 执行中用户提出文件夹粒度授权设想（"用户甲只能上传到 A 和 B 文件夹"），经决策**另立本任务**（安全加固任务不膨胀交付面）。现状（T-ADMIN-023 终态）：文件全接口已按 `ADMIN_FILE` 门禁，但 detail/page/download 为类型级 VIEW 过渡、delete 实例级 resourceCode=文件 ID——ADMIN_FILE 无投影机制，实例级授权条目实际配不出来，放行全由 scopeAll 决定。

## 设计口径

- 粒度贯穿：**全链路 CREATE/VIEW/DELETE**（上传、查看、下载、page 过滤、删除均按文件夹隔离）。
- 文件夹实例源：**预置 + 惰性登记**——bootstrap 预置 default/avatar/document/image 四文件夹投影，上传新 bizType 惰性登记，无管理界面。
- bizType 格式白名单（`^[A-Za-z0-9_-]{1,32}$`）已在 T-ADMIN-023 落地，是文件夹 code 合法性的既有保证。

## 范围

- 上传惰性登记投影（upsert `resource_entity(ADMIN_FILE, code=bizType)`，事务内）+ bootstrap 预置四文件夹。
- 四链路门禁档位迁移（见 acceptance 第 2 条）；page 过滤管线（批量 `getDeniedResourceCodes` + mapper 按 bucket_name 集合过滤）。
- delete 的 resourceCode 语义迁移（文件 ID → bizType 集合，去重）。
- 契约/架构/schema 回写。

## 当前口径

- 文件夹 = `sys_file.bucket_name` = `resource_entity(ADMIN_FILE).code`，单事实源为投影表；不建文件夹管理界面。
- 历史存量文件的 bucketName 若不满足格式白名单（白名单前落库的脏数据）：无投影 → 实例级校验 fail-closed 拒绝，不自动修复。
- page 的 bizType 请求参数指向无权文件夹时静默返回空（过滤语义，非 403）。

## 非目标 / 遗留

- 不做文件夹管理界面（改名/禁用/删除文件夹）。
- 不做单文件粒度授权（文件夹即最小粒度）。
- 不解决多实例共享存储（见架构 §15，独立演进方向）。
- 符号链接逃逸（T-ADMIN-023 评审登记）：securePath 为词法 normalize、不解析 symlink，存储根内被植入指向外部的 symlink 仍可逃逸——单实例可信本地盘档位下接受；若本任务或后续放开存储根写入面，须显式评估（如 `toRealPath` 校验）。
