# 前端依赖核实与验证

2026-10-17：原始报告 high=77、critical=2；升级后原始报告 high=1、critical=0。剩余 braces 公告为已批准的开发依赖临时例外，不声称漏洞清零。low/moderate 仍完整保留在报告中，按当前 high 阈值不阻断。

## 触发边界

- 浏览器运行依赖包含 Vue、Axios、js-cookie、lodash-es；包存在不等于公告的全部服务端/原型污染前提在浏览器成立。Axios HTTP 代理/重定向公告涉及 Node 适配器；项目 HTTP 客户端位于 `frontend/src/utils/http/index.ts`。统一升级到修复版本，不为这些条目添加忽略。
- Vue server-renderer 随 Vue 安装，但当前前端为 Vite 浏览器应用，没有 SSR 服务入口；统一升级 Vue 内部组件到 3.5.44。
- 文件匹配、CSS/SVG 解析、打包、lint 和测试工具的公告需相应恶意文件、匹配表达式或原型污染前提；这些属于开发/CI 输入面，仍参与完整扫描。
- tinypool 经 Vitest 3 引入，升级 Vitest 5.0.5 后从锁文件移除；未强制覆盖 Vitest 3 的内部依赖。
- braces 临时例外的唯一权威见 [安全规则](../../../../../../.claude/rules/security-standards.md#braces-开发依赖临时例外)，门禁保留原报告、检查开发路径/版本及新正式版发布；进入外部输入路径时必须重新决策。

## 逐公告处置

同一公告的多个受影响版本范围合并列示；“开发”来自 audit finding，“含生产”表示依赖路径包含生产依赖，不表示已验证线上可利用。所有清除项均以升级后完整原始报告不再命中为证据。

| 公告 | 包 / 原锁版本 | 当前锁版本 | 依赖范围 | 结果 |
|---|---|---|---|---|
| [GHSA-5j98-mcp5-4vw2](https://github.com/advisories/GHSA-5j98-mcp5-4vw2) | glob / 11.0.3 | 11.1.0 | 开发 | 升级后扫描不再命中 |
| [GHSA-7h2j-956f-4vf2](https://github.com/advisories/GHSA-7h2j-956f-4vf2) | @isaacs/brace-expansion / 5.0.0 | 已移除 | 开发 | 升级后扫描不再命中 |
| [GHSA-3ppc-4f35-3m26](https://github.com/advisories/GHSA-3ppc-4f35-3m26) | minimatch / 3.1.2, 9.0.5, 10.0.3 | 10.2.6, 3.1.5, 9.0.9 | 开发 | 升级后扫描不再命中 |
| [GHSA-mw96-cpmx-2vgc](https://github.com/advisories/GHSA-mw96-cpmx-2vgc) | rollup / 4.52.5 | 4.64.7 | 开发 | 升级后扫描不再命中 |
| [GHSA-7r86-cg39-jmmj](https://github.com/advisories/GHSA-7r86-cg39-jmmj) | minimatch / 3.1.2, 9.0.5, 10.0.3 | 10.2.6, 3.1.5, 9.0.9 | 开发 | 升级后扫描不再命中 |
| [GHSA-23c5-xmqv-rm74](https://github.com/advisories/GHSA-23c5-xmqv-rm74) | minimatch / 3.1.2, 9.0.5, 10.0.3 | 10.2.6, 3.1.5, 9.0.9 | 开发 | 升级后扫描不再命中 |
| [GHSA-xpqw-6gx7-v673](https://github.com/advisories/GHSA-xpqw-6gx7-v673) | svgo / 4.0.0, 3.3.2 | 3.3.5, 4.1.0 | 开发 | 升级后扫描不再命中 |
| [GHSA-25h7-pfq9-p65f](https://github.com/advisories/GHSA-25h7-pfq9-p65f) | flatted / 3.3.3 | 3.4.4 | 开发 | 升级后扫描不再命中 |
| [GHSA-c2c7-rcm5-vvqj](https://github.com/advisories/GHSA-c2c7-rcm5-vvqj) | picomatch / 2.3.1, 4.0.3 | 2.3.2, 4.0.7 | 含生产 | 升级后扫描不再命中 |
| [GHSA-j3q9-mxjg-w52f](https://github.com/advisories/GHSA-j3q9-mxjg-w52f) | path-to-regexp / 8.3.0 | 8.4.2 | 开发 | 升级后扫描不再命中 |
| [GHSA-r5fr-rjxr-66jc](https://github.com/advisories/GHSA-r5fr-rjxr-66jc) | lodash-es / 4.17.21 | 4.18.1 | 含生产 | 升级后扫描不再命中 |
| [GHSA-r5fr-rjxr-66jc](https://github.com/advisories/GHSA-r5fr-rjxr-66jc) | lodash / 4.17.21 | 4.18.1 | 含生产 | 升级后扫描不再命中 |
| [GHSA-737v-mqg7-c878](https://github.com/advisories/GHSA-737v-mqg7-c878) | defu / 6.1.4 | 6.1.7 | 含生产 | 升级后扫描不再命中 |
| [GHSA-v2wj-q39q-566r](https://github.com/advisories/GHSA-v2wj-q39q-566r) | vite / 7.1.12 | 7.3.7 | 开发 | 升级后扫描不再命中 |
| [GHSA-p9ff-h696-f583](https://github.com/advisories/GHSA-p9ff-h696-f583) | vite / 7.1.12 | 7.3.7 | 开发 | 升级后扫描不再命中 |
| [GHSA-wf6x-7x77-mvgw](https://github.com/advisories/GHSA-wf6x-7x77-mvgw) | immutable / 5.1.4 | 5.1.9 | 开发 | 升级后扫描不再命中 |
| [GHSA-pmwg-cvhr-8vh7](https://github.com/advisories/GHSA-pmwg-cvhr-8vh7) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-pf86-5x62-jrwf](https://github.com/advisories/GHSA-pf86-5x62-jrwf) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-6chq-wfr3-2hj9](https://github.com/advisories/GHSA-6chq-wfr3-2hj9) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-43fc-jf86-j433](https://github.com/advisories/GHSA-43fc-jf86-j433) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-q8qp-cvcw-x6jj](https://github.com/advisories/GHSA-q8qp-cvcw-x6jj) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-hfxv-24rg-xrqf](https://github.com/advisories/GHSA-hfxv-24rg-xrqf) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-qjx8-664m-686j](https://github.com/advisories/GHSA-qjx8-664m-686j) | js-cookie / 3.0.5 | 3.0.8 | 含生产 | 升级后扫描不再命中 |
| [GHSA-777c-7fjr-54vf](https://github.com/advisories/GHSA-777c-7fjr-54vf) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-p92q-9vqr-4j8v](https://github.com/advisories/GHSA-p92q-9vqr-4j8v) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-j5f8-grm9-p9fc](https://github.com/advisories/GHSA-j5f8-grm9-p9fc) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-3g43-6gmg-66jw](https://github.com/advisories/GHSA-3g43-6gmg-66jw) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-35jp-ww65-95wh](https://github.com/advisories/GHSA-35jp-ww65-95wh) | axios / 1.12.2 | 1.20.0 | 含生产 | 升级后扫描不再命中 |
| [GHSA-hmw2-7cc7-3qxx](https://github.com/advisories/GHSA-hmw2-7cc7-3qxx) | form-data / 4.0.4 | 4.0.6 | 含生产 | 升级后扫描不再命中 |
| [GHSA-fx2h-pf6j-xcff](https://github.com/advisories/GHSA-fx2h-pf6j-xcff) | vite / 7.1.12 | 7.3.7 | 开发 | 升级后扫描不再命中 |
| [GHSA-3jxr-9vmj-r5cp](https://github.com/advisories/GHSA-3jxr-9vmj-r5cp) | brace-expansion / 2.0.2, 1.1.12 | 1.1.21, 2.1.7, 5.0.12 | 开发 | 升级后扫描不再命中 |
| [GHSA-52cp-r559-cp3m](https://github.com/advisories/GHSA-52cp-r559-cp3m) | js-yaml / 4.1.0 | 4.3.2 | 开发 | 升级后扫描不再命中 |
| [GHSA-v2hh-gcrm-f6hx](https://github.com/advisories/GHSA-v2hh-gcrm-f6hx) | fast-uri / 3.1.0 | 3.1.8 | 开发 | 升级后扫描不再命中 |
| [GHSA-6g55-p6wh-862q](https://github.com/advisories/GHSA-6g55-p6wh-862q) | postcss / 8.5.6 | 8.5.30 | 含生产 | 升级后扫描不再命中 |
| [GHSA-mh99-v99m-4gvg](https://github.com/advisories/GHSA-mh99-v99m-4gvg) | brace-expansion / 1.1.12, 2.0.2 | 1.1.21, 2.1.7, 5.0.12 | 开发 | 升级后扫描不再命中 |
| [GHSA-7p8r-x3mc-p8w7](https://github.com/advisories/GHSA-7p8r-x3mc-p8w7) | fast-uri / 3.1.0 | 3.1.8 | 开发 | 升级后扫描不再命中 |
| [GHSA-rgw5-rvv9-x895](https://github.com/advisories/GHSA-rgw5-rvv9-x895) | brace-expansion / 2.0.2, 1.1.12 | 1.1.21, 2.1.7, 5.0.12 | 开发 | 升级后扫描不再命中 |
| [GHSA-5p4m-2wfm-xmqj](https://github.com/advisories/GHSA-5p4m-2wfm-xmqj) | js-yaml / 4.1.0 | 4.3.2 | 开发 | 升级后扫描不再命中 |
| [GHSA-28wg-ghj8-5hjv](https://github.com/advisories/GHSA-28wg-ghj8-5hjv) | nanoid / 3.3.11 | 3.3.20 | 含生产 | 升级后扫描不再命中 |
| [GHSA-2v37-7h3g-55p8](https://github.com/advisories/GHSA-2v37-7h3g-55p8) | nanoid / 3.3.11 | 3.3.20 | 含生产 | 升级后扫描不再命中 |
| [GHSA-r28c-9q8g-f849](https://github.com/advisories/GHSA-r28c-9q8g-f849) | postcss / 8.5.6 | 8.5.30 | 含生产 | 升级后扫描不再命中 |
| [GHSA-2p49-hgcm-8545](https://github.com/advisories/GHSA-2p49-hgcm-8545) | svgo / 4.0.0, 3.3.2 | 3.3.5, 4.1.0 | 开发 | 升级后扫描不再命中 |
| [GHSA-q3j6-qgpj-74h6](https://github.com/advisories/GHSA-q3j6-qgpj-74h6) | fast-uri / 3.1.0 | 3.1.8 | 开发 | 升级后扫描不再命中 |
| [GHSA-v39h-62p7-jpjc](https://github.com/advisories/GHSA-v39h-62p7-jpjc) | fast-uri / 3.1.0 | 3.1.8 | 开发 | 升级后扫描不再命中 |
| [GHSA-c83g-rgw3-j3cx](https://github.com/advisories/GHSA-c83g-rgw3-j3cx) | browserslist / 4.27.0 | 4.29.3 | 开发 | 升级后扫描不再命中 |
| [GHSA-73wf-gq98-2v4g](https://github.com/advisories/GHSA-73wf-gq98-2v4g) | browserslist / 4.27.0 | 4.29.3 | 开发 | 升级后扫描不再命中 |
| [GHSA-xwg4-73v4-xw9w](https://github.com/advisories/GHSA-xwg4-73v4-xw9w) | nanoid / 3.3.11 | 3.3.20 | 含生产 | 升级后扫描不再命中 |
| [GHSA-qxc2-j82w-r537](https://github.com/advisories/GHSA-qxc2-j82w-r537) | @faker-js/faker / 10.1.0 | 10.6.0 | 开发 | 升级后扫描不再命中 |
| [GHSA-f65p-4m7j-42xc](https://github.com/advisories/GHSA-f65p-4m7j-42xc) | fast-uri / 3.1.0 | 3.1.8 | 开发 | 升级后扫描不再命中 |
| [GHSA-jqff-g426-hqxp](https://github.com/advisories/GHSA-jqff-g426-hqxp) | fast-uri / 3.1.0 | 3.1.8 | 开发 | 升级后扫描不再命中 |
| [GHSA-xvcm-6775-5m9r](https://github.com/advisories/GHSA-xvcm-6775-5m9r) | immutable / 5.1.4 | 5.1.9 | 开发 | 升级后扫描不再命中 |
| [GHSA-2883-xcg3-v3hh](https://github.com/advisories/GHSA-2883-xcg3-v3hh) | js-yaml / 4.1.0 | 4.3.2 | 开发 | 升级后扫描不再命中 |
| [GHSA-w27v-7q3p-w38r](https://github.com/advisories/GHSA-w27v-7q3p-w38r) | svgo / 4.0.0, 3.3.2 | 3.3.5, 4.1.0 | 开发 | 升级后扫描不再命中 |
| [GHSA-4c8g-83qw-93j6](https://github.com/advisories/GHSA-4c8g-83qw-93j6) | fast-uri / 3.1.0 | 3.1.8 | 开发 | 升级后扫描不再命中 |
| [GHSA-v56q-mh7h-f735](https://github.com/advisories/GHSA-v56q-mh7h-f735) | immutable / 5.1.4 | 5.1.9 | 开发 | 升级后扫描不再命中 |
| [GHSA-qw65-cvwx-89v3](https://github.com/advisories/GHSA-qw65-cvwx-89v3) | fast-uri / 3.1.0 | 3.1.8 | 开发 | 升级后扫描不再命中 |
| [GHSA-qhr7-859c-m2p7](https://github.com/advisories/GHSA-qhr7-859c-m2p7) | brace-expansion / 1.1.12, 2.0.2 | 1.1.21, 2.1.7, 5.0.12 | 开发 | 升级后扫描不再命中 |
| [GHSA-6j4f-fj2g-mc7p](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p) | brace-expansion / 1.1.12, 2.0.2 | 1.1.21, 2.1.7, 5.0.12 | 开发 | 升级后扫描不再命中 |
| [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) | braces / 3.0.3 | 3.0.3 | 开发 | 已批准临时例外 |
| [GHSA-rf6f-7fwh-wjgh](https://github.com/advisories/GHSA-rf6f-7fwh-wjgh) | flatted / 3.3.3 | 3.4.4 | 开发 | 升级后扫描不再命中 |
| [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q) | source-map-js / 1.2.1 | 1.2.2 | 含生产 | 升级后扫描不再命中 |
| [GHSA-g2v6-rqmx-r4w6](https://github.com/advisories/GHSA-g2v6-rqmx-r4w6) | @vue/server-renderer / 3.5.22 | 3.5.44 | 含生产 | 升级后扫描不再命中 |
| [GHSA-5gmw-xhrv-c9v3](https://github.com/advisories/GHSA-5gmw-xhrv-c9v3) | tinypool / 1.1.1 | 已移除 | 开发 | 升级后扫描不再命中 |
| [GHSA-85c8-ppgw-ccpr](https://github.com/advisories/GHSA-85c8-ppgw-ccpr) | tinypool / 1.1.1 | 已移除 | 开发 | 升级后扫描不再命中 |

## 本地验证

- 2026-10-17，`pnpm --dir frontend test`（Vitest 5.0.5）：50 文件、562 项通过；未更改测试断言。
- `pnpm --dir frontend typecheck`、`pnpm --dir frontend lint:check`、`pnpm --dir frontend build`、`pnpm --dir frontend contracts:check`（44 组）通过；`pnpm --dir frontend install --frozen-lockfile` 通过。
- `node --test tools/security/check-pnpm-audit.test.mjs`：安全门禁边界测试 5 项通过。
- 同一门禁回放升级前原始报告：退出码 1、阻断 78 个 high/critical 条目，仅接受已批准的 braces 条目；证明例外没有吞掉其他存量漏洞。
- `pnpm --dir frontend audit --audit-level high --json` 原始退出码 1；`node tools/security/check-pnpm-audit.mjs <原始报告> 1` 退出码 0，accepted=[GHSA-vfj7-8cjw-p6xm]、blocked=[]。原始报告见 [pnpm-audit-after-2026-10-17.json](pnpm-audit-after-2026-10-17.json)。
- `pnpm --dir frontend audit --prod --json`：生产依赖 high=0、critical=0；low=1、moderate=4 保留，未认定这些公告均有可触发业务通路。
- 后端迁移后再次执行前端审计和 44 组 DTO 对账，通过；审计 JSON 与上述保存报告内容一致，[例外判定](pnpm-risk-decision-2026-10-17.json) 保留。Maven 本地扫描另见 [Maven 证据](maven-remediation.md)，[真实 CI 验收](ci-verification-2026-10-17.json)已完成，原始报告及批准例外保留。

## 本地评审

代码轨：核对升级后的锁定依赖、Vitest 隔离配置保持、图标名称注册与组件渲染调用、例外仅作用于指定公告/版本/开发入口、新正式版本触发复核及其他高危失败保留；补齐 CI 已声明但缺失的 `pnpm build`。当前前端范围无未处理 P0–P2。

文档轨：核对 Node 支持与 quickstart/镜像/工程基线一致，批准例外归安全规则，原始漏洞数和例外结果分别保存，任务状态与计划/看板一致且未提前验收；变更文件链接与过程词扫描通过。整体回归与 CI 验收由 T-ACCESS-105 独立记录，未用前端通过代替整体收口。
