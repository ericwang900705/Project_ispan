package gameplatform.support.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

/** 加入客服參數轉接與心跳排程，不建立第二套 SecurityFilterChain。 */
@Configuration
@EnableScheduling
public class SupportWebConfig implements WebMvcConfigurer {
    private final SupportActorResolver actors;

    public SupportWebConfig(SupportActorResolver actors) {
        this.actors = actors;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(actors);
    }
}
