package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 抽象用户数据访问接口
 * <p>
 * 提供抽象用户表的基础CRUD操作和自定义查询方法。
 * 抽象用户包括内部用户和外部用户，是权限分配的主体。
 * 支持批量软删除操作。
 * </p>
 */
public interface AbstractUserMapper extends BaseMapper<AbstractUser> {

    /**
     * 批量软删除抽象用户
     * <p>
     * 将指定用户的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的抽象用户ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据ID和租户ID查询有效用户
     *
     * @param id       用户ID
     * @param tenantId 租户ID
     * @return 用户实体，不存在或已删除返回null
     */
    AbstractUser selectValidById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 根据用户类型、外部ID和租户ID查询有效用户（用于sync/create唯一性检查）
     *
     * @param tenantId  租户ID
     * @param userType  用户类型值
     * @param externalId 用户外部ID
     * @return 用户实体，不存在返回null
     */
    AbstractUser selectByTypeAndExternalId(@Param("tenantId") Long tenantId,
                                            @Param("userType") Integer userType,
                                            @Param("externalId") String externalId);

    /**
     * 根据ID集合批量查询有效用户
     *
     * @param tenantId 租户ID
     * @param ids      用户ID集合
     * @return 用户列表
     */
    List<AbstractUser> selectValidByIds(@Param("tenantId") Long tenantId, @Param("ids") Set<Long> ids);

    /**
     * 分页查询用户列表（带过滤条件）
     *
     * @param tenantId        租户ID
     * @param userType        用户类型值，可选
     * @param keyword         搜索关键字，可选（LIKE匹配name或external_id）
     * @param matchNone       是否匹配空结果（用于域过滤不匹配时）
     * @param offset          偏移量
     * @param limit           每页数量
     * @return 用户列表
     */
    List<AbstractUser> selectUserListPaged(@Param("tenantId") Long tenantId,
                                            @Param("userType") Integer userType,
                                            @Param("keyword") String keyword,
                                            @Param("matchNone") boolean matchNone,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /**
     * 统计用户数量（带过滤条件）
     *
     * @param tenantId        租户ID
     * @param userType        用户类型值，可选
     * @param keyword         搜索关键字，可选
     * @param matchNone       是否匹配空结果
     * @return 用户总数
     */
    long selectUserListCount(@Param("tenantId") Long tenantId,
                              @Param("userType") Integer userType,
                              @Param("keyword") String keyword,
                              @Param("matchNone") boolean matchNone);

    /**
     * 根据用户类型和外部ID批量查询有效用户
     *
     * @param tenantId    租户ID
     * @param userType    用户类型值
     * @param externalIds 外部ID集合
     * @return 用户列表
     */
    List<AbstractUser> selectByTypeAndExternalIds(@Param("tenantId") Long tenantId,
                                                   @Param("userType") Integer userType,
                                                   @Param("externalIds") Set<String> externalIds);
}