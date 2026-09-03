/**
 * 位运算工具（BigInt 全链路，设计文档 permission-grant.md §3.5 P1-3）。
 *
 * binaryBit / inheritMask / grantedBits 均为 63 位位图：
 * - 契约线格式（T-PERM-028）：十进制字符串（如 "9223372036854775807"）；
 * - 共享 mock（mock/_shared/resource-fixtures.ts）同为十进制字符串（历史 number 形态已退役）。
 * 本模块统一宽容解析（string | number → BigInt），禁止 number 位运算
 * （JS 按位运算符强转 32 位，63 位 bigint 超 2^53 丢精度，事后转换无法恢复）。
 */

/** 位值输入（宽容线格式：十进制字符串 / number / 空） */
export type BitsInput = string | number | bigint | null | undefined;

/**
 * 宽容解析位值为 BigInt。
 * 无效输入（null/undefined/空串/非数字）归一化为 0n——位值为空等价于无任何位。
 */
export function toBigIntBits(value: BitsInput): bigint {
  if (value == null) return 0n;
  if (typeof value === "bigint") return value;
  if (typeof value === "number") {
    if (!Number.isFinite(value) || !Number.isInteger(value)) return 0n;
    return BigInt(value);
  }
  const trimmed = value.trim();
  if (trimmed === "" || !/^-?\d+$/.test(trimmed)) return 0n;
  return BigInt(trimmed);
}

/** 有效位 = binaryBit | inheritMask（对齐 OperationPermissionUtils.effectiveBits） */
export function effectiveBits(op: {
  binaryBit: bigint;
  inheritMask: bigint;
}): bigint {
  return op.binaryBit | op.inheritMask;
}

/**
 * 覆盖判定（对齐 OperationPermissionUtils.covers）：
 * granted 的有效位覆盖 targetBit ⟺ (effectiveBits(granted) & targetBit) != 0；
 * targetBit 为 0 时恒 false（与后端 `targetBit == 0L` 早退一致）。
 */
export function coversBit(
  granted: { binaryBit: bigint; inheritMask: bigint },
  targetBit: bigint
): boolean {
  if (targetBit === 0n) return false;
  return (effectiveBits(granted) & targetBit) !== 0n;
}

/**
 * 逐位拆解位图（返回所有置位，升序）。
 * 用于组合位记录（operationCode=null）按 grantedBits 与各操作列 binaryBit 逐位比对。
 */
export function decomposeBits(bits: bigint): bigint[] {
  const result: bigint[] = [];
  if (bits <= 0n) return result;
  let remaining = bits;
  let bit = 1n;
  while (remaining > 0n) {
    if ((remaining & bit) !== 0n) {
      result.push(bit);
      remaining &= ~bit;
    }
    bit <<= 1n;
  }
  return result;
}

/** BigInt → 十进制字符串（grantedBits 线格式回填） */
export function bitsToString(bits: bigint): string {
  return bits.toString(10);
}
