package com.example.cart.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 訂單項目資料表 order_items：結帳時每個購物車項目變成一筆
@Entity
@Table(name = "order_items")
public class OrderItem {
    // 訂單項目編號
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer orderItemId;

    // 訂單編號_FK（回傳 JSON 時忽略，避免訂單 ↔ 項目無限循環）
    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    // 遊戲編號_FK
    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    // 數量：數位遊戲固定為 1
    private Integer quantity;

    // 單價：結帳當下套用優惠券後的價格，之後遊戲改價也不影響舊訂單
    @Column(precision = 10, scale = 2)
    private BigDecimal unitPrice;

    // 是否為禮物
    private Boolean isGift;

    // 受贈會員編號_FK
    @ManyToOne
    @JoinColumn(name = "recipient_member_id")
    private Member recipientMember;

    // 優惠券編號_FK
    @ManyToOne
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    // 無參數建構子：JPA 從資料庫讀取資料建立物件時需要使用
    public OrderItem() {
    }

    public Integer getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Integer orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
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

    public Coupon getCoupon() {
        return coupon;
    }

    public void setCoupon(Coupon coupon) {
        this.coupon = coupon;
    }
}
