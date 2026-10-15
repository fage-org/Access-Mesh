import { ref } from "vue";
import { useTimeoutFn } from "@vueuse/core";
import type { DialogOptions } from "./type";

/**
 * 全局弹窗队列（自 index.ts 拆出，T-FE-068）：dialogStore 为模块级全局——挂 App 层
 * 的 ReDialog 渲染、跨路由存活（登出不清则旧上下文弹窗浮在登录页）。独立纯 ts 模块
 * 的原因：barrel（index.ts）会连带引入 index.vue，node 测试环境无 SFC 编译插件，
 * 会话终结方（store/modules/user.ts logOut）直接依赖本模块保持依赖链纯 ts。
 */
export const dialogStore = ref<Array<DialogOptions>>([]);

/** 打开弹框 */
export const addDialog = (options: DialogOptions) => {
  const open = () =>
    dialogStore.value.push(Object.assign(options, { visible: true }));
  if (options?.openDelay) {
    useTimeoutFn(() => {
      open();
    }, options.openDelay);
  } else {
    open();
  }
};

/** 关闭弹框 */
export const closeDialog = (
  options: DialogOptions,
  index: number,
  args?: any
) => {
  dialogStore.value[index].visible = false;
  options.closeCallBack && options.closeCallBack({ options, index, args });

  const closeDelay = options?.closeDelay ?? 200;
  useTimeoutFn(() => {
    dialogStore.value.splice(index, 1);
  }, closeDelay);
};

/**
 * @description 更改弹框自身属性值
 * @param value 属性值
 * @param key 属性，默认`title`
 * @param index 弹框索引（默认`0`，代表只有一个弹框，对于嵌套弹框要改哪个弹框的属性值就把该弹框索引赋给`index`）
 */
export const updateDialog = (value: any, key = "title", index = 0) => {
  dialogStore.value[index][key] = value;
};

/** 关闭所有弹框 */
export const closeAllDialog = () => {
  dialogStore.value = [];
};
