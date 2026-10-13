package com.example.cart.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 優惠券資料表 coupons（暫代版本，正式版本由隊友負責；欄位照規格）
@Entity
@Table(name = "coupons")
public class Coupon {
    public static final String STATUS_ACTIVE = "ACTIVE";

    // 優惠券編號
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer couponId;

    // 優惠券名稱：NVARCHAR(100)
    @Nationalized
    @Column(length = 100)
    private String couponName;

    // 折扣數值：DECIMAL(3,2)，當作「乘數」使用，例如 0.80 = 打八折
    @Column(precision = 3, scale = 2)
    private BigDecimal discountValue;

    // 優惠券狀態：VARCHAR(20)
    @Column(length = 20)
    private String status;

    // 開始日：DATE
    private LocalDate startAt;

    // 到期時間：DATETIME2
    private LocalDateTime expiresAt;

    // 無參數建構子：JPA 從資料庫讀取資料建立物件時需要使用
    public Coupon() {
    }

    // 現在能不能用：狀態必須是 ACTIVE，而且今天在使用期間內（沒設定的日期視為不限制）
    public boolean isUsableNow() {
        if (!STATUS_ACTIVE.equals(status)) {
            return false;
        }
        if (startAt != null && LocalDate.now().isBefore(startAt)) {
            return false;
        }
        return expiresAt == null || LocalDateTime.now().isBefore(expiresAt);
    }

    public Integer getCouponId() {
        return couponId;
    }

    public void setCouponId(Integer couponId) {
        this.couponId = couponId;
    }

    public String getCouponName() {
        return couponName;
    }

    public void setCouponName(String couponName) {
        this.couponName = couponName;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDate startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
