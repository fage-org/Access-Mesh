package cn.ac.fage.accessmesh.example.data.mapper;

import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import java.util.List;
import java.util.Map;

public final class DemoImportSql {
    public String insert(Map<String,Object> args) {
        var rows=(List<?>)args.get("rows");
        @SuppressWarnings("unchecked") var fields=(List<DemoField>)args.get("fields");
        if(rows.isEmpty() || rows.size()*(fields.size()+4)>4096) throw new IllegalStateException("Invalid import chunk");
        StringBuilder sql=new StringBuilder("INSERT INTO demo_record(tenant_id,batch_id,created_by,updated_by");
        fields.forEach(f -> sql.append(',').append(f.column())); sql.append(") VALUES ");
        for(int i=0;i<rows.size();i++) {
            if(i>0) sql.append(',');
            String row="rows["+i+"]";
            sql.append("(#{").append(row).append(".tenantId},#{").append(row).append(".batchId},#{").append(row).append(".createdBy},#{").append(row).append(".createdBy}");
            for(var field:fields) sql.append(",#{").append(row).append('.').append(field.key()).append('}');
            sql.append(')');
        }
        return sql.toString();
    }
}
