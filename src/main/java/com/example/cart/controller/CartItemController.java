package com.example.cart.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cart.service.CartService;

// 購物車項目 API
// 規格的 cart_items 沒有數量欄位，所以只有刪除，沒有修改數量
@RestController
@RequestMapping("/api/cart-items")
public class CartItemController {
    private final CartService cartService;

    // 建構子注入：Spring 會自動把 CartService 的實例傳進來
    public CartItemController(CartService cartService) {
        this.cartService = cartService;
    }

    // 從購物車移除一個項目
    // DELETE /api/cart-items/{memberId}/{cartItemId}
    @DeleteMapping("/{memberId}/{cartItemId}")
    public ResponseEntity<String> deleteCartItem(@PathVariable Integer memberId,
            @PathVariable Integer cartItemId) throws Exception {
        cartService.removeCartItem(memberId, cartItemId);
        return new ResponseEntity<>("CartItem deleted successfully", HttpStatus.OK);
    }
}
