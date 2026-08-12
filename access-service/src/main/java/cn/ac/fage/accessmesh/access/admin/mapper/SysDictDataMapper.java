package cn.ac.fage.accessmesh.access.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.admin.entity.SysDictData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统字典数据数据访问接口
 * <p>
 * 提供字典数据表的基础CRUD操作。
 * 字典数据归属于字典类型，包含标签、值、排序等属性。
 * </p>
 */
@Mapper
public interface SysDictDataMapper extends BaseMapper<SysDictData> {

    /**
     * 根据ID和租户ID安全查询单条字典数据
     *
     * @param tenantId 租户ID
     * @param id       字典数据ID
     * @return 字典数据实体，未找到时返回null
     */
    SysDictData selectByIdSafe(@Param("tenantId") Long tenantId,
                               @Param("id") Long id);

    /**
     * 根据租户ID和字典类型编码列表查询字典数据中存在的类型（去重）
     * <p>
     * 用于检查指定字典类型下是否有关联数据。
     * </p>
     *
     * @param tenantId  租户ID
     * @param dictTypes 字典类型编码列表
     * @return 包含dictType字段的字典数据列表（已去重）
     */
    List<SysDictData> selectDistinctDictTypesByTenantAndTypes(@Param("tenantId") Long tenantId,
                                                              @Param("dictTypes") List<String> dictTypes);

    /**
     * 根据租户ID和字典类型编码列表查询字典数据（按排序正序）
     *
     * @param tenantId  租户ID
     * @param dictTypes 字典类型编码列表
     * @return 字典数据列表
     */
    List<SysDictData> selectByTenantAndDictTypes(@Param("tenantId") Long tenantId,
                                                  @Param("dictTypes") List<String> dictTypes);

    /**
     * 根据租户ID和字典类型编码查询字典数据（按排序正序）
     *
     * @param tenantId 租户ID
     * @param dictType 字典类型编码
     * @return 字典数据列表
     */
    List<SysDictData> selectByTenantAndDictType(@Param("tenantId") Long tenantId,
                                                 @Param("dictType") String dictType);
}
