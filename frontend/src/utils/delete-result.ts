import type { BatchDeleteResp } from "@/api/batch-delete";

export function deleteResultNotice(result: BatchDeleteResp<unknown>): {
  text: string;
  type: "success" | "warning" | "info";
} {
  const { deleted, denied, notFound } = result;
  return {
    text: `已删除 ${deleted.length} 项，无权跳过 ${denied.length} 项，不存在 ${notFound.length} 项`,
    type:
      denied.length > 0 ? "warning" : deleted.length > 0 ? "success" : "info"
  };
}
