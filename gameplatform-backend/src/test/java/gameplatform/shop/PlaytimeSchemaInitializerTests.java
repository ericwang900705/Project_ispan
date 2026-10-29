package gameplatform.shop;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
class PlaytimeSchemaInitializerTests {
 @Test void schemaLocksInsideTransaction()throws Exception{var ds=mock(DataSource.class);var c=mock(Connection.class);var s=mock(Statement.class);var meta=mock(DatabaseMetaData.class);when(ds.getConnection()).thenReturn(c);when(c.getMetaData()).thenReturn(meta);when(meta.getDatabaseProductName()).thenReturn("Microsoft SQL Server");when(c.getAutoCommit()).thenReturn(true);when(c.createStatement()).thenReturn(s);new PlaytimeSchemaInitializer(ds).run(null);var sql=ArgumentCaptor.forClass(String.class);verify(s).execute(sql.capture());assertTrue(sql.getValue().indexOf("BEGIN TRANSACTION;")<sql.getValue().indexOf("sys.sp_getapplock"));assertTrue(sql.getValue().contains("PRIMARY KEY(member_id,game_id,batch_id)"));assertTrue(sql.getValue().contains("ROLLBACK TRANSACTION"));verify(c).close();}
 @Test void skipsOtherDatabase()throws Exception{var ds=mock(DataSource.class);var c=mock(Connection.class);var meta=mock(DatabaseMetaData.class);when(ds.getConnection()).thenReturn(c);when(c.getMetaData()).thenReturn(meta);when(meta.getDatabaseProductName()).thenReturn("Other");new PlaytimeSchemaInitializer(ds).run(null);verify(c,never()).createStatement();verify(c).close();}
}
