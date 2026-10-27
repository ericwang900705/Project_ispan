package gameplatform.shop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import java.util.Locale;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 初始化必須在同一實體連線執行 SQL，並於取交易鎖前明確開始交易。 */
class CouponSchemaInitializerTests {
    DataSource source;
    Connection connection;
    Statement statement;
    CouponSchemaInitializer initializer;
    @BeforeEach void setup() throws Exception {
        source=mock(DataSource.class);connection=mock(Connection.class);statement=mock(Statement.class);
        var metadata=mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        initializer=new CouponSchemaInitializer(source);
    }
    @Test void startsExplicitTransactionBeforeTransactionOwnedApplicationLock() throws Exception {
        initializer.run(null);
        var sql=ArgumentCaptor.forClass(String.class);verify(statement).execute(sql.capture());
        String batch=sql.getValue().toUpperCase(Locale.ROOT);
        int begin = batch.indexOf("BEGIN TRANSACTION;");
        int acquire = batch.indexOf("EXEC @COUPONLOCK=");
        assertTrue(begin >= 0, "必須明確開始 SQL Server 交易");
        assertTrue(acquire > begin, "交易鎖必須在 BEGIN TRANSACTION 後取得");
        assertTrue(batch.contains("COMMIT TRANSACTION;"));
        assertTrue(batch.contains("ROLLBACK TRANSACTION;"));
        assertTrue(batch.contains("SP_GETAPPLOCK="));
        verify(source,times(1)).getConnection();verify(statement).close();verify(connection).close();
    }
    @Test void temporarilyUsesAutocommitForSelfContainedSqlTransaction() throws Exception {
        when(connection.getAutoCommit()).thenReturn(false);
        initializer.run(null);
        var order=inOrder(connection,statement);
        order.verify(connection).setAutoCommit(true);
        order.verify(statement).execute(anyString());
        order.verify(connection).setAutoCommit(false);
    }
    @Test void sqlFailureRemainsVisibleAndConnectionIsRestoredAndClosed() throws Exception {
        when(connection.getAutoCommit()).thenReturn(false);
        var error=new SQLException("sp_getapplock=-999","S0001",51120);
        when(statement.execute(anyString())).thenThrow(error);
        assertSame(error,assertThrows(SQLException.class,()->initializer.run(null)));
        verify(connection).setAutoCommit(false);verify(connection).close();verify(statement).close();
    }
    @Test void skipsNonSqlServerWithoutExecutingVendorSpecificSql() throws Exception {
        when(connection.getMetaData().getDatabaseProductName()).thenReturn("H2");
        initializer.run(null);
        verify(connection,never()).createStatement();verify(connection).close();
    }
}
