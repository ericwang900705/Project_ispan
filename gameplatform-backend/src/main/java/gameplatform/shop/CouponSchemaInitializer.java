package gameplatform.shop;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

/** 啟動後端時，於 SQL Server 交易內補齊領券與使用規則資料表。 */
@Component
@Order(-100)
public class CouponSchemaInitializer implements ApplicationRunner {
    private final DataSource dataSource;
    public CouponSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        try(var connection=dataSource.getConnection()) {
            if(!connection.getMetaData().getDatabaseProductName().equals("Microsoft SQL Server")) return;
            String sql = new ClassPathResource("db/coupon-schema.sql").getContentAsString(StandardCharsets.UTF_8);
            // 本批次使用明確 BEGIN TRANSACTION／COMMIT；不讓 JPA 與 JDBC 各自取得連線。
            boolean autoCommit = connection.getAutoCommit();
            try {
                if (!autoCommit) connection.setAutoCommit(true);
                try (var statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            } finally {
                if (!autoCommit) connection.setAutoCommit(false);
            }
        }
    }
}
