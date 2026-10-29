package gameplatform.support.extension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

/** 分類資料必須在客服結構之後，以同一連線執行；取鎖及失敗不可靜默略過。 */
class SupportExtensionInitializerTests {
    DataSource source;
    Connection connection;
    Statement statement;
    SupportExtensionInitializer initializer;

    @BeforeEach
    void setup() throws Exception {
        source = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        var metadata = mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        initializer = new SupportExtensionInitializer(source);
    }

    @Test
    void initializesStructureBeforeCategoriesOnOneConnection() throws Exception {
        initializer.run(null);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(statement, times(3)).execute(sql.capture());
        assertTrue(sql.getAllValues().getFirst().contains("CREATE TABLE dbo.support_order_links"));
        String categories = sql.getAllValues().get(1);
        String faqs = sql.getAllValues().get(2);
        assertTrue(faqs.contains("HERO_FAQ_CATALOG_20261028"));
        assertTrue(faqs.contains("sp_addextendedproperty"));
        assertTrue(faqs.contains("怎麼更新已下載的 ZIP 遊戲檔案"));
        assertTrue(categories.indexOf("BEGIN TRANSACTION;") < categories.indexOf("sys.sp_getapplock"));
        assertTrue(categories.contains("sp_getapplock="));
        assertTrue(categories.contains("ROLLBACK TRANSACTION;"));
        verify(statement).executeUpdate("SET NOCOUNT OFF");
        verify(source, times(1)).getConnection();
        verify(connection).close();
    }

    @Test
    void restoresConnectionModeAfterSecondBatchFailure() throws Exception {
        when(connection.getAutoCommit()).thenReturn(false);
        var failure = new SQLException("categories failed");
        when(statement.execute(anyString())).thenReturn(false).thenThrow(failure);
        assertSame(failure, assertThrows(SQLException.class, () -> initializer.run(null)));
        verify(connection).setAutoCommit(true);
        verify(statement).executeUpdate("SET NOCOUNT OFF");
        verify(connection).setAutoCommit(false);
        verify(connection).close();
    }

    @Test
    void firstBatchFailureDoesNotStartCategoryWrites() throws Exception {
        when(statement.execute(anyString())).thenThrow(new SQLException("structure failed"));
        assertThrows(SQLException.class, () -> initializer.run(null));
        verify(statement, times(1)).execute(anyString());
        verify(statement).executeUpdate("SET NOCOUNT OFF");
        verify(connection).close();
    }

    @Test
    void cleanupFailureDoesNotHideOriginalSqlFailure() throws Exception {
        var original = new SQLException("initialization failed");
        var cleanup = new SQLException("NOCOUNT reset failed");
        when(statement.execute(anyString())).thenThrow(original);
        when(statement.executeUpdate("SET NOCOUNT OFF")).thenThrow(cleanup);
        assertSame(original, assertThrows(SQLException.class, () -> initializer.run(null)));
        assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
        verify(connection).close();
    }

    @Test
    void cleanupFailureIsVisibleWhenInitializationSucceeded() throws Exception {
        var cleanup = new SQLException("NOCOUNT reset failed");
        when(statement.executeUpdate("SET NOCOUNT OFF")).thenThrow(cleanup);
        assertSame(cleanup, assertThrows(SQLException.class, () -> initializer.run(null)));
        verify(connection).close();
    }

    @Test
    void skipsNonSqlServer() throws Exception {
        when(connection.getMetaData().getDatabaseProductName()).thenReturn("PostgreSQL");
        initializer.run(null);
        verify(connection, never()).createStatement();
        verify(connection).close();
    }
}
