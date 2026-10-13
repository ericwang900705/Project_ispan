package com.example.cart.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.Nationalized;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

// 訂單資料表 orders：結帳時由購物車產生
@Entity
@Table(name = "orders")
public class Order {
    // 訂單編號_PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer orderId;

    // 會員編號_FK：下單的會員
    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    @JsonIgnore
    private Member member;

    // 訂單金額：所有訂單項目 unit_price 的加總
    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // 訂單狀態：NVARCHAR，PENDING（待付款）、PAID（已付款）、FAILED（付款失敗）、
    // CANCELLED（會員取消）、EXPIRED（逾期未付款）
    @Nationalized
    @Column(length = 20)
    private String orderStatus;

    // 下單日期
    private LocalDateTime createdAt;

    // 獲得點數
    private Integer pointsEarned;

    // 綠界交易編號（綠界回傳的 TradeNo）：付款成功才有，對帳、退款時要用
    @Column(length = 20)
    private String ecpayTradeNo;

    // 付款方式（綠界回傳的 PaymentType），例如 Credit_CreditCard、ATM_TAISHIN、CVS_CVS
    @Column(length = 30)
    private String paymentType;

    // 付款時間（綠界回傳的 PaymentDate）：付款成功才有
    private LocalDateTime paidAt;

    // 訂單項目：cascade ALL 讓儲存訂單時一起儲存所有項目
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    // ATM／超商代碼的繳費資訊：不是資料表欄位（@Transient），查詢訂單時才從記憶體帶上，給「我的訂單」頁顯示
    @Transient
    private PaymentInfo paymentInfo;

    // 無參數建構子：JPA 從資料庫讀取資料建立物件時需要使用
    public Order() {
    }

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getPointsEarned() {
        return pointsEarned;
    }

    public void setPointsEarned(Integer pointsEarned) {
        this.pointsEarned = pointsEarned;
    }

    public String getEcpayTradeNo() {
        return ecpayTradeNo;
    }

    public void setEcpayTradeNo(String ecpayTradeNo) {
        this.ecpayTradeNo = ecpayTradeNo;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public PaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    public void setPaymentInfo(PaymentInfo paymentInfo) {
        this.paymentInfo = paymentInfo;
    }
}
