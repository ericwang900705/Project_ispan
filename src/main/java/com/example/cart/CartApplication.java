package com.example.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @SpringBootApplication：Spring Boot 主程式標記，包含三個功能：
// 1. @Configuration：此類別可定義 Bean
// 2. @EnableAutoConfiguration：依照 pom.xml 的相依套件自動設定（如資料庫、Web 伺服器）
// 3. @ComponentScan：自動掃描 com.example.cart 底下所有 @Controller、@Service、@Repository 等元件
// @EnableScheduling：啟用 @Scheduled 排程（逾期的待付款訂單自動改成 EXPIRED）
@SpringBootApplication
@EnableScheduling
public class CartApplication {

	// 程式進入點：啟動 Spring Boot 應用程式（內嵌 Tomcat，預設 port 由 application.properties 設定）
	public static void main(String[] args) {
		SpringApplication.run(CartApplication.class, args);
	}

}
