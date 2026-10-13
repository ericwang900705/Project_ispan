package com.example.cart.response;

import java.math.BigDecimal;
import java.util.List;

import com.example.cart.model.CartItem;
import com.example.cart.model.Coupon;
import com.example.cart.service.CartService;

// 查詢購物車時回傳給前端的 JSON：{ items: [...], coupon, totalPrice: 1234 }
// 規格沒有購物車表可以存總金額，所以每次查詢時即時算出來
public class CartResponse {
    private final List<CartItem> items;   // 購物車項目
    private final Coupon coupon;          // 整個購物車套用的優惠券（沒有就是 null）
    private final BigDecimal totalPrice;  // 所有項目 finalPrice 的加總

    public CartResponse(List<CartItem> items) {
        this.items = items;
        this.coupon = CartService.getCartCoupon(items);
        this.totalPrice = items.stream()
                .map(CartItem::getFinalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<CartItem> getItems() {
        return items;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }
}
