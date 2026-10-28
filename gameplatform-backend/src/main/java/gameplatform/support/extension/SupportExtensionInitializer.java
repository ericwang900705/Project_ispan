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
      try {
        if (!auto) c.setAutoCommit(true);
        try (var s = c.createStatement()) {
          // 先補齊客服結構，再初始化分類；每個批次自行管理實體交易。
          for (String resource : new String[]{"db/support-extension.sql", "db/support-account-help.sql"}) {
            s.execute(new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8));
          }
        }
      } finally {
        if (!auto) c.setAutoCommit(false);
      }
    }
  }
}
