/**
 * 组织树配置选择器分页循环锁（2026-10-06 拍板：getOrgTreeConfigs 按 hasNext 循环拉全）。
 *
 * 锁住的性质：后端单页上限 200，只取首页会静默截断超页配置（选择器漏显且无提示）。
 * 历史形态：固定 {pageNum:1, pageSize:100} 单页取数——配置超 100 个后第 101 个起永不可见。
 * 本 spec 在「只取首页」旧实现下必红（第二页断言失败），对齐 loadAllRoles 范式先例。
 */
import { describe, it, expect, beforeEach, vi } from "vitest";

const { mockRequest } = vi.hoisted(() => ({ mockRequest: vi.fn() }));
vi.mock("@/utils/http", () => ({ http: { request: mockRequest } }));

import { getOrgTreeConfigs } from "./user-manage";

const page = (items: unknown[], hasNext: boolean) => ({
  code: 200,
  message: "success",
  data: { items, total: 3, pageNum: 1, pageSize: 200, hasNext }
});

describe("getOrgTreeConfigs 分页循环拉全", () => {
  beforeEach(() => {
    mockRequest.mockReset();
  });

  it("首页 hasNext=true → 继续翻页至拉全（旧实现只取首页必红）", async () => {
    mockRequest
      .mockResolvedValueOnce(page([{ id: 1 }, { id: 2 }], true))
      .mockResolvedValueOnce(page([{ id: 3 }], false));
    const configs = await getOrgTreeConfigs();
    expect(configs).toHaveLength(3);
    expect(mockRequest).toHaveBeenCalledTimes(2);
    expect(mockRequest.mock.calls[0][2].data).toEqual({
      pageNum: 1,
      pageSize: 200
    });
    expect(mockRequest.mock.calls[1][2].data).toEqual({
      pageNum: 2,
      pageSize: 200
    });
  });

  it("首页 hasNext=false → 单页即止", async () => {
    mockRequest.mockResolvedValueOnce(page([{ id: 1 }], false));
    const configs = await getOrgTreeConfigs();
    expect(configs).toHaveLength(1);
    expect(mockRequest).toHaveBeenCalledTimes(1);
  });
});
