package cn.ac.fage.accessmesh.access.admin.dto.resp;

/**
 * 用户创建响应记录类
 * <p>
 * 创建用户成功后返回用户ID和系统生成的初始密码。
 * 初始密码仅在此响应中返回一次，前端应提示管理员保存。
 * </p>
 *
 * @param id               新用户ID
 * @param initialPassword  系统生成的随机初始密码（明文，仅本次返回）
 */
public record UserCreateResp(
    /**
     * 新用户ID
     */
    Long id,

    /**
     * 系统生成的随机初始密码（明文）
     * <p>
     * 仅在创建时返回一次，后续无法再获取明文密码。
     * </p>
     */
    String initialPassword
) {}
