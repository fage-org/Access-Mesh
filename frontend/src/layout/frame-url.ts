/** 为 iframe 刷新增加查询参数，保留子应用的 hash 路由。 */
export function refreshFrameUrl(
  source: string,
  origin: string,
  timestamp: number
) {
  const url = new URL(source, origin);
  url.searchParams.set("t", String(timestamp));
  return url.href;
}
