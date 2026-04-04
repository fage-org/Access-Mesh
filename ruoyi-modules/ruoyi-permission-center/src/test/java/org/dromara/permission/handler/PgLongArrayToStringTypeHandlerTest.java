package org.dromara.permission.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PgLongArrayToStringTypeHandlerTest {

    @Mock
    private PreparedStatement ps;
    @Mock
    private ResultSet rs;
    @Mock
    private Connection connection;
    @Mock
    private java.sql.Array sqlArray;

    private PgLongArrayToStringTypeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PgLongArrayToStringTypeHandler();
    }

    // ======================== setNonNullParameter ========================

    @Test
    void setNonNullParameter_normalString_createsArrayAndSets() throws Exception {
        when(ps.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("BIGINT"), any(Long[].class))).thenReturn(sqlArray);

        handler.setNonNullParameter(ps, 1, "1,2,3", null);

        verify(connection).createArrayOf(eq("BIGINT"), argThat(arg -> {
            Long[] arr = (Long[]) arg;
            return arr.length == 3 && arr[0] == 1L && arr[1] == 2L && arr[2] == 3L;
        }));
        verify(ps).setArray(1, sqlArray);
    }

    @Test
    void setNonNullParameter_emptyString_setsNull() throws Exception {
        handler.setNonNullParameter(ps, 1, "", null);

        verify(ps).setNull(1, Types.ARRAY);
        verify(ps, never()).setArray(anyInt(), any());
    }

    @Test
    void setNonNullParameter_invalidNumber_throwsSQLException() {
        assertThrows(SQLException.class, () ->
            handler.setNonNullParameter(ps, 1, "abc", null)
        );
    }

    // ======================== getNullableResult ========================

    @Test
    void getNullableResult_byColumnName_returnsCommaSeparated() throws Exception {
        when(rs.getArray("col")).thenReturn(sqlArray);
        when(sqlArray.getArray()).thenReturn(new Long[]{1L, 2L});

        String result = handler.getNullableResult(rs, "col");

        assertEquals("1,2", result);
    }

    @Test
    void getNullableResult_nullArray_returnsNull() throws Exception {
        when(rs.getArray("col")).thenReturn(null);

        String result = handler.getNullableResult(rs, "col");

        assertNull(result);
    }
}
