package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 系统用户数据访问接口
 * <p>
 * 提供用户表的基础CRUD操作和自定义查询方法。
 * 支持批量软删除、批量更新状态、批量更新密码等操作。
 * </p>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 分页查询用户列表（支持可选过滤条件）
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @param username 用户名（可选，模糊匹配）
     * @param name     姓名（可选，模糊匹配）
     * @param phone    手机号（可选，精确匹配）
     * @param email    邮箱（可选，精确匹配）
     * @param status   状态（可选）
     * @return 分页用户列表
     */
    Page<SysUser> paginateUsers(@Param("page") Page<SysUser> page,
                                @Param("tenantId") Long tenantId,
                                @Param("username") String username,
                                @Param("name") String name,
                                @Param("phone") String phone,
                                @Param("email") String email,
                                @Param("status") Integer status,
                                @Param("orgIds") Set<Long> orgIds);

    /**
     * 查询所有有效的租户ID（去重）
     *
     * @return 活跃租户ID集合
     */
    Set<Long> selectDistinctTenantIds();

    /**
     * 批量软删除用户
     *
     * @param tenantId  租户ID
     * @param ids       用户ID列表
     * @param deletedAt 删除时间
     * @return 影响行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 批量更新用户状态
     *
     * @param tenantId  租户ID
     * @param ids       用户ID列表
     * @param status    目标状态
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int batchUpdateStatus(@Param("tenantId") Long tenantId,
                          @Param("ids") List<Long> ids,
                          @Param("status") Integer status,
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 批量更新用户密码
     *
     * @param tenantId     租户ID
     * @param ids          用户ID列表
     * @param password     新密码（已加密）
     * @param updatedAt    更新时间
     * @return 影响行数
     */
    int batchUpdatePassword(@Param("tenantId") Long tenantId,
                            @Param("ids") List<Long> ids,
                            @Param("password") String password,
                            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 根据主键ID查询有效用户（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户实体，不存在则返回null
     */
    SysUser selectValidById(@Param("tenantId") Long tenantId,
                            @Param("userId") Long userId);

    /**
     * 根据用户ID集合批量查询有效用户（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID集合
     * @return 用户实体列表
     */
    List<SysUser> selectValidByIds(@Param("tenantId") Long tenantId,
                                   @Param("userIds") Set<Long> userIds);

    /**
     * 根据用户名查询有效用户（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return 用户实体，不存在则返回null
     */
    SysUser selectByUsername(@Param("tenantId") Long tenantId,
                             @Param("username") String username);

    /**
     * 根据手机号查询有效用户（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param phone    手机号
     * @return 用户实体，不存在则返回null
     */
    SysUser selectByPhone(@Param("tenantId") Long tenantId,
                          @Param("phone") String phone);

    /**
     * 根据用户名集合查询已存在的用户（租户隔离 + 未删除）
     *
     * @param tenantId  租户ID
     * @param usernames 用户名集合
     * @return 用户实体列表
     */
    List<SysUser> selectExistingByUsernames(@Param("tenantId") Long tenantId,
                                            @Param("usernames") Set<String> usernames);

    /**
     * 根据手机号集合查询已存在的用户（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param phones   手机号集合
     * @return 用户实体列表
     */
    List<SysUser> selectExistingByPhones(@Param("tenantId") Long tenantId,
                                         @Param("phones") Set<String> phones);
}
