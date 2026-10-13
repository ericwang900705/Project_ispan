package com.example.cart.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cart.model.Order;

// 訂單的資料存取層
public interface OrderRepository extends JpaRepository<Order, Integer> {
    // 某會員的所有訂單，新的在前面
    List<Order> findByMember_MemberIdOrderByCreatedAtDesc(Integer memberId);

    // 某個狀態的所有訂單（排程找逾期的待付款訂單用）
    List<Order> findByOrderStatus(String orderStatus);
}
