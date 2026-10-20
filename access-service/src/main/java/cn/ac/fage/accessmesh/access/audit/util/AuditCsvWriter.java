package cn.ac.fage.accessmesh.access.audit.util;

import cn.ac.fage.accessmesh.common.csv.BoundedCsvWriter;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;

/** Audit-specific error boundary around the shared bounded CSV formatter. */
public final class AuditCsvWriter {
    private final BoundedCsvWriter writer;
    public AuditCsvWriter(int maxRows,int maxBytes) {
        writer=new BoundedCsvWriter(maxRows,maxBytes,()->new BizException(AccessErrorCode.AUDIT_EXPORT_LIMIT_EXCEEDED.getCode(),
            "导出超过行数或文件大小上限，请缩小筛选范围后重试"));
    }
    public void header(String... cells) { writer.header(cells); }
    public void row(Object... cells) { writer.row(cells); }
    public byte[] bytes() { return writer.bytes(); }
}
