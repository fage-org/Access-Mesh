package cn.ac.fage.accessmesh.common.mybatis;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;

/**
 * 租户安全查询工具类
 * <p>
 * 提供显式包含tenant_id和delete_flag条件的安全查询方法，
 * 作为多租户数据隔离的深度防御措施。
 * </p>
 *
 * <p>这些方法补充MyBatis-Flex的自动租户过滤功能，
 * 通过添加显式的WHERE条件确保租户隔离，
 * 即使TenantContextHolder为空时也能保证数据隔离
 * （如定时任务或后台线程场景）。
 * </p>
 */
public final class TenantSafeQuery {

    /**
     * 私有构造方法（工具类）
     */
    private TenantSafeQuery() {
        // 工具类，禁止实例化
    }

    /**
     * 安全单条查询
     * <p>
     * 通过ID查询单条实体，显式添加tenant_id和delete_flag条件。
     * 替代不安全的{@code mapper.selectOneById(id)}调用。
     * </p>
     *
     * @param mapper       MyBatis-Flex Mapper
     * @param idColumn     表的ID列（如SYS_CONFIG.ID）
     * @param tenantColumn 表的tenant_id列（如SYS_CONFIG.TENANT_ID）
     * @param delColumn    表的delete_flag列（如SYS_CONFIG.DELETE_FLAG）
     * @param tenantId     租户ID
     * @param id           实体ID
     * @param <T>          实体类型
     * @return 实体对象，未找到时返回null
     */
    public static <T> T selectOneByIdSafe(
            BaseMapper<T> mapper,
            QueryColumn idColumn,
            QueryColumn tenantColumn,
            QueryColumn delColumn,
            Long tenantId,
            Long id) {
        if (tenantId == null || id == null) {
            return null;
        }
        return mapper.selectOneByQuery(
            QueryWrapper.create()
                .where(idColumn.eq(id))
                .and(tenantColumn.eq(tenantId))
                .and(delColumn.eq(0))
        );
    }

    /**
     * 安全批量查询
     * <p>
     * 通过ID列表查询多条实体，显式添加tenant_id和delete_flag条件。
     * 替代多租户表上不安全的批量查询。
     * </p>
     *
     * @param mapper       MyBatis-Flex Mapper
     * @param idColumn     表的ID列
     * @param tenantColumn 表的tenant_id列
     * @param delColumn    表的delete_flag列
     * @param tenantId     租户ID
     * @param ids          实体ID列表
     * @param <T>          实体类型
     * @return 实体列表
     */
    public static <T> List<T> selectListByIdsSafe(
            BaseMapper<T> mapper,
            QueryColumn idColumn,
            QueryColumn tenantColumn,
            QueryColumn delColumn,
            Long tenantId,
            List<Long> ids) {
        if (tenantId == null || ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectListByQuery(
            QueryWrapper.create()
                .where(idColumn.in(ids))
                .and(tenantColumn.eq(tenantId))
                .and(delColumn.eq(0))
        );
    }

    /**
     * 确保查询包含租户过滤条件
     * <p>
     * 为现有QueryWrapper添加显式的tenant_id和delete_flag条件。
     * 用于增强已有查询的租户隔离。
     * </p>
     *
     * @param qw           现有QueryWrapper
     * @param tenantColumn 表的tenant_id列
     * @param delColumn    表的delete_flag列
     * @param tenantId     租户ID
     * @return 增强后的QueryWrapper（同一实例，支持链式调用）
     */
    public static QueryWrapper ensureTenantFilter(
            QueryWrapper qw,
            QueryColumn tenantColumn,
            QueryColumn delColumn,
            Long tenantId) {
        if (tenantId != null) {
            qw.and(tenantColumn.eq(tenantId));
        }
        qw.and(delColumn.eq(0));
        return qw;
    }
}