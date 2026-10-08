package gameplatform.support.integration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="aki.modules.enabled",havingValue="true")
@ComponentScan("gameplatform.support")
public class AkiModuleConfiguration implements WebMvcConfigurer {
    private final ObjectProvider<CurrentActorProvider> providers;
    public AkiModuleConfiguration(ObjectProvider<CurrentActorProvider> providers) { this.providers=providers; }
    @Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) { resolvers.add(new ModuleActorResolver(providers)); }
}
