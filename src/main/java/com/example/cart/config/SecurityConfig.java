// Spring Security 設定（目前整段已停用）
// 用途：關閉 CSRF 並放行 /api/** 所有請求，其餘請求需登入
// 若要啟用：取消下方註解，並在 pom.xml 加入 spring-boot-starter-security 相依套件
/*
 * package com.example.cart.config;
 * 
 * import org.springframework.context.annotation.Bean;
 * import org.springframework.context.annotation.Configuration;
 * import
 * org.springframework.security.config.annotation.web.builders.HttpSecurity;
 * import org.springframework.security.web.SecurityFilterChain;
 * 
 * @Configuration
 * public class SecurityConfig {
 * 
 * @Bean
 * public SecurityFilterChain securityFilterChain(HttpSecurity http) throws
 * Exception {
 * http
 * .csrf(csrf -> csrf.disable())
 * .authorizeHttpRequests(auth -> auth
 * // 登入功能做好之前，先放行全部 API 方便測試
 * .requestMatchers("/api/**").permitAll()
 * .anyRequest().authenticated());
 * return http.build();
 * }
 * }
 */