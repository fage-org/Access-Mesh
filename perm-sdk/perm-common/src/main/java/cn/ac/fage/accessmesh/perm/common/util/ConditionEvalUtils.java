package cn.ac.fage.accessmesh.perm.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * 权限条件评估工具类（T-PERM-017 搬入 perm-common，供 Gateway 与 access-service 共享）
 * <p>
 * 提供权限条件项评估的静态工具方法。纯静态、无 DB / Spring 依赖，可在任意 JVM 进程内复用。
 * 支持日期范围、时间范围、IP白名单/黑名单等条件类型的评估。
 * </p>
 * <p>
 * 时钟语义说明（T-PERM-017；2026-09-09 T-PERM-057 修订）：
 * <ul>
 *   <li>{@link #evalDateRange} / {@link #evalTimeRange} 优先消费评估上下文的服务器环境键
 *       {@link #CONTEXT_KEY_EVALUATED_AT}（ISO-8601 LocalDateTime 字符串，access-service
 *       统一引擎的 {@code PermEvalContext} 展平注入）；键缺省或不可解析回退调用方进程
 *       系统时钟 {@code LocalDate.now()} / {@code LocalTime.now()}（Gateway 快照重评等
 *       无服务器环境上下文的调用方维持既有行为）。</li>
 *   <li>Gateway 与 access-service 可能运行在不同进程。本项目面向中小型企业部署，
 *       Gateway 与 access-service 通常同机房 / 同云区域，跨进程时钟一致性由 NTP
 *       同步保证（亚秒级）。条件规则的业务粒度（DATE_RANGE 按天，TIME_RANGE 通常按
 *       小时级如 09:00-18:00）远大于 NTP 漂移，因此 4 类条件均可下发 Gateway。</li>
 *   <li>评估上下文 {@link Map} 承载 {@code clientIp}（用户环境）与
 *       {@code evaluatedAt}（服务器环境，可选）。</li>
 * </ul>
 * </p>
 */
public final class ConditionEvalUtils {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvalUtils.class);

    /**
     * 评估上下文键：服务器环境评估时间（ISO-8601 LocalDateTime 字符串；缺省回退本机时钟）
     */
    public static final String CONTEXT_KEY_EVALUATED_AT = "evaluatedAt";

    /**
     * Gateway 可下发评估的条件类型白名单（T-PERM-017）
     * <p>
     * 当 {@code permission_condition.gateway_evaluable=true} 时，{@code conditionRules.items[].type}
     * 必须全部在本集合内才允许标记；含集合外类型（含未来未知类型）→ 拒绝下发，走 fallback 实时鉴权。
     * </p>
     * <p>
     * 集合范围由 T-PERM-017 决策确定：4 个已知类型全部在内（IP_WHITELIST / IP_BLACKLIST /
     * DATE_RANGE / TIME_RANGE）。跨进程时钟一致性由 NTP 同步保证，业务粒度（小时级）
     * 容忍亚秒漂移，故 TIME_RANGE 也可下发。未知类型默认 fail-close，便于后续新增类型
     * 时强制走显式审批流程。
     * </p>
     */
    public static final Set<String> GATEWAY_PUSHABLE_TYPES = Set.of(
        "IP_WHITELIST",
        "IP_BLACKLIST",
        "DATE_RANGE",
        "TIME_RANGE"
    );

    /**
     * 合法的条件规则顶层 {@code logic} 取值集合（T-PERM-017 评审反馈 P2-B）。
     * <p>
     * 历史行为：{@code logic.equals("AND")} 走 AND，**任何其他值（含拼写错 "ANDD" / 大小写 "and" /
     * 未来扩展未知值）一律按 OR 处理**，可能放宽权限。
     * </p>
     * <p>
     * 收口策略（fail-close）：
     * <ul>
     *   <li>{@code logic} 缺省 → 默认 AND（与历史语义一致，向后兼容）</li>
     *   <li>{@code logic} 显式声明且非 {@code AND} / {@code OR}（含 null / 空串） → 写入门禁拒绝；
     *       两端 evaluate 函数防御性 fail-close 返回 false</li>
     * </ul>
     * </p>
     */
    public static final Set<String> VALID_LOGIC = Set.of("AND", "OR");

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private ConditionEvalUtils() {}

    /**
     * CIDR 参数合法性校验（T-PERM-109 共享单源：判定器、条件写入口与前端基础校验同语义）。
     * <p>
     * 合法形态：IPv4/IPv6 数值字面量（无 {@code /}），或 {@code 字面量/前缀}——前缀为
     * 1-3 位 ASCII 纯数字（拒绝负数/带符号/夹带任意串/全角数字）且值不超过地址族位数
     * （IPv4=32、IPv6=128）。字面量经 {@link #literalAddressBits} <b>结构级</b>闸门
     * （外评 P2-2 处置：IPv4 严格点分四段，IPv6 校验分组/压缩/zone/IPv4 映射尾——
     * 仅字符集校验会放行 {@code :::} 等结构非法形态，进而在匹配期经 DNS 解析失败
     * 落空、黑名单反转为放行）；闸门与匹配共用同一套纯字节解析器（codex sol r2 P2-2 数值化）——无 InetAddress、
     * 无「校验放行而解析拒绝」落差面、无 zone/网卡名平台相关副作用。判定器消费：非法
     * CIDR 不匹配任何地址（fail-closed）。
     * </p>
     */
    public static boolean isValidCidr(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return false;
        }
        int slash = cidr.indexOf('/');
        String literal = slash < 0 ? cidr : cidr.substring(0, slash);
        byte[] addressBytes = parseNumericAddress(literal);
        int familyBits = addressBytes == null ? -1 : addressBytes.length * 8;
        if (familyBits < 0) {
            return false;
        }
        if (slash < 0) {
            return true;
        }
        String prefix = cidr.substring(slash + 1);
        if (!isAsciiDigits(prefix) || prefix.length() > 3) {
            return false;
        }
        return Integer.parseInt(prefix) <= familyBits;
    }

    /** ASCII 纯数字判定（Character.isDigit 接受全角数字，解析链路仅认 ASCII——外评 P2-2）。 */
    private static boolean isAsciiDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * 数值地址字面量解析（T-PERM-109 立项；codex sol r2 P2-2 数值化收口）：纯结构、
     * 无 {@code InetAddress}/DNS 副作用——IPv4 = 严格点分四段（每段 1-3 位 ASCII 数字
     * 且 0-255）→ byte[4]；IPv6 = 结构合法字面量（{@link #parseIpv6}）→ byte[16]；
     * zone（%scope）一律拒绝——CIDR 条目无 zone 匹配语义（客户端地址不携带 zone），
     * zone 解析平台相关（%eth0 在无该网卡的宿主 UnknownHost）、JDK 对 v4-mapped+zone
     * 明确拒绝。非法返回 null。校验（{@code isValidCidr}）与匹配（{@code ipMatchesCidr}）
     * 共用本解析器，不存在「校验放行而解析拒绝」的落差面。
     */
    static byte[] parseNumericAddress(String literal) {
        if (literal == null || literal.isEmpty() || literal.indexOf('%') >= 0) {
            return null;
        }
        if (literal.indexOf(':') >= 0) {
            return parseIpv6(literal);
        }
        return parseIpv4(literal);
    }

    private static byte[] parseIpv4(String literal) {
        String[] octets = literal.split("\\.", -1);
        if (octets.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            String octet = octets[i];
            if (octet.isEmpty() || octet.length() > 3 || !isAsciiDigits(octet)
                || Integer.parseInt(octet) > 255) {
                return null;
            }
            bytes[i] = (byte) Integer.parseInt(octet);
        }
        return bytes;
    }

    /**
     * IPv6 数值解析（纯结构）：每组 1-4 位 ASCII 十六进制；IPv4 点分尾仅允许作为
     * <b>整地址末组</b>（计 2 组——无压缩时为单侧末组，有压缩时为右侧末组；左侧
     * 出现 v4 尾即 {@code 1.2.3.4::} 形态拒绝，codex sol r2 P2-2）；恰含一个
     * {@code ::} 压缩且两侧组数（v4 尾计 2）之和 ≤ 7，无压缩则恰 8 组（允许
     * 「6 组 + v4 尾」形态）；空组（多余冒号/首尾孤立冒号）、重复压缩、超界组数
     * 均拒绝。返回 16 字节网络序，非法 null。
     */
    private static byte[] parseIpv6(String literal) {
        int compressIndex = literal.indexOf("::");
        if (compressIndex >= 0 && literal.indexOf("::", compressIndex + 1) >= 0) {
            return null;
        }
        // 无压缩：单侧即整地址，末组 v4 尾合法；有压缩：左侧任何 v4 尾都不在地址末位，拒绝
        int[] leftGroups = parseIpv6Side(
            compressIndex >= 0 ? literal.substring(0, compressIndex) : literal, compressIndex < 0);
        if (leftGroups == null) {
            return null;
        }
        int[] rightGroups = compressIndex >= 0
            ? parseIpv6Side(literal.substring(compressIndex + 2), true)
            : null;
        if (compressIndex >= 0 && rightGroups == null) {
            // 压缩右侧解析失败（超界组/非法 v4 尾）立即拒绝（codex sol r3 P2-2）：
            // parseIpv6Side 仅对非法输入返回 null（空串返回 0 组），null 按 0 组放行
            // 会在游标计算处解引用抛 NPE（写入口 500、判定面外层兜底误拒）
            return null;
        }
        int groupCount = leftGroups.length + (rightGroups == null ? 0 : rightGroups.length);
        if (compressIndex < 0) {
            if (groupCount != 8) {
                return null;
            }
        } else if (groupCount > 7) {
            return null;
        }
        byte[] bytes = new byte[16];
        int cursor = 0;
        for (int group : leftGroups) {
            bytes[cursor++] = (byte) (group >> 8);
            bytes[cursor++] = (byte) group;
        }
        if (compressIndex >= 0) {
            cursor = 16 - (rightGroups.length * 2);
        }
        for (int group : rightGroups == null ? new int[0] : rightGroups) {
            bytes[cursor++] = (byte) (group >> 8);
            bytes[cursor++] = (byte) group;
        }
        return bytes;
    }

    /**
     * 冒号侧组解析：空串=0 组；每组 1-4 位十六进制；v4 点分尾（计 2 组）仅在
     * {@code allowV4Tail}（=该侧为整地址末侧）且为本侧末组时接受；空组/违规组返回 null。
     */
    private static int[] parseIpv6Side(String side, boolean allowV4Tail) {
        if (side.isEmpty()) {
            return new int[0];
        }
        String[] parts = side.split(":", -1);
        int[] groups = new int[parts.length + 1];
        int count = 0;
        for (int i = 0; i < parts.length; i++) {
            String group = parts[i];
            if (group.isEmpty()) {
                return null;
            }
            if (group.indexOf('.') >= 0) {
                if (!allowV4Tail || i != parts.length - 1) {
                    return null;
                }
                byte[] v4 = parseIpv4(group);
                if (v4 == null) {
                    return null;
                }
                groups[count++] = ((v4[0] & 0xFF) << 8) | (v4[1] & 0xFF);
                groups[count++] = ((v4[2] & 0xFF) << 8) | (v4[3] & 0xFF);
            } else {
                if (group.length() > 4) {
                    return null;
                }
                int value = 0;
                for (int j = 0; j < group.length(); j++) {
                    char c = group.charAt(j);
                    int digit = Character.digit(c, 16);
                    if (digit < 0) {
                        return null;
                    }
                    value = (value << 4) | digit;
                }
                groups[count++] = value;
            }
        }
        return java.util.Arrays.copyOf(groups, count);
    }

    // ===== 日期 / 时间范围评估 =====

    /**
     * 评估日期范围条件
     * <p>
     * 判断当前日期是否在指定的日期范围内（包含边界）。
     * 日期格式为 ISO 8601 格式（yyyy-MM-dd）。使用调用方进程的系统时钟；
     * 带 context 的重载优先消费服务器环境键 {@link #CONTEXT_KEY_EVALUATED_AT}。
     * </p>
     *
     * @param startDate 开始日期字符串
     * @param endDate   结束日期字符串
     * @return 当前日期在范围内返回true，否则返回false
     */
    public static boolean evalDateRange(String startDate, String endDate) {
        return evalDateRange(startDate, endDate, null);
    }

    /**
     * 评估日期范围条件（服务器环境时钟：context 的 evaluatedAt 优先，缺省本机时钟）
     */
    public static boolean evalDateRange(String startDate, String endDate, Map<String, Object> context) {
        if (startDate == null || endDate == null) return false;
        try {
            LocalDate now = resolveEvalDate(context);
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (DateTimeParseException e) {
            log.warn("无效的日期范围: [{}, {}] — {}", startDate, endDate, e.getMessage());
            return false;
        }
    }

    /**
     * 评估时间范围条件
     * <p>
     * 判断当前时间是否在指定的时间范围内（包含边界）。
     * 时间格式为 ISO 8601 格式（HH:mm:ss）。使用调用方进程的系统时钟；
     * 带 context 的重载优先消费服务器环境键 {@link #CONTEXT_KEY_EVALUATED_AT}。
     * </p>
     *
     * @param startTime 开始时间字符串
     * @param endTime   结束时间字符串
     * @return 当前时间在范围内返回true，否则返回false
     */
    public static boolean evalTimeRange(String startTime, String endTime) {
        return evalTimeRange(startTime, endTime, null);
    }

    /**
     * 评估时间范围条件（服务器环境时钟：context 的 evaluatedAt 优先，缺省本机时钟）
     */
    public static boolean evalTimeRange(String startTime, String endTime, Map<String, Object> context) {
        if (startTime == null || endTime == null) return false;
        try {
            LocalTime now = resolveEvalTime(context);
            LocalTime start = LocalTime.parse(startTime);
            LocalTime end = LocalTime.parse(endTime);
            if (end.isBefore(start)) {
                // 跨午夜时间范围（如 22:00-06:00）：当前时间 >= 开始时间 或 <= 结束时间 时在范围内
                return !now.isBefore(start) || !now.isAfter(end);
            }
            return !now.isBefore(start) && !now.isAfter(end);
        } catch (DateTimeParseException e) {
            log.warn("无效的时间范围: [{}, {}] — {}", startTime, endTime, e.getMessage());
            return false;
        }
    }

    /**
     * 解析评估日期：context 服务器环境键 evaluatedAt（ISO-8601 LocalDateTime 字符串）优先，
     * 缺省/不可解析回退本机时钟（2026-09-09 定案：条件上下文含服务器环境维度）。
     */
    private static LocalDate resolveEvalDate(Map<String, Object> context) {
        LocalDateTime at = resolveEvalAt(context);
        return at != null ? at.toLocalDate() : LocalDate.now();
    }

    /**
     * 解析评估时间：context 服务器环境键 evaluatedAt 优先，缺省/不可解析回退本机时钟。
     */
    private static LocalTime resolveEvalTime(Map<String, Object> context) {
        LocalDateTime at = resolveEvalAt(context);
        return at != null ? at.toLocalTime() : LocalTime.now();
    }

    private static LocalDateTime resolveEvalAt(Map<String, Object> context) {
        Object value = context == null ? null : context.get(CONTEXT_KEY_EVALUATED_AT);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (DateTimeParseException e) {
            log.warn("无效的评估时间上下文: {} — 回退本机时钟", value);
            return null;
        }
    }

    // ===== IP / CIDR 评估 =====

    /**
     * 评估IP列表条件
     * <p>
     * 判断客户端IP是否匹配CIDR列表中的任一地址。
     * 支持IPv4和IPv6地址格式。
     * T-PERM-109：任一 CIDR 参数非法（负前缀/超地址族位数/主机名样串）→ 整个 IP 条件
     * 不满足（fail-closed）——黑名单不因「无匹配→反转」放行非法配置。
     * </p>
     *
     * @param cidrs     CIDR地址列表（JSON数组）
     * @param clientIp  客户端IP地址
     * @param whitelist 是否为白名单模式（true：白名单匹配返回true，false：黑名单匹配返回false）
     * @return 评估结果
     */
    public static boolean evalIpList(JsonNode cidrs, String clientIp, boolean whitelist) {
        if (clientIp == null) return false;
        if (cidrs == null || !cidrs.isArray()) return false;
        // T-PERM-109：前置全量校验——任一非法项令整个 IP 条件不满足（先校验后匹配，
        // 避免合法项先命中短路遮蔽后面的非法项；黑名单不因「无匹配→反转」放行非法配置）
        for (JsonNode cidr : cidrs) {
            if (!isValidCidr(cidr == null ? null : cidr.asText())) {
                return false;
            }
        }
        for (JsonNode cidr : cidrs) {
            if (ipMatchesCidr(clientIp, cidr.asText())) {
                return whitelist;
            }
        }
        return !whitelist;
    }

    /**
     * 判断IP是否在CIDR范围内
     * <p>
     * 支持IPv4和IPv6地址的CIDR匹配。
     * CIDR格式为 network/prefixLength，如 192.168.1.0/24。
     * T-PERM-109：参数与客户端值先经 {@link #isValidCidr} 形态闸门——非法 CIDR 不匹配
     * 任何地址（负前缀不再「匹配一切」）。codex sol r2 P2-2：地址解析数值化——校验与
     * 匹配共用同一套纯字节解析器（{@link #parseNumericAddress}），全程无
     * {@code InetAddress.getByName}：不再存在「校验放行而 JDK 解析拒绝」的落差面
     * （旧差形态如 {@code 1.2.3.4::} 在匹配期降级为未命中、黑名单反转为放行），
     * 亦无 zone/网卡名等平台相关解析副作用。
     * codex sol r3 P2-1：跨族仅 IPv4-mapped（{@code ::ffff:a.b.c.d}）↔IPv4 归一后可比
     * （恢复 InetAddress 将 mapped 解析为 Inet4Address 的归一语义——mapped 网段前缀
     * 换算 -96 后与内嵌 v4 比较；纯 v6 × v4 不匹配）；无前缀单地址按归一后字节等值
     * （压缩/全写同址等价、mapped↔v4 等价，取代旧字符串等值）。
     * </p>
     *
     * @param ip   IP地址
     * @param cidr CIDR范围
     * @return IP在范围内返回true，否则返回false
     */
    public static boolean ipMatchesCidr(String ip, String cidr) {
        if (!isValidCidr(ip) || !isValidCidr(cidr)) {
            return false;
        }
        if (!cidr.contains("/")) {
            // 无前缀单地址：字节等值——压缩/全写同址等价，mapped↔v4 归一（codex sol r3
            // P2-1：旧字符串等值不识别等价写法，存量观察随 mapped 归一同层收敛）
            byte[] clientBytes = parseNumericAddress(ip);
            byte[] networkBytes = parseNumericAddress(cidr);
            return clientBytes != null && networkBytes != null
                && bytesEqualNormalized(clientBytes, networkBytes);
        }
        int slash = cidr.indexOf('/');
        int prefixLength = Integer.parseInt(cidr.substring(slash + 1));

        byte[] clientBytes = parseNumericAddress(ip);
        byte[] networkBytes = parseNumericAddress(cidr.substring(0, slash));
        if (clientBytes == null || networkBytes == null) {
            return false;
        }
        if (clientBytes.length != networkBytes.length) {
            // 跨族仅 IPv4-mapped↔IPv4 归一后可比（codex sol r3 P2-1）：数值化前
            // InetAddress 将 ::ffff:a.b.c.d 解析为 Inet4Address，mapped 客户端可命中
            // 普通 v4 网段——纯字节长度比较丢失该归一曾使黑名单漏拦经反转放行；
            // 纯 v6 × v4 仍不匹配（::/0 非 mapped 网段不含 v4 客户端）
            byte[] clientV4 = embeddedIpv4OrSelf(clientBytes);
            byte[] networkV4 = embeddedIpv4OrSelf(networkBytes);
            if (clientV4 == null || networkV4 == null) {
                return false;
            }
            if (networkBytes.length == 16) {
                prefixLength = Math.max(0, prefixLength - 96);
            }
            clientBytes = clientV4;
            networkBytes = networkV4;
        }

        for (int i = 0; i < prefixLength; i++) {
            int byteIndex = i / 8;
            int bitIndex = 7 - (i % 8);
            if (((clientBytes[byteIndex] >> bitIndex) & 1) != ((networkBytes[byteIndex] >> bitIndex) & 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * IPv4-mapped IPv6（前 80 位 0 + ffff 段）取内嵌 IPv4 字节；已是 IPv4 原样返回；
     * 其余 IPv6 返回 null（codex sol r3 P2-1：恢复 InetAddress 将 mapped 解析为
     * Inet4Address 的归一语义，匹配层专用——校验/解析层保持 16 字节原形）。
     */
    private static byte[] embeddedIpv4OrSelf(byte[] addressBytes) {
        if (addressBytes.length == 4) {
            return addressBytes;
        }
        for (int i = 0; i < 10; i++) {
            if (addressBytes[i] != 0) {
                return null;
            }
        }
        if (addressBytes[10] != (byte) 0xFF || addressBytes[11] != (byte) 0xFF) {
            return null;
        }
        return Arrays.copyOfRange(addressBytes, 12, 16);
    }

    /** 归一后字节等值：同长直接比较；mapped 16B 与 v4 4B 经内嵌归一后比较（codex sol r3 P2-1）。 */
    private static boolean bytesEqualNormalized(byte[] left, byte[] right) {
        if (left.length == right.length) {
            return Arrays.equals(left, right);
        }
        byte[] leftV4 = embeddedIpv4OrSelf(left);
        byte[] rightV4 = embeddedIpv4OrSelf(right);
        return leftV4 != null && rightV4 != null && Arrays.equals(leftV4, rightV4);
    }

    // ===== 条件项评估 =====

    /**
     * 判定一组条件规则是否可下发 Gateway 评估（T-PERM-017）
     * <p>
     * 用于 {@code ConditionAppService} 写入校验 + {@code InterfaceAdmissionSnapshotAssembler} 内联前防御过滤。
     * 两处共用同一份判定，避免管理面接受却下发面拒绝的不一致。
     * </p>
     * <p>
     * 判定规则（fail-close）：
     * <ul>
     *   <li>{@code conditionRules} 为 null / 非对象 → false</li>
     *   <li>{@code logic} 字段存在但不在 {@link #VALID_LOGIC}（AND/OR） → false（T-PERM-017 P2-B 收口）</li>
     *   <li>{@code items} 缺失 / 非数组 / 为空 → false（空规则视为无意义，不允许下发）</li>
     *   <li>任一 item 缺 {@code type} 字段或 type 不在 {@link #GATEWAY_PUSHABLE_TYPES} → false</li>
     *   <li>全部 item 的 type 都在白名单 → true</li>
     * </ul>
     * </p>
     *
     * @param conditionRules 条件规则根 JsonNode（含 logic + items）
     * @return true 表示可下发 Gateway，false 表示必须走 fallback 实时鉴权
     */
    public static boolean isGatewayPushable(JsonNode conditionRules) {
        if (conditionRules == null || !conditionRules.isObject()) {
            return false;
        }
        // T-PERM-017 P2-B/P3：logic 显式声明时必须在 VALID_LOGIC；缺省放行（兼容默认 AND 语义），null/空串拒绝。
        JsonNode logicNode = conditionRules.get("logic");
        if (logicNode != null && !VALID_LOGIC.contains(logicNode.asText())) {
            return false;
        }
        JsonNode items = conditionRules.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return false;
        }
        for (JsonNode item : items) {
            if (item == null || !item.has("type")) {
                return false;
            }
            String type = item.get("type").asText();
            if (!GATEWAY_PUSHABLE_TYPES.contains(type)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 评估单个条件项
     * <p>
     * 根据条件类型评估单个条件项JSON节点。
     * 支持的类型：DATE_RANGE、TIME_RANGE、IP_WHITELIST、IP_BLACKLIST。
     * </p>
     *
     * @param item             条件项JSON节点
     * @param context          评估上下文（包含clientIp等信息）
     * @param dateRangeType    日期范围类型标识
     * @param timeRangeType    时间范围类型标识
     * @param ipWhitelistType  IP白名单类型标识
     * @param ipBlacklistType  IP黑名单类型标识
     * @return 条件满足返回true，否则返回false
     */
    public static boolean evalItem(JsonNode item, Map<String, Object> context,
                                   String dateRangeType, String timeRangeType,
                                   String ipWhitelistType, String ipBlacklistType) {
        if (item == null) return false;
        String type = item.has("type") ? item.get("type").asText() : "";
        JsonNode params = item.get("params");
        if (params == null) return false;

        if (dateRangeType.equals(type)) {
            return evalDateRange(
                params.has("start") ? params.get("start").asText() : null,
                params.has("end") ? params.get("end").asText() : null,
                context);
        }
        if (timeRangeType.equals(type)) {
            return evalTimeRange(
                params.has("start") ? params.get("start").asText() : null,
                params.has("end") ? params.get("end").asText() : null,
                context);
        }
        if (ipWhitelistType.equals(type)) {
            String clientIp = (String) context.get("clientIp");
            return evalIpList(params.get("cidrs"), clientIp, true);
        }
        if (ipBlacklistType.equals(type)) {
            String clientIp = (String) context.get("clientIp");
            return evalIpList(params.get("cidrs"), clientIp, false);
        }
        return false;
    }
}
