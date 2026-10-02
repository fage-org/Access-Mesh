import { describe, expect, it } from "vitest";
import { nextAvailableOperationBit, operationBitError } from "./types";

describe("操作独占位", () => {
  it("为预置 CRUD 生成下一可用位，并复用空闲位", () => {
    expect(nextAvailableOperationBit([])).toBe("1");
    expect(nextAvailableOperationBit(["1", "2", "4", "8"])).toBe("16");
    expect(nextAvailableOperationBit(["1", "4", "8"])).toBe("2");
  });

  it("精确生成高位，63 个位置用尽后无可用值", () => {
    const bits = Array.from({ length: 63 }, (_, i) =>
      (1n << BigInt(i)).toString()
    );
    expect(nextAvailableOperationBit(bits.slice(0, 62))).toBe(
      "4611686018427387904"
    );
    expect(nextAvailableOperationBit(bits)).toBeNull();
  });

  it.each([
    0,
    -1,
    3,
    1.5,
    "3",
    "0",
    "-9223372036854775808",
    "9223372036854775808",
    "",
    "bad",
    2 ** 53
  ])("拒绝非法或不安全数值 %s", value =>
    expect(operationBitError(value, [])).not.toBeNull()
  );

  it.each([1, 2 ** 32, "9007199254740992", "4611686018427387904"])(
    "接受精确单比特 %s",
    value => expect(operationBitError(value, [])).toBeNull()
  );

  it("拒绝当前类型已占用的位，编辑保留自身值时不算重复", () => {
    expect(operationBitError("4", ["1", "2", "4"])).not.toBeNull();
    expect(operationBitError("4", ["1", "2"])).toBeNull();
  });
});
