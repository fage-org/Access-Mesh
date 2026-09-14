---
doc_type: task
id: T-PERM-067
title: USER 写入口自身豁免收窄——启停/删除不豁免，档案字段与自助改密保留
status: done
plan: —（Q-002 单卡转出）
domain: permission-center
design_refs:
  - docs/design/access-service-api-contract.md#§7.4/§7.5/§7.6/§7.7（管理面 user 端点）
  - docs/design/access-service-api-contract.md#§4（门禁规范自身豁免口径）
  - docs/design/access-service-api-contract.md#§7.8（abstract-user update/remove 门禁表）
depends_on: []
blocks: []
acceptance:
  - "admin 轨 /user/update：自身档案字段（name/phone/email）豁免保留；自身 status 变更不豁免——status=0 对齐 /user/enable 的 CANNOT_DISABLE_SELF 硬禁、status=1 须持 USER:ENABLE（消除同规则两入口相反结果）"
  - "perm 轨 abstract-user/update：自身 enabled 变更不豁免（须持 USER:ENABLE）；name/extra 自身豁免保留（死分支语义统一——操作者==目标必被 rejectIfLocalUser 先拒）"
  - "perm 轨 abstract-user/remove：删除 nonSelfUserIds 特例，门禁改查全量 existing ids（与 admin 轨 CANNOT_DELETE_SELF 硬禁口径一致化：自身不再被静默剔除）"
  - "/user/reset-password 自身豁免保留并文档定位为自助改密通道；修正「系统无自助改密通道」错误注释；旧密码验证另立任务不扩本批"
  - "回归锁三面：自身启停拒（旧实现下通过=锁住）、自身档案豁免保留（verify 门禁不触发）、删除门禁含自身（旧实现剔除自身=锁住）"
  - "设计回写：decision-registry 定案行 + 契约总册 §4 表/§7.4/§7.7/§7.8/§21.2/§22.2 决策 13 自身豁免口径 + pending-problems Q-002 收敛"
design_writeback:
  required: true
  status: done
last_updated: 2026-09-14
---

# T-PERM-067 USER 写入口自身豁免收窄（Q-002 转出）

## 背景

Q-002（2026-09-13 登记）：两轨用户写入口对「操作者 == 目标用户」整段跳过门禁。T-ACCESS-034 字段分档化（UPDATE/ENABLE/DELETE/RESET_PASSWORD）后自身路径仍零操作位即可改自己档案/启停/删除。

## 盘点核实（2026-09-14，实施前）

- **可达暴露面全在 admin 轨**：`/user/update` 自身全免 UPDATE+ENABLE 门禁（`UserWriteAppServiceImpl:174`），且旁路 `/user/enable` 的 CANNOT_DISABLE_SELF 硬禁（同规则两入口相反结果）；`/user/reset-password` 自身全免 RESET_PASSWORD 门禁（`UserAppServiceImpl:354`，无旧密码验证）。
- **perm 轨两处豁免为不可达死分支**：操作者==目标 ⇒ 目标必为登录者自身 LOCAL_USER ⇒ 前置 `rejectIfLocalUser` 先拒（`UserManageAppServiceImpl:276/:345`）。但 `deleteUsers` 的 nonSelfUserIds 静默剔除与 admin 轨硬禁口径不一致，属语义债。
- **已安全无需动**：admin `/user/delete`（CANNOT_DELETE_SELF 硬禁）、admin `/user/enable`（硬禁自禁 + 自启用仍过门禁）、读面 `/user/user-menus` 自查豁免（登录导航必需）。

## 定案（2026-09-14 用户拍板）

1. **收窄为档案字段**：档案字段（admin: name/phone/email；perm: name/extra）自身豁免保留；启停与删除不豁免。
2. **resetPassword 保留豁免并定位自助改密通道**：修正「系统无自助改密通道」错误注释；旧密码验证另立任务。

## 非目标 / 遗留

- resetPassword 旧密码验证（自助通道安全加固）——另立任务。
- 前端个人中心自助资料页——当前零 UX 依赖豁免面，需要时另立前端任务。

## 完成记录

**实施（2026-09-14）**：

- 主代码 3 文件：`UserWriteAppServiceImpl.updateUser`（status 值域校验前移至门禁前 + 自身 status=0 硬拒 CANNOT_DISABLE_SELF + status=1/非自身 ENABLE 门禁 + 档案字段仅非自身查 UPDATE）、`UserManageAppServiceImpl`（updateUser 的 ENABLE 门禁移出自身包裹 + name/extra 门禁仅非自身；deleteUsers 删 nonSelfUserIds 特例、门禁查全量 existing ids）、`UserAppServiceImpl`（resetPassword 注释定位自助改密通道，零行为变更；类头/updateUser javadoc 同步）。
- 同心圆注释 4 处：`AdminPermissionValidator` javadoc 收窄括注、前端 `user-manage.ts` updateUser 注释、`login/index.vue` 与 `docs/design/frontend/login.md` 的「系统暂无自助改密通道」改「前端暂无自助改密 UI（API 层自助通道为 /user/reset-password 自身路径）」。
- 回归锁：`UserWriteAppServiceUpdateGateTest` 自身面 4 用例（档案豁免保留/自禁硬拒/自启用过门禁/自启用持码放行——旧实现下前两类必红）+ 两既有用例改锁 ENABLE 先行短路序；`UserManageAppServiceImplTest` 自身 enabled 拒 + 档案豁免 + 删除全量门禁参数锁 + 自身 DELETE 拒整批 4 用例（旧实现下必红）；新 `UserAppServiceResetPasswordGateTest` 4 用例（自身免门禁 verify never/非自身查码/持码放行随机密码 12 位/目标不存在 BizException）。
- 首轮收口回归担出 `LoginLockTemporaryPgIT.userUpdateRejectsStatusOutsideZeroAndOne` 失败：自身 status=2 旧形态 200/10008（注释明言依赖「自我修改豁免门禁」）被门禁重排变 403——即代码轨评审 P3-3 的实锤。处置：status 值域校验前移至门禁前（对齐 updateStatus 先验参后门禁先例，自身/非自身非法值统一 10008 业务拒绝）；测试第二段改锁新语义（自身合法值 1 零操作位 → 403 且库值不变）。隔离复跑 9/9 绿。
- 双轨评审处置：代码轨零 P0-P1（P2-1=归档时序与本收口同批闭合；P3-1/3-2 注释残留已修；P3-3 报错顺序已如上处置）；文档轨 P1-1 死锚点（§12.2/附录 A → §7.8/§22.2 决策 13，四处传播全修）、P1-2/P2-1 终态时序随本收口闭合、P2-2 acceptance 清单已修、P3-1 capability-structure 前向指针【已收敛】内联标记已加、P3-2 看板链接随归档落 archive 链接、P3-3 AdminPermissionValidator 括注已加。
- 收口全量回归 `mvn test -T 1C`（含 E2E）BUILD SUCCESS 全绿：perm-common 30 / common 65+15+10+106 / access-service 1249 单测 + 210 容器 / e2e 14，零失败。

**行为变化清单**（未部署零存量迁移）：

1. `/user/update` 自身 status=0：放行 → 硬拒 CANNOT_DISABLE_SELF（10166 段）。
2. `/user/update` 自身 status=1：放行 → 须持 USER:ENABLE（403）。
3. `/user/update` 非自身 + 非法 status 值：403（门禁先行）→ 200/10008（验参先行，对齐 /user/enable）。
4. `abstract-user/update`、`abstract-user/remove` 自身分支语义统一（生产面不可达，零行为差异）。
