/**
 * BigInt 位运算工具（兼容 63 位 bigint 列）。
 *
 * JS `&`/`|` 强转 32 位有符号整数，>2^31 截断
 * （如 4294967296 | 0 === 0，2^32 截断为 0）。
 * schema 的 binaryBit / operationBits 为 BIGINT（63 位），
 * 故用 BigInt 做位运算。
 *
 * Number 精确整数上限 2^53，覆盖全部实际业务场景；
 * >2^53 的精度问题（API 仍以 number 接收 bigint）由 T-PERM-028 负责。
 */

/**
 * 判断 bits 是否包含 bit（BigInt 位与）。
 * @param bits 位掩码（number | string，string 兼容 bigint 序列化）
 * @param bit 单个位（2 的幂次）
 */
export function hasBit(bits: number | string, bit: number): boolean {
  if (!bit || bit <= 0) return false;
  return (BigInt(bits) & BigInt(bit)) === BigInt(bit);
}

/**
 * 判断 n 是否为 2 的幂次（BigInt 实现，兼容 63 位 bigint 列）。
 *
 * JS `&`/`|` 强转 32 位有符号整数，>2^31 会误判
 * （如 4294967297 = 2^32+1 截断为 1，`1 & 0 === 0` 被误判为幂次）。
 * 故用 BigInt 做位与。Number 精确整数上限 2^53，覆盖全部实际业务场景。
 */
export function isPowerOfTwo(n: number): boolean {
  if (!Number.isInteger(n) || n <= 0) return false;
  const bn = BigInt(n);
  return (bn & (bn - 1n)) === 0n;
}

/**
 * 位或（BigInt 实现，兼容 63 位）。
 *
 * JS `|` 强转 32 位，>2^31 截断（如 4294967296 | 0 === 0）。
 * 返回 Number（≤2^53 精确）。
 */
export function bitOr(a: number, b: number): number {
  return Number(BigInt(a) | BigInt(b));
}

/**
 * 多位合并（BigInt 位或，兼容 63 位）。
 * @param bits 位掩码数组
 * @returns 合并后的位掩码（Number，≤2^53 精确）
 */
export function bitsUnion(bits: Array<number | string>): number {
  let result = 0n;
  for (const b of bits) {
    result |= BigInt(b);
  }
  return Number(result);
}
