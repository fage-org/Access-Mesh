package cn.ac.fage.accessmesh.example.data;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.data.csv.DemoCsvReader;
import cn.ac.fage.accessmesh.example.data.catalog.DemoField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class DemoCsvReaderTest {
    static final String HEADER="recordCode,ownerUserId,teamCode,departmentCode,regionCode,saleDate,productName,amount,remark\r\n";
    private final DemoCsvReader csv=new DemoCsvReader();
    @Test void shouldParseBomQuotedCommaAndMultilineRemarkWithoutLosingDecimalPrecision() {
        var result=csv.read(("\uFEFF"+HEADER+"A,42,T1,D1,R1,2026-10-19,\"one,two\",9999999999999999.99,\"line1\nline2\"\r\n").getBytes(StandardCharsets.UTF_8));
        assertNotNull(result); assertEquals(1,result.rows().size());
        assertEquals("one,two",result.rows().getFirst().get(DemoField.PRODUCT_NAME));
        assertEquals("line1\nline2",result.rows().getFirst().get(DemoField.REMARK));
        assertEquals(new java.math.BigDecimal("9999999999999999.99"),result.rows().getFirst().get(DemoField.AMOUNT));
    }
    @ParameterizedTest @ValueSource(strings={"quote","bad-date","bad-money","unknown-header","missing-column","empty"})
    void shouldRejectInvalidInputWithoutEchoingValues(String defect) {
        String content=HEADER+"A,42,T1,D1,R1,2026-10-19,product,1.00,remark\n";
        content=switch(defect) {
            case "quote" -> HEADER+"A,42,T1,D1,R1,2026-10-19,\"secret-value,1.00,remark";
            case "bad-date" -> content.replace("2026-10-19","2026-02-30");
            case "bad-money" -> content.replace("1.00","1.001");
            case "unknown-header" -> content.replace("productName","secret-value");
            case "missing-column" -> content.replace("ownerUserId,","");
            case "empty" -> HEADER;
            default -> throw new AssertionError();
        };
        byte[] input=content.getBytes(StandardCharsets.UTF_8);
        var error=assertThrows(BizException.class,()->csv.read(input));
        assertEquals(30105,error.getErrorCode()); assertFalse(error.getMessage().contains("secret-value"));
    }
    @Test void shouldRejectMalformedUtf8AndCapacityOverflow() {
        assertEquals(30105,assertThrows(BizException.class,()->csv.read(new byte[]{(byte)0xc3,0x28})).getErrorCode());
        assertEquals(30106,assertThrows(BizException.class,()->csv.read(new byte[DemoCsvReader.MAX_BYTES+1])).getErrorCode());
        String content=HEADER+"A,42,T1,D1,R1,2026-10-19,item,1.00,note\n".repeat(10001);
        assertEquals(30106,assertThrows(BizException.class,()->csv.read(content.getBytes(StandardCharsets.UTF_8))).getErrorCode());
    }
}
