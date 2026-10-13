package com.example.cart.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cart.model.Coupon;
import com.example.cart.repository.CouponRepository;

// 優惠券 API（暫代版本，正式版本由隊友負責）
@RestController
@RequestMapping("/api/coupons")
public class CouponController {
    private final CouponRepository couponRepository;

    // 建構子注入：Spring 會自動把 CouponRepository 的實例傳進來
    public CouponController(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    // 現在可以用的優惠券（購物車選券用）
    // GET /api/coupons/usable
    @GetMapping("/usable")
    public ResponseEntity<List<Coupon>> getUsableCoupons() {
        List<Coupon> coupons = couponRepository.findByStatusOrderByExpiresAtAsc(Coupon.STATUS_ACTIVE).stream()
                .filter(Coupon::isUsableNow)
                .toList();
        return new ResponseEntity<>(coupons, HttpStatus.OK);
    }
}
