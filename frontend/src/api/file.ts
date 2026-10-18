import { http } from "@/utils/http";
import { unwrap, type R } from "./_envelope";
import type { PageResp } from "./role-manage";

export type FileResp = {
  id: number;
  fileName: string;
  originalName: string;
  fileSuffix: string | null;
  fileSize: string;
  fileType: string | null;
  storagePath: string;
  createdAt: string;
  bizType: string;
};

export const pageFiles = async (
  pageNum: number,
  pageSize: number,
  bizType?: string
) =>
  unwrap(
    await http.request<R<PageResp<FileResp>>>("post", "/api/access/file/page", {
      data: { pageNum, pageSize, bizType }
    })
  );
export const getFile = async (id: number) =>
  unwrap(
    await http.request<R<FileResp>>("post", "/api/access/file/detail", {
      data: { id }
    })
  );
export const deleteFile = async (id: number) =>
  unwrap(
    await http.request<R<void>>("post", "/api/access/file/delete", {
      data: { ids: [id] }
    })
  );

export async function uploadFile(
  file: File,
  bizType: string,
  signal?: AbortSignal
) {
  const data = new FormData();
  data.append("file", file);
  data.append("bizType", bizType);
  return unwrap(
    await http.request<R<number>>("post", "/api/access/file/upload", {
      data,
      headers: { "Content-Type": undefined },
      timeout: 90000,
      signal
    })
  );
}
export async function downloadFile(
  id: number,
  signal?: AbortSignal
): Promise<Blob> {
  const rejectEnvelope = async (blob: Blob): Promise<never> => {
    unwrap(JSON.parse(await blob.text()) as R<unknown>);
    throw new Error("文件下载响应格式不正确");
  };
  let attachment = false;
  const blob = await http.request<Blob>("post", "/api/access/file/download", {
    data: { id },
    responseType: "blob",
    timeout: 90000,
    signal,
    beforeResponseCallback: response => {
      attachment = /^\s*attachment(?:\s*;|$)/i.test(
        String(response.headers["content-disposition"] ?? "")
      );
    }
  });
  if (!attachment && blob instanceof Blob && blob.type.includes("json"))
    return rejectEnvelope(blob);
  if (!(blob instanceof Blob)) throw new Error("文件下载响应格式不正确");
  return blob;
}
