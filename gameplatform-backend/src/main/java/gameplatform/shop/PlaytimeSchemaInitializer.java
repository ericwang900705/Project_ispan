package gameplatform.shop;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 啟動時補齊遊玩時數同步去重表，保留既有時數。 */
@Component
@Order(-50)
public class PlaytimeSchemaInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public PlaytimeSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (var connection = dataSource.getConnection()) {
            if (!"Microsoft SQL Server".equals(connection.getMetaData().getDatabaseProductName()))
                return;
            boolean auto = connection.getAutoCommit();
            try {
                if (!auto)
                    connection.setAutoCommit(true);
                try (var statement = connection.createStatement()) {
                    statement.execute(
                            new ClassPathResource("db/playtime-sync-schema.sql").getContentAsString(StandardCharsets.UTF_8));
                }
            } finally {
                if (!auto)
                    connection.setAutoCommit(false);
            }
        }
    }
}
