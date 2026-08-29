/**
 * BigInt 位运算工具（兼容 63 位 bigint 列）。
 *
 * JS `&`/`|` 强转 32 位有符号整数，>2^31 截断
 * （如 4294967296 | 0 === 0，2^32 截断为 0）。
 * schema 的 binaryBit / operationBits 为 BIGINT（63 位），
 * 故用 BigInt 做位运算。
 *
 * Number 精确整数上限 2^53，覆盖全部实际业务场景。
 * T-PERM-028 起 operation-permission 位字段线格式为十进制字符串（63 位 bigint），
 * 本模块参数统一宽容 string | number。
 */

/**
 * 判断 bits 是否包含 bit（BigInt 位与）。
 * @param bits 位掩码（number | string，十进制字符串兼容 bigint 线格式）
 * @param bit 单个位（2 的幂次，number | string）
 */
export function hasBit(bits: number | string, bit: number | string): boolean {
  const bitValue = typeof bit === "number" ? bit : Number(bit);
  if (!bitValue || bitValue <= 0) return false;
  return (BigInt(bits) & BigInt(bit)) === BigInt(bit);
}

/**
 * 判断 n 是否为 2 的幂次（BigInt 实现，兼容 63 位 bigint 列）。
 *
 * JS `&`/`|` 强转 32 位有符号整数，>2^31 会误判
 * （如 4294967297 = 2^32+1 截断为 1，`1 & 0 === 0` 被误判为幂次）。
 * 故用 BigInt 做位与。Number 精确整数上限 2^53，覆盖全部实际业务场景。
 */
export function isPowerOfTwo(n: number | string): boolean {
  try {
    const bn = BigInt(n);
    return bn > 0n && (bn & (bn - 1n)) === 0n;
  } catch {
    return false;
  }
}

/**
 * 位或（BigInt 实现，兼容 63 位）。
 *
 * JS `|` 强转 32 位，>2^31 截断（如 4294967296 | 0 === 0）。
 * 返回 Number（≤2^53 精确）。
 */
export function bitOr(a: number | string, b: number | string): string {
  return (BigInt(a) | BigInt(b)).toString();
}

/**
 * 多位合并（BigInt 位或，兼容 63 位）。
 * @param bits 位掩码数组
 * @returns 合并后的位掩码（Number，≤2^53 精确）
 */
export function bitsUnion(bits: Array<number | string>): string {
  let result = 0n;
  for (const b of bits) {
    result |= BigInt(b);
  }
  return result.toString();
}
