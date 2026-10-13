package com.example.cart.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cart.model.CartItem;

// 購物車項目的資料存取層
// 方法名稱照 Spring Data 規則命名，就會自動產生查詢；Member_MemberId 代表 member.memberId
public interface CartItemRepository extends JpaRepository<CartItem, Integer> {
    // 某會員的整個購物車，依加入時間排序
    List<CartItem> findByMember_MemberIdOrderByCreatedAtAsc(Integer memberId);

    // 購物車裡是否已經有「自己要買的」這款遊戲
    boolean existsByMember_MemberIdAndGame_GameIdAndIsGiftFalse(Integer memberId, Integer gameId);

    // 購物車裡是否已經有「要送給某人的」這款遊戲
    boolean existsByMember_MemberIdAndGame_GameIdAndRecipientMember_MemberId(
            Integer memberId, Integer gameId, Integer recipientMemberId);
}
