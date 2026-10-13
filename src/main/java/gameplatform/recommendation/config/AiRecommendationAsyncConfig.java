
package gameplatform.recommendation.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AiRecommendationAsyncConfig {

    /**
     * AI 推薦專用背景執行緒池。
     */
    @Bean(name = "aiRecommendationExecutor")
    public Executor aiRecommendationExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 平常維持 2 條背景執行緒
        executor.setCorePoolSize(2);

        // 最多同時執行 4 個任務
        executor.setMaxPoolSize(4);

        // 最多等待 50 個任務
        executor.setQueueCapacity(50);

        executor.setThreadNamePrefix(
                "ai-recommendation-");

        executor.initialize();

        return executor;
    }
}
