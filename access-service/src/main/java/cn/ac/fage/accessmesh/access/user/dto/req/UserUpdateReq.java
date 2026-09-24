package cn.ac.fage.accessmesh.access.user.dto.req;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 用户更新请求记录类
 * <p>
 * 用于更新用户信息的请求参数。
 * 所有字段均为可选，仅更新提供的字段。
 * </p>
 *
 * @param id     用户ID（必填，用于定位用户）
 * @param name   姓名（可选）
 * @param phone  手机号（可选；空白拒绝，清空须传 phoneClear——T-API-004）
 * @param email  邮箱（可选；空白拒绝，清空须传 emailClear——T-API-004）
 * @param status 状态（可选，0=停用，1=启用）
 * @param phoneClear 手机号显式清空标志（T-API-004，协议对齐 role/resource extraClear）：
 *                   true=清空 phone 为 NULL，与 phone 同传拒绝；false/缺省无清空作用
 * @param emailClear 邮箱显式清空标志（同上）
 */
public record UserUpdateReq(
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    Long id,

    /**
     * 姓名（显示名称）
     */
    String name,

    /**
     * 手机号（空白拒绝——清空唯一通道 phoneClear，杜绝空串入库与唯一索引空串撞车）
     */
    @Pattern(regexp = "(?s).*\\S.*", message = "phone 不能为空白；清空请传 phoneClear=true")
    String phone,

    /**
     * 邮箱（空白拒绝——清空唯一通道 emailClear）
     */
    @Pattern(regexp = "(?s).*\\S.*", message = "email 不能为空白；清空请传 emailClear=true")
    String email,

    /**
     * 状态（0=停用，1=启用，仅接纳 0/1；登录失败临时锁定不落库、不经本接口——T-ADMIN-022）
     */
    Integer status,

    /**
     * 手机号显式清空标志（T-API-004）
     */
    Boolean phoneClear,

    /**
     * 邮箱显式清空标志（T-API-004）
     */
    Boolean emailClear
) {
    /**
     * T-API-004（U006 拍板：全端点冲突拒绝）：新值与 Clear 同传拒绝——
     * 取代 role/resource 旧「Clear 优先、新值静默丢弃」口径，值静默丢失不可接受。
     */
    @AssertTrue(message = "phone 与 phoneClear 不能同时提供（清空请只传 phoneClear=true）")
    public boolean isPhoneConflictFree() {
        return phone == null || !Boolean.TRUE.equals(phoneClear);
    }

    @AssertTrue(message = "email 与 emailClear 不能同时提供（清空请只传 emailClear=true）")
    public boolean isEmailConflictFree() {
        return email == null || !Boolean.TRUE.equals(emailClear);
    }
}
