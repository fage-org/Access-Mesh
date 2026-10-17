import { expect, it } from "vitest";
import { deleteResultNotice } from "./delete-result";

it.each([
  { deleted: [123], denied: [], notFound: [], type: "success" },
  { deleted: [123], denied: [456], notFound: [789], type: "warning" },
  { deleted: [], denied: [], notFound: [123], type: "info" }
])("删除反馈按实际结果披露 $type", ({ type, ...result }) => {
  expect(deleteResultNotice(result)).toEqual({
    text: `已删除 ${result.deleted.length} 项，无权跳过 ${result.denied.length} 项，不存在 ${result.notFound.length} 项`,
    type
  });
});
