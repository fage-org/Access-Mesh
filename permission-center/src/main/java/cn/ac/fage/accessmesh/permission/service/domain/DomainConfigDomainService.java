package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;

/**
 * 域配置领域服务接口
 * <p>
 * 提供域配置（DomainConfig）、业务域（BizDomain）、类型定义（TypeDefinition）
 * 的基础数据访问操作。这些实体是权限系统的基础配置数据，
 * 用于定义业务域、资源类型、角色类型等系统级配置。
 * 该接口封装统一的领域层访问API，供上层服务调用。
 * </p>
 */
public interface DomainConfigDomainService {

    /**
     * 根据ID查询域配置
     *
     * @param id 域配置ID
     * @return 域配置实体，不存在返回null
     */
    DomainConfig selectOneById(Long id);

    /**
     * 根据查询条件查询域配置列表
     *
     * @param qw QueryWrapper查询条件
     * @return 域配置列表
     */
    List<DomainConfig> selectListByQuery(QueryWrapper qw);

    /**
     * 根据查询条件查询单个域配置
     *
     * @param qw QueryWrapper查询条件
     * @return 域配置实体，不存在返回null
     */
    DomainConfig selectOneByQuery(QueryWrapper qw);

    /**
     * 根据查询条件统计域配置数量
     *
     * @param qw QueryWrapper查询条件
     * @return 匹配的域配置数量
     */
    long selectCountByQuery(QueryWrapper qw);

    /**
     * 插入域配置
     *
     * @param entity 域配置实体
     */
    void insert(DomainConfig entity);

    /**
     * 更新域配置
     *
     * @param entity 域配置实体
     * @return 更新影响的行数
     */
    int update(DomainConfig entity);

    // ===== 业务域操作 =====

    /**
     * 根据ID查询业务域
     *
     * @param id 业务域ID
     * @return 业务域实体，不存在返回null
     */
    BizDomain selectBizDomainById(Long id);

    /**
     * 根据查询条件查询业务域列表
     *
     * @param qw QueryWrapper查询条件
     * @return 业务域列表
     */
    List<BizDomain> selectBizDomainsByQuery(QueryWrapper qw);

    /**
     * 根据查询条件查询单个业务域
     *
     * @param qw QueryWrapper查询条件
     * @return 业务域实体，不存在返回null
     */
    BizDomain selectBizDomainByQuery(QueryWrapper qw);

    /**
     * 插入业务域
     *
     * @param entity 业务域实体
     */
    void insertBizDomain(BizDomain entity);

    /**
     * 更新业务域
     *
     * @param entity 业务域实体
     * @return 更新影响的行数
     */
    int updateBizDomain(BizDomain entity);

    // ===== 类型定义操作 =====

    /**
     * 根据ID查询类型定义
     *
     * @param id 类型定义ID
     * @return 类型定义实体，不存在返回null
     */
    TypeDefinition selectTypeDefinitionById(Long id);

    /**
     * 根据查询条件查询类型定义列表
     *
     * @param qw QueryWrapper查询条件
     * @return 类型定义列表
     */
    List<TypeDefinition> selectTypeDefinitionsByQuery(QueryWrapper qw);

    /**
     * 根据查询条件查询单个类型定义
     *
     * @param qw QueryWrapper查询条件
     * @return 类型定义实体，不存在返回null
     */
    TypeDefinition selectTypeDefinitionByQuery(QueryWrapper qw);

    /**
     * 插入类型定义
     *
     * @param entity 类型定义实体
     */
    void insertTypeDefinition(TypeDefinition entity);

    /**
     * 更新类型定义
     *
     * @param entity 类型定义实体
     * @return 更新影响的行数
     */
    int updateTypeDefinition(TypeDefinition entity);
}