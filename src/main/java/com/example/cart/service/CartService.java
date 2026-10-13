package com.example.cart.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cart.model.CartItem;
import com.example.cart.model.Coupon;
import com.example.cart.model.Game;
import com.example.cart.model.Member;
import com.example.cart.repository.CartItemRepository;
import com.example.cart.repository.CouponRepository;
import com.example.cart.repository.MemberGameRepository;
import com.example.cart.request.AddItemRequest;
import com.example.cart.response.CartResponse;

// 購物車的業務邏輯層：查詢、加入、移除購物車項目
@Service
public class CartService {
    private final CartItemRepository cartItemRepository; // 購物車項目
    private final MemberGameRepository memberGameRepository; // 用來檢查是否已擁有遊戲
    private final CouponRepository couponRepository; // 用來查詢優惠券
    private final MemberService memberService;
    private final GameService gameService;

    // 建構子注入：Spring 會自動把需要的物件傳進來
    public CartService(CartItemRepository cartItemRepository, MemberGameRepository memberGameRepository,
            CouponRepository couponRepository, MemberService memberService, GameService gameService) {
        this.cartItemRepository = cartItemRepository;
        this.memberGameRepository = memberGameRepository;
        this.couponRepository = couponRepository;
        this.memberService = memberService;
        this.gameService = gameService;
    }

    // 取得會員的購物車（項目清單 + 總金額）
    public CartResponse getCart(Integer memberId) throws Exception {
        memberService.findMemberById(memberId); // 會員不存在就直接丟例外
        return new CartResponse(cartItemRepository.findByMember_MemberIdOrderByCreatedAtAsc(memberId));
    }

    // 加入購物車
    // 自己買：自己不能已擁有、購物車裡也不能已經有
    // 送禮：受贈者必須存在且不是自己、受贈者不能已擁有、購物車裡不能已經有送給同一人的
    public CartItem addToCart(Integer memberId, AddItemRequest req) throws Exception {
        if (req.getGameId() == null) {
            throw new Exception("請指定要加入的遊戲");
        }
        Member member = memberService.findMemberById(memberId);
        Game game = gameService.getGameById(req.getGameId());
        boolean isGift = Boolean.TRUE.equals(req.getIsGift());

        CartItem item = new CartItem();
        item.setMember(member);
        item.setGame(game);
        item.setIsGift(isGift);
        item.setCreatedAt(LocalDateTime.now());

        if (isGift) {
            Integer recipientId = req.getRecipientMemberId();
            if (recipientId == null) {
                throw new Exception("送禮請指定受贈會員");
            }
            if (recipientId.equals(memberId)) {
                throw new Exception("不能送禮給自己");
            }
            Member recipient = memberService.findMemberById(recipientId);
            if (memberGameRepository.existsByMember_MemberIdAndGame_GameIdAndIsActiveTrue(recipientId,
                    game.getGameId())) {
                throw new Exception(recipient.getUsername() + " 已經擁有這款遊戲");
            }
            if (cartItemRepository.existsByMember_MemberIdAndGame_GameIdAndRecipientMember_MemberId(
                    memberId, game.getGameId(), recipientId)) {
                throw new Exception("購物車裡已經有要送給 " + recipient.getUsername() + " 的這款遊戲");
            }
            item.setRecipientMember(recipient);
        } else {
            if (memberGameRepository.existsByMember_MemberIdAndGame_GameIdAndIsActiveTrue(memberId, game.getGameId())) {
                throw new Exception("你已經擁有這款遊戲");
            }
            if (cartItemRepository.existsByMember_MemberIdAndGame_GameIdAndIsGiftFalse(memberId, game.getGameId())) {
                throw new Exception("這款遊戲已經在購物車裡");
            }
        }

        // 優惠券是套在整個購物車上的：沒指定就沿用購物車目前的券
        if (req.getCouponId() != null) {
            item.setCoupon(findUsableCoupon(req.getCouponId()));
        } else {
            item.setCoupon(getCartCoupon(cartItemRepository.findByMember_MemberIdOrderByCreatedAtAsc(memberId)));
        }

        return cartItemRepository.save(item);
    }

    // 整個購物車套用優惠券：每個項目都寫上同一張券（規格只有 cart_items.coupon_id）
    // couponId 傳 null 就是取消優惠券
    @Transactional
    public CartResponse applyCoupon(Integer memberId, Integer couponId) throws Exception {
        memberService.findMemberById(memberId);
        Coupon coupon = couponId == null ? null : findUsableCoupon(couponId);
        List<CartItem> items = cartItemRepository.findByMember_MemberIdOrderByCreatedAtAsc(memberId);
        if (items.isEmpty()) {
            throw new Exception("購物車是空的");
        }
        items.forEach(item -> item.setCoupon(coupon));
        cartItemRepository.saveAll(items);
        return new CartResponse(items);
    }

    // 購物車目前套用的優惠券（每個項目都一樣，取第一個；沒有就是 null）
    public static Coupon getCartCoupon(List<CartItem> items) {
        return items.isEmpty() ? null : items.get(0).getCoupon();
    }

    // 找優惠券，並確認現在能用
    private Coupon findUsableCoupon(Integer couponId) throws Exception {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new Exception("找不到優惠券，couponId = " + couponId));
        if (!coupon.isUsableNow()) {
            throw new Exception("「" + coupon.getCouponName() + "」目前不能使用");
        }
        return coupon;
    }

    // 從購物車移除一個項目；只能移除自己購物車裡的
    public void removeCartItem(Integer memberId, Integer cartItemId) throws Exception {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new Exception("找不到購物車項目，cartItemId = " + cartItemId));
        if (!item.getMember().getMemberId().equals(memberId)) {
            throw new Exception("不能移除別人購物車裡的項目");
        }
        cartItemRepository.delete(item);
    }

    // 取得會員購物車的所有項目（結帳用）
    public List<CartItem> getCartItems(Integer memberId) {
        return cartItemRepository.findByMember_MemberIdOrderByCreatedAtAsc(memberId);
    }

    // 清空購物車（結帳完成後呼叫）
    public void clearCart(List<CartItem> items) {
        cartItemRepository.deleteAll(items);
    }

    public CartResponse clearAll(Integer memberId) throws Exception {
        memberService.findMemberById(memberId); // 會員不存在
        clearCart(getCartItems(memberId));
        return new CartResponse(List.of());
    }
}
