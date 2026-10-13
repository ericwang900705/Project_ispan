package com.example.cart.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cart.model.Coupon;

// 優惠券的資料存取層（暫代版本，正式版本由隊友負責）
public interface CouponRepository extends JpaRepository<Coupon, Integer> {
    // 指定狀態的優惠券，到期時間早的在前面
    List<Coupon> findByStatusOrderByExpiresAtAsc(String status);
}
