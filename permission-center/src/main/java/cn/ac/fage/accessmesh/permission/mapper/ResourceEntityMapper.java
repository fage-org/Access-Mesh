package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 资源实体数据访问接口
 * <p>
 * 提供资源实体表的基础CRUD操作和自定义查询方法。
 * 资源实体是权限系统的核心实体，代表可被权限控制的资源对象。
 * 支持批量软删除、后代递归查询等操作。
 * </p>
 */
public interface ResourceEntityMapper extends BaseMapper<ResourceEntity> {

    /**
     * 根据ID查询有效资源（含租户校验）
     *
     * @param tenantId  租户ID
     * @param resourceId 资源ID
     * @return 资源实体
     */
    ResourceEntity selectValidById(@Param("tenantId") Long tenantId,
                                   @Param("resourceId") Long resourceId);

    /**
     * 批量查询有效资源（按ID集合）
     *
     * @param tenantId    租户ID
     * @param resourceIds 资源ID集合
     * @return 资源列表
     */
    List<ResourceEntity> selectValidByIds(@Param("tenantId") Long tenantId,
                                          @Param("resourceIds") Set<Long> resourceIds);

    /**
     * 查询已存在的编码集合
     *
     * @param tenantId 租户ID
     * @param codes    编码集合
     * @return 已存在的编码集合
     */
    Set<String> selectExistingCodes(@Param("tenantId") Long tenantId,
                                    @Param("codes") Set<String> codes);

    /**
     * 查找指定租户、类型、编码和编码类型的资源
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @param code         资源编码
     * @param codeType     编码类型
     * @return 资源实体
     */
    ResourceEntity selectByTypeCodeAndCodeType(@Param("tenantId") Long tenantId,
                                                @Param("resourceType") Integer resourceType,
                                                @Param("code") String code,
                                                @Param("codeType") String codeType);

    /**
     * 批量查询指定类型和编码集合的资源
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @param codes        编码集合
     * @return 资源列表
     */
    List<ResourceEntity> selectByTypeAndCodes(@Param("tenantId") Long tenantId,
                                               @Param("resourceType") Integer resourceType,
                                               @Param("codes") Set<String> codes);

    /**
     * 批量查询指定类型 + 编码 + 编码类型的资源（按编码类型分桶后再查询）。
     * <p>
     * 用于 full-sync 阶段 B 一次性预加载所有 (resourceType, code, codeType) 组合，避免循环单条 select。
     * codeType 可能存在多种取值（如 "default"、"path"），此查询按 codeType 列表展开 OR 条件。
     * </p>
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @param codes        编码集合
     * @param codeTypes    编码类型集合
     * @return 资源列表（包含 (codeType, code) 命中的所有有效行；调用方再按 (code, codeType) 二维 key 分组）
     */
    List<ResourceEntity> selectByTypeAndCodesAndCodeTypes(@Param("tenantId") Long tenantId,
                                                          @Param("resourceType") Integer resourceType,
                                                          @Param("codes") Set<String> codes,
                                                          @Param("codeTypes") Set<String> codeTypes);

    /**
     * 查询指定租户和类型的所有API资源
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @return 资源列表
     */
    List<ResourceEntity> selectApiResourcesByType(@Param("tenantId") Long tenantId,
                                                   @Param("resourceType") Integer resourceType);

    /**
     * 批量软删除资源实体
     * <p>
     * 将指定资源实体的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的资源实体ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 使用PostgreSQL递归CTE批量查询多个资源的所有后代资源ID
     * <p>
     * 从多个资源实体开始，向下递归查询所有后代资源的ID。
     * 用于批量级联操作场景，提高查询效率。
     * </p>
     *
     * @param tenantId          租户ID
     * @param resourceEntityIds 资源实体ID集合
     * @return 后代查询结果列表，包含resourceId和descendantId对
     */
    List<DescendantResult> selectDescendantIdsBatch(@Param("tenantId") Long tenantId,
                                                     @Param("resourceEntityIds") java.util.Set<Long> resourceEntityIds);

    /**
     * 批量后代查询结果类
     * <p>
     * 用于封装批量后代查询的结果，包含资源ID和后代ID对。
     * </p>
     */
    @Getter
    @Setter
    class DescendantResult {
        private Long resourceId;
        private Long descendantId;
    }

    /**
     * 查询所有有效资源实体（用于权限树构建）
     *
     * @param tenantId 租户ID
     * @return 资源实体列表
     */
    List<ResourceEntity> selectAllValid(@Param("tenantId") Long tenantId);

    /**
     * 根据资源类型集合查询有效资源实体（用于接口快照scopeAll）
     *
     * @param tenantId      租户ID
     * @param resourceTypes 资源类型值集合
     * @return 资源实体列表
     */
    List<ResourceEntity> selectValidByResourceTypes(@Param("tenantId") Long tenantId,
                                                     @Param("resourceTypes") Set<Integer> resourceTypes);

    /**
     * 查询资源树（所有有效且启用的资源，可选资源类型过滤）
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值，可选
     * @param matchNone    是否匹配空结果（用于域过滤不匹配时）
     * @return 资源实体列表
     */
    List<ResourceEntity> selectResourceTree(@Param("tenantId") Long tenantId,
                                             @Param("resourceType") Integer resourceType,
                                             @Param("matchNone") boolean matchNone);

    /**
     * 分页查询资源列表（带过滤条件）
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值，可选
     * @param matchNone    是否匹配空结果
     * @param offset       偏移量
     * @param limit        每页数量
     * @return 资源实体列表
     */
    List<ResourceEntity> selectResourceListPaged(@Param("tenantId") Long tenantId,
                                                   @Param("resourceType") Integer resourceType,
                                                   @Param("matchNone") boolean matchNone,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    /**
     * 统计资源数量（带过滤条件）
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值，可选
     * @param matchNone    是否匹配空结果
     * @return 资源总数
     */
    long selectResourceListCount(@Param("tenantId") Long tenantId,
                                  @Param("resourceType") Integer resourceType,
                                  @Param("matchNone") boolean matchNone);
}