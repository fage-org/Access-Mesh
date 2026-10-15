package cn.ac.fage.accessmesh.access.infrastructure.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import cn.dev33.satoken.secure.BCrypt;

/** 复用现有用户改密的强度与随机密码规则，不记录或缓存明文。 */
public final class CredentialPasswords {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    /**
     * BCrypt 参与哈希的输入上限（字节）：超出部分被静默截断、尾部差异失效。
     * 字符数规则（8-32）允许的全角密码可达 96+ 字节，故写入路径须先过本上限。
     * 登录验证不回溯：历史超限密码两侧同样截断比对，仍可登录。
     */
    public static final int BCRYPT_MAX_INPUT_BYTES = 72;

    /**
     * 等成本 dummy 哈希（strength 10，与登录验密同档）：账号不存在/无哈希分支
     * 对它做一次真实比对后仍拒绝，消除「未知账号 ~0.5ms / 错误密码 ~60-80ms」
     * 的时间侧信道。明文为无意义占位串，不对应任何账号。
     */
    public static final String DUMMY_HASH = "$2a$10$4z6UqAC5nfaCGk7DBj6ele7vzWkBRTwbwe9w8q5FuXSTkhO75gW4a";

    private CredentialPasswords() {}

    public static boolean isValid(String password) {
        return password != null && password.length() >= 8 && password.length() <= 32
            && password.matches("(?s).*[A-Za-z].*") && password.matches("(?s).*[0-9].*");
    }

    /** 写入口先用：UTF-8 字节数超 BCrypt 上限的密码拒绝入哈希（先长度后哈希）。 */
    public static boolean exceedsBcryptByteLimit(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_INPUT_BYTES;
    }

    /** 账号缺失分支的等成本比对：结果恒弃用，仅为燃烧与真实验密相同的 BCrypt 工作量。 */
    public static void dummyCheck(String password) {
        BCrypt.checkpw(password, DUMMY_HASH);
    }

    public static String generate() {
        StringBuilder password = new StringBuilder(12);
        password.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        password.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        password.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        password.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));
        for (int i = 4; i < 12; i++) password.append(ALL.charAt(RANDOM.nextInt(ALL.length())));
        return password.toString();
    }
}
