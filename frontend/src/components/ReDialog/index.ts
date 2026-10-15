import reDialog from "./index.vue";
import { withInstall } from "@pureadmin/utils";
import type {
  EventType,
  ArgsType,
  DialogProps,
  ButtonProps,
  DialogOptions
} from "./type";

// 弹窗队列与开/关 API 在 store.ts（T-FE-068 拆出：会话终结方直接依赖纯 ts 模块，
// 不经本 barrel 连带引入 SFC）；本文件保留组件注册与既有导入路径兼容
export {
  dialogStore,
  addDialog,
  closeDialog,
  updateDialog,
  closeAllDialog
} from "./store";

/**
 * 千万别忘了在下面这三处引入并注册下，放心注册，不使用`addDialog`调用就不会被挂载
 * https://github.com/pure-admin/vue-pure-admin/blob/main/src/App.vue#L4
 * https://github.com/pure-admin/vue-pure-admin/blob/main/src/App.vue#L12
 * https://github.com/pure-admin/vue-pure-admin/blob/main/src/App.vue#L22
 */
const ReDialog = withInstall(reDialog);

export type { EventType, ArgsType, DialogProps, ButtonProps, DialogOptions };
export { ReDialog };
