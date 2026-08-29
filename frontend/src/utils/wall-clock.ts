/**
 * Date → 后端 LocalDateTime ISO 格式（YYYY-MM-DDTHH:mm:ss，取本地墙钟分量）。
 *
 * 数字对齐语义：el-date-picker 按浏览器本地分量构造 Date（用户键入/选择所见数字），
 * 表格 createdAt 原样展示后端 UTC 墙钟串，因此序列化取本地分量使「用户输入的数字 ==
 * 提交的数字 == 表格展示的数字」，按所见筛选即命中。注意快捷选项（如「今天」）按本地
 * 日历展开，与 UTC 日历存在时差——展示侧本地化改造（codex 2026-08-28 评审登记）前
 * 以数字一致为优先不变量。
 *
 * 消费方：操作日志（T-PERM-025）/ 权限变更日志（T-PERM-032）时间范围筛选。
 */
export function formatToWallClockIso(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  );
}
