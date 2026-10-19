package cn.ac.fage.accessmesh.example.metadata.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DemoDatasetMapper {
    @Insert("""
        INSERT INTO demo_dataset(tenant_id,dataset_code,created_by,updated_by)
        VALUES (#{tenant},'sales-records',#{actor},#{actor})
        ON CONFLICT (tenant_id,dataset_code) WHERE delete_flag=0 DO NOTHING
        """)
    int ensure(@Param("tenant") long tenant, @Param("actor") long actor);

    @Select("""
        SELECT id FROM demo_dataset
        WHERE tenant_id=#{tenant} AND dataset_code='sales-records' AND delete_flag=0 FOR UPDATE
        """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    Long lock(@Param("tenant") long tenant);

    @Select("""
        SELECT EXISTS(SELECT 1 FROM demo_scope WHERE tenant_id=#{tenant} AND resource_type_code=#{type})
          OR EXISTS(SELECT 1 FROM demo_policy WHERE tenant_id=#{tenant}
            AND (configuration #>> '{read,rowResourceTypeCode}'=#{type}
              OR configuration #>> '{edit,rowResourceTypeCode}'=#{type}))
        """)
    boolean usedForScope(@Param("tenant") long tenant, @Param("type") String type);

    @Select("""
        SELECT EXISTS(SELECT 1 FROM demo_report WHERE tenant_id=#{tenant} AND field_resource_type_code=#{type})
        """)
    boolean usedForField(@Param("tenant") long tenant, @Param("type") String type);

    @Select("""
        <script>
        SELECT DISTINCT field_resource_type_code FROM demo_report WHERE tenant_id=#{tenant} AND field_resource_type_code IN
        <foreach collection="types" item="type" open="(" close=")" separator=",">#{type}</foreach>
        </script>
        """)
    java.util.List<String> fieldTypes(@Param("tenant") long tenant,@Param("types") java.util.Set<String> types);

    @Select("SELECT active_policy_version FROM demo_dataset WHERE tenant_id=#{tenant} AND dataset_code='sales-records' AND delete_flag=0")
    Long policyVersion(@Param("tenant") long tenant);

    @org.apache.ibatis.annotations.Update("""
        UPDATE demo_dataset SET active_policy_version=#{next},updated_by=#{actor},updated_at=now()
        WHERE tenant_id=#{tenant} AND dataset_code='sales-records' AND active_policy_version=#{expected} AND delete_flag=0
        """)
    int activatePolicy(@Param("tenant") long tenant,@Param("expected") long expected,@Param("next") long next,@Param("actor") long actor);
}
