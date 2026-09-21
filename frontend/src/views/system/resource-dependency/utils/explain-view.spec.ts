import { describe, expect, it } from "vitest";
import {
  explainConditionLabel,
  explainFactLabel,
  nodeStatus,
  edgesTouchedBy,
  declarationLabel
} from "./explain-view";
import type { ExplainNode } from "@/api/resource-dependency";

function nodeOf(
  nodeKey: string,
  factCode: string,
  explicitSeed: boolean,
  desired: boolean
): ExplainNode {
  return {
    nodeKey,
    fact: {
      resource: {
        resourceTypeCode: "REPORT",
        resourceCode: factCode,
        codeType: "default"
      },
      operationCode: "VIEW",
      conditionRef: {
        kind: "NONE",
        conditionId: null,
        conditionCode: null,
        requestItemRef: null
      }
    },
    explicitSeed,
    seedRefs: explicitSeed ? [{ permissionId: 7, requestItemRef: null }] : [],
    desired,
    actualPermissionIds: desired && !explicitSeed ? [99] : []
  };
}

describe("explain 事实标签", () => {
  it("条件身份 NONE/EXISTING 展示", () => {
    expect(
      explainConditionLabel({
        operationCode: "VIEW",
        resource: {
          resourceTypeCode: "R",
          resourceCode: "x",
          codeType: "default"
        },
        conditionRef: {
          kind: "NONE",
          conditionId: null,
          conditionCode: null,
          requestItemRef: null
        }
      })
    ).toBe("无条件");
    expect(
      explainConditionLabel({
        operationCode: "VIEW",
        resource: {
          resourceTypeCode: "R",
          resourceCode: "x",
          codeType: "default"
        },
        conditionRef: {
          kind: "EXISTING",
          conditionId: 5,
          conditionCode: "workday",
          requestItemRef: null
        }
      })
    ).toBe("条件 workday");
  });

  it("事实标签聚合类型/编码/操作/条件", () => {
    expect(explainFactLabel(nodeOf("n1", "template", false, true).fact)).toBe(
      "REPORT/template · VIEW · 无条件"
    );
  });
});

describe("nodeStatus（desired/actual 四象限，不把应生成当已生效）", () => {
  it("显式种子", () => {
    expect(nodeStatus(nodeOf("n1", "monthly", true, false))).toEqual({
      label: "显式种子",
      type: "info"
    });
  });

  it("已生效 = desired 且有实际行", () => {
    expect(nodeStatus(nodeOf("n2", "template", false, true)).label).toBe(
      "已生效"
    );
  });

  it("应有未落库 = desired 且无实际行（缺行漂移）", () => {
    const node = nodeOf("n2", "template", false, true);
    node.actualPermissionIds = [];
    expect(nodeStatus(node)).toEqual({ label: "应有未落库", type: "warning" });
  });

  it("无来源存量 = 非 desired 非 actual 支持（孤立行，不伪造来源）", () => {
    expect(nodeStatus(nodeOf("n3", "orphan", false, false))).toEqual({
      label: "无来源存量",
      type: "danger"
    });
  });
});

describe("edgesTouchedBy（选中节点沿直接边本地展开）", () => {
  const edges = [
    {
      fromNodeKey: "n1",
      toNodeKey: "n2",
      triggerOperationCode: "VIEW",
      declarationRefs: []
    },
    {
      fromNodeKey: "n2",
      toNodeKey: "n3",
      triggerOperationCode: "VIEW",
      declarationRefs: []
    },
    {
      fromNodeKey: "n1",
      toNodeKey: "n3",
      triggerOperationCode: "VIEW",
      declarationRefs: []
    }
  ];

  it("返回与节点直接相连的边", () => {
    expect(edgesTouchedBy(edges, "n2")).toHaveLength(2);
  });

  it("未命中返回空", () => {
    expect(edgesTouchedBy(edges, "n9")).toHaveLength(0);
  });
});

describe("declarationLabel（声明引用展示）", () => {
  it("空引用显示占位符", () => {
    expect(declarationLabel([])).toBe("—");
  });

  it("多声明并列展示（声明键+来源服务）", () => {
    expect(
      declarationLabel([
        { declarationId: 1, declarationKey: "ab", sourceService: "report-svc" },
        { declarationId: 2, declarationKey: "ab2", sourceService: "report-svc" }
      ])
    ).toBe("ab（report-svc）、ab2（report-svc）");
  });
});
