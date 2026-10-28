package gameplatform.shop;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 客服及優惠券結構完成後，補齊退款表與付款環境紀錄。 */
@Component
@Order(-60)
public class RefundSchemaInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    public RefundSchemaInitializer(DataSource dataSource) {
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
                            new ClassPathResource("db/refund-schema.sql").getContentAsString(StandardCharsets.UTF_8));
                }
            } finally {
                if (!auto)
                    connection.setAutoCommit(false);
            }
        }
    }
}
