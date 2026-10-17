import { isUrl } from "@pureadmin/utils";

/** 外链兼容模板的 name=URL 节点；现役菜单使用 path。 */
export function externalMenuHref(item: {
  path?: string;
  name?: string;
}): string | undefined {
  if (isUrl(item.path)) return item.path;
  return isUrl(item.name) ? String(item.name) : undefined;
}
