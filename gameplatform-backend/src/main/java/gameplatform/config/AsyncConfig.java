package gameplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync // 告訴 Spring 啟動背景非同步任務的支援
public class AsyncConfig {
    // 如果有高併發需求，可以在這裡進一步設定專屬的 ThreadPoolTaskExecutor (執行緒池)
}
