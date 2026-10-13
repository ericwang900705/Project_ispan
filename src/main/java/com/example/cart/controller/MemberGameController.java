package com.example.cart.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cart.model.MemberGame;
import com.example.cart.service.OrderService;

// 會員遊戲庫 API
@RestController
@RequestMapping("/api/member-games")
public class MemberGameController {
    private final OrderService orderService;

    // 建構子注入：Spring 會自動把 OrderService 的實例傳進來
    public MemberGameController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 查詢會員的遊戲庫
    // GET /api/member-games/{memberId}
    @GetMapping("/{memberId}")
    public ResponseEntity<List<MemberGame>> getMemberGames(@PathVariable Integer memberId) throws Exception {
        return new ResponseEntity<>(orderService.getMemberGames(memberId), HttpStatus.OK);
    }
}
