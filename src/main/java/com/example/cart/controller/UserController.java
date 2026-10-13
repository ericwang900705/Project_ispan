package com.example.cart.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController：使用者相關的 REST API 控制器
// @RequestMapping：此類別所有 API 的共同路徑前綴為 /api/users
// （批次新增商品的 API 已移到 ProductController 的 POST /api/products/batch）
@RestController
@RequestMapping("/api/users")
public class UserController {
}
