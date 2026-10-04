// 这里存放本地图标，在 src/layout/index.vue 文件中加载，避免在首启动加载
import { getSvgInfo } from "@pureadmin/utils";
import { addIcon } from "@iconify/vue/dist/offline";

// https://icon-sets.iconify.design/ep/?keyword=ep
import EpHomeFilled from "~icons/ep/home-filled?raw";
import EpSetting from "~icons/ep/setting?raw";
import EpUser from "~icons/ep/user?raw";
import EpUserFilled from "~icons/ep/user-filled?raw";
import EpFiles from "~icons/ep/files?raw";
import EpTools from "~icons/ep/tools?raw";
import EpDocument from "~icons/ep/document?raw";
import EpOfficeBuilding from "~icons/ep/office-building?raw";
import EpConnection from "~icons/ep/connection?raw";
import EpCoin from "~icons/ep/coin?raw";
import EpKey from "~icons/ep/key?raw";
import EpWarnTriangleFilled from "~icons/ep/warn-triangle-filled?raw";
import EpShare from "~icons/ep/share?raw";
import EpClock from "~icons/ep/clock?raw";
// 空侧栏占位项两态图标（T-FE-049）：MENU_LOAD_RETRY_ITEM/页内 warning-filled 与
// MENU_EMPTY_ITEM/页内 menu——IconifyIconOffline 按 storage 查找，未注册即渲染空
import EpWarningFilled from "~icons/ep/warning-filled?raw";
import EpMenu from "~icons/ep/menu?raw";

// https://icon-sets.iconify.design/ri/?keyword=ri
import RiSearchLine from "~icons/ri/search-line?raw";
import RiInformationLine from "~icons/ri/information-line?raw";

const icons = [
  // Element Plus Icon: https://github.com/element-plus/element-plus-icons
  ["ep/home-filled", EpHomeFilled],
  ["ep/setting", EpSetting],
  ["ep/user", EpUser],
  ["ep/user-filled", EpUserFilled],
  ["ep/files", EpFiles],
  ["ep/tools", EpTools],
  ["ep/document", EpDocument],
  ["ep/office-building", EpOfficeBuilding],
  ["ep/connection", EpConnection],
  // 菜单既有键保留；Element Plus 图形名为 coin / clock。
  ["ep/coins", EpCoin],
  ["ep/key", EpKey],
  ["ep/warn-triangle-filled", EpWarnTriangleFilled],
  ["ep/share", EpShare],
  ["ep/history", EpClock],
  ["ep/warning-filled", EpWarningFilled],
  ["ep/menu", EpMenu],
  // Remix Icon: https://github.com/Remix-Design/RemixIcon
  ["ri/search-line", RiSearchLine],
  ["ri/information-line", RiInformationLine]
];

// 本地菜单图标，后端在路由的 icon 中返回对应的图标字符串并且前端在此处使用 addIcon 添加即可渲染菜单图标
icons.forEach(([name, icon]) => {
  addIcon(name as string, getSvgInfo(icon as string));
});
