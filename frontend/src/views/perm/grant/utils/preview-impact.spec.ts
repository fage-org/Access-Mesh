import { describe, expect, it } from "vitest";
import {
  conditionLabel,
  previewFactLabel,
  seedLabel,
  hasNoImpact,
  previewFailureText,
  PREVIEW_BOUNDARY_NOTES
} from "./preview-impact";
import type { GrantPlanPreviewResp } from "@/api/permission-grant";

function respOf(
  overrides: Partial<GrantPlanPreviewResp>
): GrantPlanPreviewResp {
  return {
    advisory: true,
    viewedAt: "2026-09-21T00:00:00",
    removed: [],
    added: [],
    retained: [],
    removedTotal: 0,
    addedTotal: 0,
    retainedTotal: 0,
    totalCount: 0,
    truncated: false,
    driftDetected: false,
    ...overrides
  };
}

const noneFact = {
  resource: {
    resourceTypeCode: "REPORT",
    resourceCode: "template",
    codeType: "default"
  },
  operationCode: "VIEW",
  conditionRef: {
    kind: "NONE",
    conditionId: null,
    conditionCode: null,
    requestItemRef: null
  }
} as const;

describe("conditionLabel（条件身份展示）", () => {
  it("NONE → 无条件", () => {
    expect(
      conditionLabel({
        kind: "NONE",
        conditionId: null,
        conditionCode: null,
        requestItemRef: null
      })
    ).toBe("无条件");
  });

  it("EXISTING → 条件 code（可见描述）", () => {
    expect(
      conditionLabel({
        kind: "EXISTING",
        conditionId: 42,
        conditionCode: "workday",
        requestItemRef: null
      })
    ).toBe("条件 workday");
  });

  it("PREVIEW_INLINE → 新内联（请求条目位置，不合并同表达式）", () => {
    expect(
      conditionLabel({
        kind: "PREVIEW_INLINE",
        conditionId: null,
        conditionCode: null,
        requestItemRef: "creates[0]"
      })
    ).toBe("新内联条件（creates[0]）");
  });
});

describe("previewFactLabel / seedLabel", () => {
  it("事实标签 = TYPE/code · OP · 条件", () => {
    expect(
      previewFactLabel({
        fact: noneFact,
        seeds: [{ permissionId: 7, requestItemRef: null }]
      })
    ).toBe("REPORT/template · VIEW · 无条件");
  });

  it("种子引用区分现有授权与预览新建条目", () => {
    expect(seedLabel({ permissionId: 7, requestItemRef: null })).toBe(
      "显式授权 #7"
    );
    expect(
      seedLabel({ permissionId: null, requestItemRef: "creates[1]" })
    ).toBe("新建条目 creates[1]");
  });
});

describe("hasNoImpact（零影响判定不得被截断骗过）", () => {
  it("totalCount=0 且三组空 → 真零影响", () => {
    expect(hasNoImpact(respOf({}))).toBe(true);
  });

  it("截断时三组空也不能解释为无影响", () => {
    expect(hasNoImpact(respOf({ truncated: true, totalCount: 12 }))).toBe(
      false
    );
  });

  it("retained 非空（多来源保留）不是零影响", () => {
    expect(
      hasNoImpact(
        respOf({ retained: [{ fact: noneFact, seeds: [] }], totalCount: 1 })
      )
    ).toBe(false);
  });

  it("resp=null（未加载/失败）不算零影响", () => {
    expect(hasNoImpact(null)).toBe(false);
  });
});

describe("previewFailureText（失败显式标识，不降级为零影响）", () => {
  it("携带错误消息", () => {
    expect(previewFailureText({ message: "网络异常" })).toBe(
      "无法预览：网络异常"
    );
  });

  it("无消息时兜底文案", () => {
    expect(previewFailureText(null)).toBe("无法预览，请稍后重试");
  });
});

describe("PREVIEW_BOUNDARY_NOTES（M5 边界说明四要素）", () => {
  it("覆盖仅供参考/独立目标权限/类型级与父继承不触发/资源停用不暂停", () => {
    expect(PREVIEW_BOUNDARY_NOTES).toHaveLength(4);
    expect(PREVIEW_BOUNDARY_NOTES[0]).toContain("仅供参考");
    expect(PREVIEW_BOUNDARY_NOTES[1]).toContain("独立目标权限");
    expect(PREVIEW_BOUNDARY_NOTES[2]).toContain("不触发自动授权");
    expect(PREVIEW_BOUNDARY_NOTES[3]).toContain("停用不暂停");
  });
});
