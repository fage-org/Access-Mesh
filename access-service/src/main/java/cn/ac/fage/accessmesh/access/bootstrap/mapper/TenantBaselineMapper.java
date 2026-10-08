package cn.ac.fage.accessmesh.access.bootstrap.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 专属初始化通道：SQL 种子仍由权威 DDL 的函数维护；语句见 mapper/bootstrap/TenantBaselineMapper.xml。 */
@Mapper
public interface TenantBaselineMapper {
    String initialize(@Param("tenantId") long tenantId);
}
