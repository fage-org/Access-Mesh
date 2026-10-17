import { ENUM_PAGE_SIZE } from "@/constants/access";
import {
  getTypeDefList,
  type TypeDefListQuery,
  type TypeDefResp
} from "@/api/type-def";

/** 完整候选统一入口：续页失败整体抛出，避免把部分候选误作完整目录。 */
export async function loadAllTypeDefs(
  query: Omit<TypeDefListQuery, "pageNum" | "pageSize">
): Promise<TypeDefResp[]> {
  const items: TypeDefResp[] = [];
  for (let pageNum = 1; ; pageNum++) {
    const page = await getTypeDefList({
      ...query,
      pageNum,
      pageSize: ENUM_PAGE_SIZE
    });
    items.push(...page.items);
    if (!page.hasNext) return items;
  }
}
