package cn.ac.fage.accessmesh.example.metadata.mapper;

import cn.ac.fage.accessmesh.example.metadata.entity.DemoScope;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DemoScopeMapper {
    @Insert("""
        INSERT INTO demo_scope(tenant_id,resource_type_code,resource_code,code_type,name,enabled,created_by,updated_by)
        VALUES (#{tenantId},#{resourceTypeCode},#{resourceCode},#{codeType},#{name},#{enabled},#{createdBy},#{createdBy})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DemoScope scope);

    @Select("""
        SELECT id,tenant_id,resource_type_code,resource_code,code_type,name,enabled,version,publication_status,delete_flag,created_by,updated_by,deleted_by,created_at,updated_at,deleted_at
        FROM demo_scope WHERE tenant_id=#{tenant} AND resource_type_code=#{type}
          AND resource_code=#{code} AND code_type=#{codeType} AND delete_flag=0
        """)
    DemoScope find(@Param("tenant") long tenant, @Param("type") String type,
                   @Param("code") String code, @Param("codeType") String codeType);

    @Update("""
        UPDATE demo_scope SET name=#{name},enabled=#{enabled},version=version+1,
          publication_status='PENDING',updated_by=#{createdBy},updated_at=now()
        WHERE tenant_id=#{tenantId} AND id=#{id} AND version=#{version} AND delete_flag=0
        """)
    int update(DemoScope scope);
}
