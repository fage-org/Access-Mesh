import { beforeEach, expect, it, vi } from "vitest";
import { usePermissionCondition } from "./hook";
import {
  createCondition,
  updateCondition,
  getConditionList
} from "@/api/permission-condition";
import { createEmptyConditionForm } from "./types";

vi.mock("@/utils/message", () => ({ message: vi.fn() }));
vi.mock("@/utils/auth", () => ({ hasPerms: () => true }));
vi.mock("@/api/permission-condition", () => ({
  getConditionList: vi.fn(),
  createCondition: vi.fn(),
  updateCondition: vi.fn(),
  removeConditions: vi.fn()
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getConditionList).mockResolvedValue({ items: [] });
});

it("编辑清空原有描述，空值与 Clear 不同时发送", async () => {
  const { submitCondition } = usePermissionCondition();
  const form = {
    ...createEmptyConditionForm(),
    code: "condition",
    name: "condition"
  };
  expect(await submitCondition(form, "edit", "condition", "before")).toBe(true);
  expect(updateCondition).toHaveBeenCalledWith(
    expect.objectContaining({
      code: "condition",
      description: null,
      descriptionClear: true
    })
  );
});

it("新建空描述发送 null 而非空串（拍板 A：create 面空白拒绝，后端 400 兜底）", async () => {
  const { submitCondition } = usePermissionCondition();
  const form = {
    ...createEmptyConditionForm(),
    code: "cond-new",
    name: "条件A"
  };
  expect(await submitCondition(form, "create")).toBe(true);
  expect(createCondition).toHaveBeenCalledWith(
    expect.objectContaining({
      code: "cond-new",
      description: null
    })
  );
});
