package cn.ac.fage.accessmesh.example.data.mapper;

import cn.ac.fage.accessmesh.example.data.entity.DemoImportBatch;
import cn.ac.fage.accessmesh.example.data.entity.DemoRecord;
import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.UUID;

@Mapper
public interface DemoImportMapper {
    @Select("SELECT * FROM demo_import_batch WHERE tenant_id=#{tenant} AND import_key=#{key} AND delete_flag=0")
    DemoImportBatch find(@Param("tenant") long tenant,@Param("key") UUID key);
    @Insert("""
        INSERT INTO demo_import_batch(tenant_id,report_id,import_key,file_sha256,imported_fields,record_count,policy_version,catalog_version,created_by,updated_by)
        VALUES(#{tenantId},#{reportId},#{importKey},#{fileSha256},CAST(#{importedFields} AS jsonb),#{recordCount},#{policyVersion},#{catalogVersion},#{createdBy},#{createdBy})
        """)
    @Options(useGeneratedKeys=true,keyProperty="id",keyColumn="id")
    int insert(DemoImportBatch batch);
    @InsertProvider(type=DemoImportSql.class,method="insert")
    int insertRecords(@Param("rows") List<DemoRecord> rows,@Param("fields") List<DemoField> fields);
    @Update("""
        UPDATE demo_record SET delete_flag=id,deleted_by=#{actor},deleted_at=now(),updated_by=#{actor},updated_at=now()
        WHERE tenant_id=#{tenant} AND batch_id=#{batch} AND delete_flag=0
        """)
    int revokeRecords(@Param("tenant") long tenant,@Param("batch") long batch,@Param("actor") long actor);
    @Update("""
        UPDATE demo_import_batch SET status='REVOKED',updated_by=#{actor},updated_at=now()
        WHERE tenant_id=#{tenant} AND id=#{batch} AND status='IMPORTED' AND delete_flag=0
        """)
    int revokeBatch(@Param("tenant") long tenant,@Param("batch") long batch,@Param("actor") long actor);
}
