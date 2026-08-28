package cn.ac.fage.accessmesh.access.permission.mapper;

import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 类型定义数据访问接口
 * <p>
 * 提供类型定义表的基础CRUD操作和自定义查询方法。
 * 类型定义存储系统中各种枚举类型的定义，如资源类型、角色类型、操作类型等。
 * 支持批量软删除操作。
 * </p>
 */
public interface TypeDefinitionMapper extends BaseMapper<TypeDefinition> {

    /**
     * 根据租户、类型键和类型编码查询有效类型定义
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @param typeCode 类型编码
     * @return 类型定义实体
     */
    TypeDefinition selectByTypeKeyAndCode(@Param("tenantId") Long tenantId,
                                          @Param("typeKey") String typeKey,
                                          @Param("typeCode") String typeCode);

    /**
     * 根据租户、类型键和类型值查询有效类型定义
     *
     * @param tenantId  租户ID
     * @param typeKey   类型键
     * @param typeValue 类型值
     * @return 类型定义实体
     */
    TypeDefinition selectByTypeKeyAndValue(@Param("tenantId") Long tenantId,
                                           @Param("typeKey") String typeKey,
                                           @Param("typeValue") Integer typeValue);

    /**
     * 批量查询类型定义（按类型键和编码集合）
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @param codes    类型编码集合
     * @return 类型定义列表
     */
    List<TypeDefinition> selectByTypeKeyAndCodes(@Param("tenantId") Long tenantId,
                                                  @Param("typeKey") String typeKey,
                                                  @Param("codes") Set<String> codes);

    /**
     * 批量查询类型定义（按类型键和值集合）
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @param values   类型值集合
     * @return 类型定义列表
     */
    List<TypeDefinition> selectByTypeKeyAndValues(@Param("tenantId") Long tenantId,
                                                   @Param("typeKey") String typeKey,
                                                   @Param("values") Set<Integer> values);

    /**
     * 根据ID和租户查询有效类型定义（含isSystem字段）
     *
     * @param tenantId  租户ID
     * @param typeDefId 类型定义ID
     * @return 类型定义实体
     */
    TypeDefinition selectValidById(@Param("tenantId") Long tenantId,
                                   @Param("typeDefId") Long typeDefId);

    /**
     * 批量软删除类型定义
     * <p>
     * 将指定类型定义的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId 租户ID
     * @param ids       待删除的类型定义ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据租户ID和ID集合查询有效类型定义列表
     *
     * @param tenantId 租户ID
     * @param ids      类型定义ID集合
     * @return 类型定义列表
     */
    List<TypeDefinition> selectValidByIds(@Param("tenantId") Long tenantId,
                                           @Param("ids") Set<Long> ids);

    /**
     * 根据租户ID和类型键查询所有有效类型定义列表
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @return 类型定义列表
     */
    List<TypeDefinition> selectByTenantAndTypeKey(@Param("tenantId") Long tenantId,
                                                   @Param("typeKey") String typeKey);

    /**
     * 查询租户+类型键内全量行（含软删行）的 typeValue 最大值
     * <p>
     * 用于 typeValue 自动分配：max+1 且软删不复用（已删行的 typeValue 仍占位）。
     * 不加 delete_flag 过滤是该方法的语义本身。
     * </p>
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @return 最大 typeValue，无任何行时返回 null
     */
    Integer selectMaxTypeValueAllRows(@Param("tenantId") Long tenantId,
                                      @Param("typeKey") String typeKey);

    /**
     * 按条件统计有效类型定义数量
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键，可选（精确过滤）
     * @param keyword  关键字，可选（name/typeCode LIKE，大小写敏感）
     * @return 有效行数
     */
    long countByCondition(@Param("tenantId") Long tenantId,
                          @Param("typeKey") String typeKey,
                          @Param("keyword") String keyword);

    /**
     * 按条件分页查询有效类型定义（ORDER BY sort_order, id）
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键，可选（精确过滤）
     * @param keyword  关键字，可选（name/typeCode LIKE，大小写敏感）
     * @param limit    每页条数
     * @param offset   偏移量
     * @return 类型定义列表
     */
    List<TypeDefinition> selectPageByCondition(@Param("tenantId") Long tenantId,
                                                @Param("typeKey") String typeKey,
                                                @Param("keyword") String keyword,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);
}