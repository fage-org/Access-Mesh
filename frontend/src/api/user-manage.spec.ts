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

import { getOrgTreeConfigs, revokeRole } from "./user-manage";

const page = (items: unknown[], hasNext: boolean) => ({
  code: 200,
  message: "success",
  data: { items, total: 3, pageNum: 1, pageSize: 200, hasNext }
});

describe("撤回用户角色关系", () => {
  it("does not report a result when the post-revoke read fails", async () => {
    mockRequest
      .mockReset()
      .mockResolvedValueOnce({ code: 200, data: null })
      .mockRejectedValueOnce(new Error("read unavailable"));
    await expect(
      revokeRole({
        userId: 10,
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "report-reader",
        relationId: null
      })
    ).rejects.toThrow("read unavailable");
  });
  it.each([null, 200])("请求保留所选关系 %s", async relationId => {
    const remaining = [
      {
        roleTypeCode: "BASIC_ROLE",
        roleExternalId: "report-reader",
        relationId
      }
    ];
    mockRequest
      .mockReset()
      .mockResolvedValueOnce({ code: 200, data: null })
      .mockResolvedValueOnce({ code: 200, data: { items: remaining } });
    const actual = await revokeRole({
      userId: 10,
      roleTypeCode: "BASIC_ROLE",
      roleExternalId: "report-reader",
      relationId
    });
    expect(actual).toEqual(remaining);
    expect(mockRequest).toHaveBeenLastCalledWith(
      "post",
      "/api/access/user-role/view",
      { data: { userId: 10 } }
    );
    expect(mockRequest).toHaveBeenCalledWith(
      "post",
      "/api/access/user-role/revoke",
      {
        data: {
          items: [
            {
              subjectTypeCode: "LOCAL_USER",
              subjectExternalId: "10",
              roleTypeCode: "BASIC_ROLE",
              roleExternalId: "report-reader",
              relationId
            }
          ]
        }
      }
    );
  });
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
