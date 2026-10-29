package gameplatform.support.extension;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 在同一 SQL Server 連線及明確交易內，初始化新增客服結構。 */
@Component
@Order(-80)
public class SupportExtensionInitializer implements ApplicationRunner {

  private final DataSource source;

  public SupportExtensionInitializer(DataSource source) {
    this.source = source;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    try (var c = source.getConnection()) {
      if (
        !"Microsoft SQL Server".equals(c.getMetaData().getDatabaseProductName())
      ) return;
      boolean auto = c.getAutoCommit();
      Exception failure = null;
      try {
        if (!auto) c.setAutoCommit(true);
        try (var s = c.createStatement()) {
          // 先補齊客服結構，再初始化分類；每個批次自行管理實體交易。
          for (String resource : new String[]{"db/support-extension.sql", "db/support-account-help.sql", "db/support-faq-catalog.sql"}) {
            s.execute(new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8));
          }
        }
      } catch (Exception e) {
        failure = e;
        throw e;
      } finally {
        // NOCOUNT 為連線層級設定，連線池不會自動還原。
        // 必須在歸還前關閉，否則商城會把未回傳的更新筆數誤判為找不到會員。
        try {
          try (var cleanup = c.createStatement()) {
            cleanup.executeUpdate("SET NOCOUNT OFF");
          }
        } catch (Exception cleanupError) {
          if (failure != null) failure.addSuppressed(cleanupError);
          else throw cleanupError;
        } finally {
          if (!auto) c.setAutoCommit(false);
        }
      }
    }
  }
}
