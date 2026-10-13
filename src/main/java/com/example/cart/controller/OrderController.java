package com.example.cart.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cart.model.Order;
import com.example.cart.service.OrderService;

// 訂單 API：結帳、查詢訂單、取消訂單
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    // 建構子注入：Spring 會自動把 OrderService 的實例傳進來
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 結帳：把購物車全部變成一張訂單，遊戲寫進遊戲庫，並清空購物車
    // POST /api/orders/{memberId}/checkout
    // 回傳新建立的訂單（含訂單項目）
    @PostMapping("/{memberId}/checkout")
    public ResponseEntity<Order> checkout(@PathVariable Integer memberId) throws Exception {
        return new ResponseEntity<>(orderService.checkout(memberId), HttpStatus.CREATED);
    }

    // 查詢會員的所有訂單
    // GET /api/orders/{memberId}
    @GetMapping("/{memberId}")
    public ResponseEntity<List<Order>> getOrders(@PathVariable Integer memberId) throws Exception {
        return new ResponseEntity<>(orderService.getOrders(memberId), HttpStatus.OK);
    }

    // 取消待付款訂單，訂單裡的遊戲會放回購物車
    // POST /api/orders/{memberId}/{orderId}/cancel
    // 回傳取消後的訂單
    @PostMapping("/{memberId}/{orderId}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable Integer memberId, @PathVariable Integer orderId)
            throws Exception {
        return new ResponseEntity<>(orderService.cancelOrder(memberId, orderId), HttpStatus.OK);
    }
}
