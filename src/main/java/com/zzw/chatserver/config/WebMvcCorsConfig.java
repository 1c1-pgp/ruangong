package com.zzw.chatserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Servlet MVC 跨域。生产环境通过 {@code app.cors.allowed-origin-patterns}（逗号分隔）配置允许的来源；
 * 若前端与 API 部署在同一域名（推荐 Nginx 同域反代），浏览器不会触发跨域，该项仅作备用。
 */
@Configuration
public class WebMvcCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;

    public WebMvcCorsConfig(
            @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String raw) {
        this.allowedOriginPatterns = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
