package com.example.cart.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cart.model.CartItem;
import com.example.cart.request.AddItemRequest;
import com.example.cart.response.CartResponse;
import com.example.cart.service.CartService;

// 購物車 API
// 登入功能做好之前，先用網址上的 memberId 代表目前的會員
@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    // 建構子注入：Spring 會自動把 CartService 的實例傳進來
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // 查詢購物車
    // GET /api/cart/{memberId}
    // 回傳 { items: [...], totalPrice }
    @GetMapping("/{memberId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable Integer memberId) throws Exception {
        return new ResponseEntity<>(cartService.getCart(memberId), HttpStatus.OK);
    }

    // 加入購物車
    // POST /api/cart/{memberId}/add
    // Body：{ gameId, isGift?, recipientMemberId?, couponId? }
    @PostMapping("/{memberId}/add")
    public ResponseEntity<CartItem> addItemToCart(@PathVariable Integer memberId, @RequestBody AddItemRequest req)
            throws Exception {
        return new ResponseEntity<>(cartService.addToCart(memberId, req), HttpStatus.CREATED);
    }

    // 套用 / 取消優惠券的 Body：couponId 傳 null 就是取消
    public record CouponRequest(Integer couponId) {
    }

    // 整個購物車套用優惠券
    // PUT /api/cart/{memberId}/coupon
    // Body：{ couponId }，回傳套用後的購物車
    @PutMapping("/{memberId}/coupon")
    public ResponseEntity<CartResponse> applyCoupon(@PathVariable Integer memberId, @RequestBody CouponRequest req)
            throws Exception {
        return new ResponseEntity<>(cartService.applyCoupon(memberId, req.couponId()), HttpStatus.OK);
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<CartResponse> clearCart(@PathVariable Integer memberId) throws Exception {
        return new ResponseEntity<>(cartService.clearAll(memberId), HttpStatus.OK);
    }
}
