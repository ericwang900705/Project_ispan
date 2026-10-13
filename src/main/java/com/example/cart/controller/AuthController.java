package com.example.cart.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController：註冊 / 登入相關的 REST API 控制器
// @RequestMapping：此類別所有 API 的共同路徑前綴為 /api/auth
// （註冊功能尚未實作，目前由 DataInitializer 在啟動時建立測試使用者與購物車）
@RestController
@RequestMapping("/api/auth")
public class AuthController {
}
