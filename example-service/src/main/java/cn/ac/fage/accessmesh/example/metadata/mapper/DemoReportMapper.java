package cn.ac.fage.accessmesh.example.metadata.mapper;

import cn.ac.fage.accessmesh.example.metadata.entity.DemoReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface DemoReportMapper {
    @Insert("""
        INSERT INTO demo_report(tenant_id, dataset_id, report_code, name, field_resource_type_code, created_by, updated_by)
        SELECT #{tenantId}, d.id, #{reportCode}, #{name}, #{fieldResourceTypeCode}, #{createdBy}, #{createdBy}
        FROM demo_dataset d
        WHERE d.tenant_id = #{tenantId} AND d.id = #{datasetId} AND d.delete_flag = 0
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DemoReport report);

    @Select("""
        SELECT r.id, r.tenant_id, r.dataset_id, r.report_code, r.name, r.field_resource_type_code,
               r.enabled, r.version, r.catalog_version, r.publication_status, r.delete_flag,
               r.created_by, r.updated_by, r.deleted_by, r.created_at, r.updated_at, r.deleted_at
        FROM demo_report r
        JOIN demo_dataset d ON d.id = r.dataset_id AND d.tenant_id = r.tenant_id AND d.delete_flag = 0
        WHERE r.tenant_id = #{tenantId} AND r.report_code = #{reportCode} AND r.delete_flag = 0
        """)
    DemoReport selectByCode(@Param("tenantId") long tenantId, @Param("reportCode") String reportCode);
}
