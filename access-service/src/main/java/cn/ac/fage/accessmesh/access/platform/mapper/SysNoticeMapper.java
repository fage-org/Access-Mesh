package cn.ac.fage.accessmesh.access.platform.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.platform.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统通知公告数据访问接口
 * <p>
 * 提供通知公告表的基础CRUD操作和自定义查询方法。
 * 支持批量软删除操作。
 * </p>
 */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 根据ID查询未删除的通知（租户隔离）
     *
     * @param tenantId 租户ID
     * @param id       通知ID
     * @return 通知实体，不存在返回null
     */
    SysNotice selectByIdSafe(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 批量查询未删除的通知（租户隔离）
     *
     * @param tenantId 租户ID
     * @param ids      通知ID列表
     * @return 通知实体列表
     */
    List<SysNotice> selectByIdsSafe(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);

    /**
     * 分页查询未删除的通知（租户隔离），按创建时间倒序
     * <p>
     * XML 分页统一 offset/limit + count 双查询（仓库既定模式，见 SysUserMapper；
     * MyBatis-Flex 的 Page 参数在 XML 映射下不生效——selectOne 多行异常，T-ADMIN-026 收口）
     * </p>
     *
     * @param tenantId 租户ID
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 通知列表（当前页）
     */
    List<SysNotice> selectByTenantPaged(@Param("tenantId") Long tenantId,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    /**
     * 统计租户下未删除的通知数（用于分页计算）
     */
    long countByTenant(@Param("tenantId") Long tenantId);

    /**
     * 查询对指定用户可见的已发布公告列表（租户隔离+受众过滤），按创建时间倒序
     * <p>
     * 可见口径（T-ADMIN-029）：status=1 且（target_type='ALL' 或
     * target_type='USER' 且 target_ids JSONB 数组包含该用户 ID）。
     * target_ids 为 NULL/空数组的 USER 行不匹配任何人（fail-closed）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   接收用户ID
     * @return 通知实体列表
     */
    List<SysNotice> selectVisibleByUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /**
     * 校验指定用户对某公告的可见性（已发布+受众含该用户；租户隔离）
     *
     * @param tenantId 租户ID
     * @param id       通知ID
     * @param userId   接收用户ID
     * @return 可见返回 1，不可见返回 0
     */
    long countVisibleById(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("userId") Long userId);

    /**
     * 批量软删除通知公告
     * <p>
     * 将指定通知的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID，用于数据隔离
     * @param ids       待删除的通知ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 发布状态原子转换（草稿 0/已撤回 2 → 已发布 1；T-ADMIN-029）
     * <p>
     * 条件 UPDATE 下沉状态判定（影响行数 0 = 前置状态不符，服务层转 10402），
     * 消除读-判-写竞态窗口——严格转换拒绝语义在并发下原子成立。
     * published_at 刷新为当次发布时间（重新发布语义）。
     * </p>
     *
     * @return 影响行数（0=当前状态不允许发布）
     */
    int publishFrom(@Param("tenantId") Long tenantId, @Param("id") Long id,
                    @Param("now") LocalDateTime now);

    /**
     * 撤回状态原子转换（已发布 1 → 已撤回 2；T-ADMIN-029）
     * <p>
     * 同 {@link #publishFrom} 的原子语义；published_at 保留作发布痕迹，已读记录不清理。
     * </p>
     *
     * @return 影响行数（0=当前状态不允许撤回）
     */
    int revokeFrom(@Param("tenantId") Long tenantId, @Param("id") Long id,
                   @Param("now") LocalDateTime now);
}