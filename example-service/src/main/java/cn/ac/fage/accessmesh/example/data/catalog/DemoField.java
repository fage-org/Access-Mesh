package cn.ac.fage.accessmesh.example.data.catalog;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.math.BigDecimal;

/** Closed physical dataset catalog. Configuration can reference keys, never SQL identifiers. */
public enum DemoField {
    RECORD_CODE("recordCode", "record_code", Kind.STRING, 64),
    OWNER_USER_ID("ownerUserId", "owner_user_id", Kind.USER_ID, 0),
    TEAM_CODE("teamCode", "team_code", Kind.STRING, 64),
    DEPARTMENT_CODE("departmentCode", "department_code", Kind.STRING, 64),
    REGION_CODE("regionCode", "region_code", Kind.STRING, 64),
    SALE_DATE("saleDate", "sale_date", Kind.DATE, 0),
    PRODUCT_NAME("productName", "product_name", Kind.STRING, 128),
    AMOUNT("amount", "amount", Kind.DECIMAL, 0),
    REMARK("remark", "remark", Kind.STRING, 1000);

    public enum Kind { STRING, USER_ID, DATE, DECIMAL }
    private final String key;
    private final String column;
    private final Kind kind;
    private final int maxLength;
    DemoField(String key, String column, Kind kind, int maxLength) {
        this.key=key; this.column=column; this.kind=kind; this.maxLength=maxLength;
    }
    public String key() { return key; }
    public String column() { return column; }
    public Kind kind() { return kind; }
    public int maxLength() { return maxLength; }

    public static DemoField require(String key) {
        for (DemoField field:values()) if (field.key.equals(key)) return field;
        throw invalid();
    }

    public Object literal(JsonNode value) {
        if (value==null || value.isNull()) throw invalid();
        return switch(kind) {
            case STRING -> {
                if (!value.isTextual() || value.asText().length()>maxLength) throw invalid();
                yield value.asText();
            }
            case USER_ID -> {
                if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong()<=0) throw invalid();
                yield value.asLong();
            }
            case DATE -> {
                if (!value.isTextual() || !value.asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
                try {
                    var date=LocalDate.parse(value.asText());
                    if (date.getYear()<1) throw invalid();
                    yield date;
                } catch (java.time.DateTimeException e) { throw invalid(); }
            }
            case DECIMAL -> {
                if (!value.isNumber()) throw invalid();
                BigDecimal decimal;
                try { decimal=value.decimalValue(); } catch (NumberFormatException e) { throw invalid(); }
                if (decimal.scale()>2 || (long)decimal.precision()-decimal.scale()>16) throw invalid();
                yield decimal;
            }
        };
    }

    private static BizException invalid() {
        return new BizException(ExampleErrorCode.DEMO_POLICY_INVALID.getCode(),"未知字段或字段值类型不匹配");
    }
}
