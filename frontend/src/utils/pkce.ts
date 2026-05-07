/**
 * PKCE 工具模块
 * RFC 7636: Proof Key for Code Exchange by OAuth Public Clients
 */

/**
 * Base64 URL 编码(无填充)
 * 字符集: [A-Z] / [a-z] / [0-9] / "-" / "_"
 */
function base64UrlEncode(buffer: Uint8Array): string {
  const base64 = btoa(String.fromCharCode(...buffer));
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/**
 * 生成 code_verifier
 * 要求: 43-128 字符,使用 URL 安全字符集
 * 实现: 32 字节随机值 → Base64 URL 编码(43 字符)
 */
export function generateCodeVerifier(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

/**
 * 计算 code_challenge
 * 方法: code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))
 * 支持: S256 方法(推荐)
 */
export async function generateCodeChallenge(
  verifier: string,
  method: "S256" | "plain" = "S256"
): Promise<string> {
  if (method === "plain") {
    return verifier;
  }

  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return base64UrlEncode(new Uint8Array(digest));
}

/**
 * 验证 code_verifier 长度
 * 要求: 43 <= length <= 128
 */
export function validateVerifierLength(verifier: string): boolean {
  const len = verifier.length;
  return len >= 43 && len <= 128;
}
