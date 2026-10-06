import cn.dev33.satoken.secure.BCrypt;
import java.io.Console;
import java.util.Arrays;

/** 离线恢复时生成与应用一致的 BCrypt 哈希；明文不进入命令参数或环境变量。 */
class PasswordHash {
    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("请在交互终端运行，禁止通过管道传入密码");
        }
        char[] password = console.readPassword("新密码（8-32 位）: ");
        char[] confirmation = console.readPassword("再次输入: ");
        try {
            if (password == null || password.length < 8 || password.length > 32 || !new String(password).matches("(?s).*[A-Za-z].*")
                || !new String(password).matches("(?s).*[0-9].*") || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("密码须为 8-32 位、含字母和数字且两次输入一致");
            }
            console.printf("%s%n", BCrypt.hashpw(new String(password), BCrypt.gensalt()));
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
