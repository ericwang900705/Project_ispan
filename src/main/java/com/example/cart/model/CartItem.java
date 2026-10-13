package com.example.cart.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 購物車資料表 cart_items
// 規格沒有獨立的購物車表：某會員的所有 cart_items 就是他的購物車
// 數位遊戲一份就夠，所以沒有數量欄位；同一款遊戲可以自己買一份、再送禮給別人
@Entity
@Table(name = "cart_items")
public class CartItem {
    // 購物車項目編號_PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer cartItemId;

    // 會員編號_FK：購物車的主人（就是發出請求的會員，回傳 JSON 時不用再帶）
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    @JsonIgnore
    private Member member;

    // 遊戲編號_FK
    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    // 是否為禮物
    private Boolean isGift;

    // 受贈會員編號_FK：不是禮物時為 null
    @ManyToOne
    @JoinColumn(name = "recipient_member_id")
    private Member recipientMember;

    // 加入時間
    private LocalDateTime createdAt;

    // 優惠券編號_FK：沒用優惠券時為 null
    @ManyToOne
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    // 無參數建構子：JPA 從資料庫讀取資料建立物件時需要使用
    public CartItem() {
    }

    // 這一項實際要付的價格：遊戲原價 × 優惠券折扣，四捨五入到整數元
    // 不是資料庫欄位，只是方便前端顯示；結帳時存進 order_items.unit_price
    public BigDecimal getFinalPrice() {
        BigDecimal price = BigDecimal.valueOf(game.getPrice());
        if (coupon != null && coupon.getDiscountValue() != null) {
            price = price.multiply(coupon.getDiscountValue());
        }
        return price.setScale(0, RoundingMode.HALF_UP);
    }

    public Integer getCartItemId() {
        return cartItemId;
    }

    public void setCartItemId(Integer cartItemId) {
        this.cartItemId = cartItemId;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Boolean getIsGift() {
        return isGift;
    }

    public void setIsGift(Boolean isGift) {
        this.isGift = isGift;
    }

    public Member getRecipientMember() {
        return recipientMember;
    }

    public void setRecipientMember(Member recipientMember) {
        this.recipientMember = recipientMember;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public void setCoupon(Coupon coupon) {
        this.coupon = coupon;
    }
}
