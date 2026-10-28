package gameplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 讓 /uploads/** 網址對應到專案根目錄下的 uploads 資料夾
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}
