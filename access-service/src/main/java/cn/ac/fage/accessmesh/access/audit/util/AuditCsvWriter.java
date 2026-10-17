package cn.ac.fage.accessmesh.access.audit.util;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Bounded UTF-8 CSV; the caller publishes bytes only after every row has passed validation. */
public final class AuditCsvWriter {
    private final int maxRows;
    private final int maxBytes;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private int rows;

    public AuditCsvWriter(int maxRows, int maxBytes) {
        if (maxRows < 1 || maxBytes < 3) throw new IllegalArgumentException("Invalid CSV limits");
        this.maxRows = maxRows;
        this.maxBytes = maxBytes;
        output.writeBytes(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
    }

    public void header(String... cells) { append(cells); }

    public void row(Object... cells) {
        if (++rows > maxRows) throw limit();
        append(cells);
    }

    private void append(Object[] cells) {
        StringBuilder line = new StringBuilder();
        for (Object cell : cells) {
            if (!line.isEmpty()) line.append(',');
            String value = cell == null ? "" : cell.toString();
            if (cell instanceof String && isFormula(value)) value = "'" + value;
            line.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        line.append("\r\n");
        byte[] bytes = line.toString().getBytes(StandardCharsets.UTF_8);
        if ((long) output.size() + bytes.length > maxBytes) throw limit();
        output.writeBytes(bytes);
    }

    private boolean isFormula(String value) {
        int i = 0;
        while (i < value.length() && (Character.isWhitespace(value.charAt(i)) || value.charAt(i) == '\uFEFF')) i++;
        return i < value.length() && "=+-@".indexOf(value.charAt(i)) >= 0;
    }

    private BizException limit() {
        return new BizException(AccessErrorCode.AUDIT_EXPORT_LIMIT_EXCEEDED.getCode(),
            "导出超过行数或文件大小上限，请缩小筛选范围后重试");
    }

    public byte[] bytes() { return output.toByteArray(); }
}
