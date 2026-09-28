package cn.ac.fage.accessmesh.access.resource.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务配置数据访问接口
 * <p>
 * 提供服务配置表的基础CRUD操作和自定义查询方法。
 * 服务配置存储各微服务的配置信息，如服务名称、描述、同步策略等。
 * 支持批量软删除操作。
 * </p>
 */
public interface ServiceConfigMapper extends BaseMapper<ServiceConfig> {

    /**
     * 根据租户ID和服务编码查询有效服务配置
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return 服务配置实体
     */
    ServiceConfig selectByTenantAndServiceCode(@Param("tenantId") Long tenantId,
                                               @Param("serviceCode") String serviceCode);

    /**
     * 批量软删除服务配置
     * <p>
     * 将指定服务配置的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的服务配置ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据租户ID查询所有有效服务配置列表
     *
     * @param tenantId 租户ID
     * @return 服务配置列表
     */
    List<ServiceConfig> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 根据租户ID和ID集合查询有效服务配置列表
     *
     * @param tenantId 租户ID
     * @param ids      服务配置ID集合
     * @return 服务配置列表
     */
    List<ServiceConfig> selectValidByIds(@Param("tenantId") Long tenantId,
                                          @Param("ids") java.util.Set<Long> ids);

    /**
     * 递增服务准入快照配置代次（T-ACCESS-059 计数列载体）。
     * <p>
     * 由该服务映射写路径（共用保存入口/删除/FULL 清理）与模式切换在同事务调用；
     * 准入快照构建以「构建前后代次比对、变更即废弃重建」消费（2026-09-25 拍板限定语义）。
     * 服务行不存在时返回 0（新增映射行会因服务未登记先行失败，此处不重复校验）。
     * </p>
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return 更新的行数
     */
    int incrementConfigGeneration(@Param("tenantId") Long tenantId,
                                  @Param("serviceCode") String serviceCode);

    /**
     * 独立语句读取配置代次（T-ACCESS-059）。
     * <p>
     * 供准入快照构建的代次复读使用：与 {@link #selectByTenantAndServiceCode} 语句不同，
     * 不命中 MyBatis 会话缓存同语句二次返回同实例的假读——同事务内两次调用各自落库。
     * </p>
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return 配置代次；服务行不存在返回 null
     */
    Long selectConfigGeneration(@Param("tenantId") Long tenantId,
                                @Param("serviceCode") String serviceCode);

    /**
     * 独立语句读取鉴权状态（启停+模式+代次）。
     * <p>
     * 供准入快照构建的终校验使用（构建期切模式/停用的代次保护）：与
     * {@link #selectConfigGeneration} 同款 {@code flushCache} 独立语句形态，
     * 不命中会话缓存——代次稳定只证明构建期间无变更，模式/启停还须在返回前
     * 强制落库复读确认（入口校验与首次代次读之间发生的变更不改变代次比对结果）。
     * </p>
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return 仅填充 status/apiAuthMode/configGeneration 的配置行；服务行不存在返回 null
     */
    ServiceConfig selectAuthState(@Param("tenantId") Long tenantId,
                                  @Param("serviceCode") String serviceCode);
}