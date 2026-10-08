package cn.ac.fage.accessmesh.access.infrastructure.util;

import java.security.SecureRandom;

/** 复用现有用户改密的强度与随机密码规则，不记录或缓存明文。 */
public final class CredentialPasswords {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;
    private CredentialPasswords() {}

    public static boolean isValid(String password) {
        return password != null && password.length() >= 8 && password.length() <= 32
            && password.matches("(?s).*[A-Za-z].*") && password.matches("(?s).*[0-9].*");
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
