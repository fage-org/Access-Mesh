package cn.ac.fage.accessmesh.example.metadata.mapper;

import cn.ac.fage.accessmesh.example.metadata.entity.DemoReportField;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import java.util.List;

@Mapper
public interface DemoReportFieldMapper {
    @Insert("""
        INSERT INTO demo_report_field(tenant_id, report_id, field_key, created_by, updated_by)
        SELECT #{tenantId}, r.id, #{fieldKey}, #{createdBy}, #{createdBy}
        FROM demo_report r
        WHERE r.tenant_id = #{tenantId} AND r.id = #{reportId} AND r.delete_flag = 0
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(DemoReportField field);

    @Select("""
        SELECT f.id, f.tenant_id, f.report_id, f.field_key, f.resource_code, f.enabled,
               f.version, f.publication_status, f.delete_flag, f.created_by, f.updated_by,
               f.deleted_by, f.created_at, f.updated_at, f.deleted_at
        FROM demo_report_field f
        JOIN demo_report r ON r.id = f.report_id AND r.tenant_id = f.tenant_id AND r.delete_flag = 0
        WHERE f.tenant_id = #{tenantId} AND f.report_id = #{reportId} AND f.delete_flag = 0
        ORDER BY f.id
        """)
    List<DemoReportField> selectByReport(@Param("tenantId") long tenantId, @Param("reportId") long reportId);
}
