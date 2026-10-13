package com.example.cart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cart.model.Member;

// 會員的資料存取層（暫代版本，正式版本由隊友負責）
public interface MemberRepository extends JpaRepository<Member, Integer> {
    // 依帳號查會員（送禮時用帳號找受贈者）
    Optional<Member> findByUsername(String username);
}
