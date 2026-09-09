package cn.ac.fage.accessmesh.access.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
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
     * 跨资源类型批量查询（selectByTypeAndCodesAndCodeTypes 的多类型变体，T-PERM-028 复评 P2）
     * <p>
     * 业务键批量解析用：一次查询覆盖请求中的全部资源类型，调用方按 (resourceType, code, codeType)
     * 三元组在内存精确过滤，避免按类型循环查询。
     * </p>
     *
     * @param tenantId      租户ID
     * @param resourceTypes 资源类型值集合
     * @param codes         编码集合
     * @param codeTypes     编码类型集合
     * @return 资源列表（笛卡尔命中超集；调用方按三元组过滤）
     */
    List<ResourceEntity> selectByTypesAndCodesAndCodeTypes(@Param("tenantId") Long tenantId,
                                                           @Param("resourceTypes") Set<Integer> resourceTypes,
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
     * 批量停用资源实体（status=0，单条 SQL，启停路径批量 N+1 消除）
     *
     * @param tenantId  租户ID
     * @param ids       资源实体ID集合
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int batchDisableStatus(@Param("tenantId") Long tenantId,
                           @Param("ids") Set<Long> ids,
                           @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 批量刷新已有投影行的 name/status（batchUpsert 已有行路径，单条 SQL 替代循环 update）。
     * <p>
     * 每行值不同，使用 PostgreSQL {@code UPDATE ... FROM (VALUES ...)} 惯用法（项目为 PG 方言）。
     * </p>
     *
     * @param tenantId  租户ID
     * @param owner     所有者服务编码
     * @param resources 待刷新行（必须含主键 id）
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int batchUpdateValues(@Param("tenantId") Long tenantId,
                          @Param("owner") String owner,
                          @Param("resources") List<ResourceEntity> resources,
                          @Param("updatedAt") LocalDateTime updatedAt);

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
     * 批量祖先闭包查询结果类
     * <p>
     * 用于封装判定面继承目标闭包查询的结果，包含目标ID与其闭包成员ID（目标自身+同类型祖先）对。
     * </p>
     */
    @Getter
    @Setter
    class AncestorClosureResult {
        private Long targetId;
        private Long closureId;
    }

    /**
     * 判定面继承目标闭包批量查询（T-PERM-057，递归 CTE 上溯）
     * <p>
     * 对每个目标实体返回 {目标自身}∪同类型祖先链 的全部成员（闭包成员对 targetId×closureId，
     * 含 targetId=closureId 自身行）。上溯**止步同类型**（2026-09-09 用户定案：sync 通道允许
     * 跨类型父子边，跨类型祖先不参与闭包——两类型操作位空间各自独立，跨类型授权不越权放行）；
     * 软删祖先截断（delete_flag=0 过滤）；UNION 组合去重防 parent 环迭代不收敛（T-PERM-044 先例）。
     * 逐次加载全租户资源图不可接受（管理 API 每调用 1-3 门禁），故走目标下推 CTE 而非 selectAllValid。
     * </p>
     *
     * @param tenantId          租户ID
     * @param resourceEntityIds 目标资源实体ID集合
     * @return 闭包成员对列表（targetId×closureId，含自身行）
     */
    List<AncestorClosureResult> selectSelfAndAncestorClosureBatch(@Param("tenantId") Long tenantId,
                                                                   @Param("resourceEntityIds") Set<Long> resourceEntityIds);

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

    /**
     * 类型下有效资源行计数（T-PERM-052 类型所有权声明变更守卫）。
     *
     * @param tenantId     租户ID
     * @param resourceType resource_type 内部类型值
     * @return 行数（0=无有效行）
     */
    int existsValidByType(@Param("tenantId") Long tenantId,
                          @Param("resourceType") Integer resourceType);

    /**
     * 批量判定哪些类型值下存在有效资源行（T-PERM-052 类型删除守卫；一次查询防批删循环单查）。
     *
     * @param tenantId      租户ID
     * @param resourceTypes resource_type 内部类型值集合
     * @return 存在有效行的类型值列表（DISTINCT）
     */
    List<Integer> selectDistinctTypesWithValidRows(@Param("tenantId") Long tenantId,
                                                   @Param("resourceTypes") Collection<Integer> resourceTypes);
}