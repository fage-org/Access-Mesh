package cn.ac.fage.accessmesh.example.data.mapper;

import cn.ac.fage.accessmesh.example.data.entity.DemoPolicy;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DemoPolicyMapper {
    @Select("""
        SELECT p.* FROM demo_policy p JOIN demo_dataset d ON d.id=p.dataset_id AND d.tenant_id=p.tenant_id
        WHERE d.tenant_id=#{tenant} AND d.dataset_code='sales-records' AND d.delete_flag=0
          AND p.version=d.active_policy_version AND p.delete_flag=0
        """)
    DemoPolicy current(@Param("tenant") long tenant);

    @Select("SELECT octet_length(CAST(#{configuration} AS jsonb)::text)")
    int storedLength(@Param("configuration") String configuration);

    @Insert("""
        INSERT INTO demo_policy(tenant_id,dataset_id,version,configuration,change_summary,created_by,updated_by)
        VALUES(#{tenantId},#{datasetId},#{version},CAST(#{configuration} AS jsonb),#{changeSummary},#{createdBy},#{createdBy})
        """)
    int insert(DemoPolicy policy);
}
