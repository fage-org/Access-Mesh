import { beforeEach, expect, it, vi } from "vitest";
import { usePermissionCondition } from "./hook";
import { updateCondition, getConditionList } from "@/api/permission-condition";
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
