package com.example.cart.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cart.model.CartItem;
import com.example.cart.model.Member;
import com.example.cart.model.MemberGame;
import com.example.cart.model.Order;
import com.example.cart.model.OrderItem;
import com.example.cart.model.PaymentInfo;
import com.example.cart.request.AddItemRequest;
import com.example.cart.repository.MemberGameRepository;
import com.example.cart.repository.OrderRepository;

// 訂單的業務邏輯層：結帳、綠界付款的訂單狀態、查詢訂單
@Service
public class OrderService {
    // 每消費多少元得 1 點（暫定規則，之後依點數功能的規格調整）
    private static final int AMOUNT_PER_POINT = 100;
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // ATM／超商代碼的繳費資訊（order_id → 繳費資訊）：orders 沒有存這些的欄位，放在記憶體，伺服器重開後會消失
    private final Map<Integer, PaymentInfo> paymentInfos = new ConcurrentHashMap<>();

    private final OrderRepository orderRepository;
    private final MemberGameRepository memberGameRepository;
    private final CartService cartService;
    private final MemberService memberService;

    // 建構子注入：Spring 會自動把需要的物件傳進來
    public OrderService(OrderRepository orderRepository, MemberGameRepository memberGameRepository,
            CartService cartService, MemberService memberService) {
        this.orderRepository = orderRepository;
        this.memberGameRepository = memberGameRepository;
        this.cartService = cartService;
        this.memberService = memberService;
    }

    // 直接結帳（不經過綠界，測試用）：購物車 → orders + order_items → member_games，最後清空購物車
    // @Transactional：任何一步失敗就全部復原，不會出現「扣了訂單卻沒拿到遊戲」的情況
    // rollbackFor = Exception.class：預設只有 RuntimeException 會復原，這裡丟的是 Exception 所以要指定
    @Transactional(rollbackFor = Exception.class)
    public Order checkout(Integer memberId) throws Exception {
        Order order = buildOrderFromCart(memberId);
        order.setOrderStatus("PAID");
        // cascade ALL：儲存訂單時一起儲存所有訂單項目
        Order savedOrder = orderRepository.save(order);
        grantGames(savedOrder, savedOrder.getCreatedAt());
        cartService.clearCart(cartService.getCartItems(memberId));
        return savedOrder;
    }

    // 綠界付款第一步：用購物車建立一張「待付款」訂單
    // 這時候還沒付錢，所以遊戲不會進遊戲庫、購物車也先不清空，等付款成功（markPaid）才做
    @Transactional(rollbackFor = Exception.class)
    public Order createPendingOrder(Integer memberId) throws Exception {
        Order order = buildOrderFromCart(memberId);
        order.setOrderStatus("PENDING");
        return orderRepository.save(order);
    }

    // 綠界通知付款成功：
    // - orders：order_status → PAID，並記下綠界交易編號（ecpay_trade_no）、付款方式（payment_type）、付款時間（paid_at）
    // - member_games 每款遊戲一筆，obtained_at = 付款時間
    // - cart_items 移除這張訂單買的項目
    // paidAmount 是綠界實際收到的金額（TradeAmt），跟 orders.total_amount 對不上就不算付款成功
    // 綠界可能對同一筆訂單通知兩次（OrderResultURL 和 ReturnURL），已付款就不再重複處理
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(Integer orderId, BigDecimal paidAmount, LocalDateTime paidAt, String ecpayTradeNo,
            String paymentType) throws Exception {
        Order order = findOrder(orderId);
        if ("PAID".equals(order.getOrderStatus())) {
            return;
        }
        // 已取消或逾期的訂單還是可能收到錢：ATM 虛擬帳號／超商代碼在綠界那邊沒有跟著作廢，
        // 錢已經收了就照樣算付款成功、給遊戲（取消／逾期都保留了 order_items，就是為了這種情況）
        if (!"PENDING".equals(order.getOrderStatus())) {
            log.warn("訂單 {} 狀態是 {}，但收到付款成功通知，照樣改成已付款", orderId, order.getOrderStatus());
        }
        if (paidAmount == null || paidAmount.compareTo(order.getTotalAmount()) != 0) {
            throw new Exception("付款金額不符，訂單 " + orderId + " 應付 " + order.getTotalAmount() + "，實付 " + paidAmount);
        }
        order.setOrderStatus("PAID");
        order.setEcpayTradeNo(ecpayTradeNo);
        order.setPaymentType(paymentType);
        order.setPaidAt(paidAt);
        grantGames(order, paidAt);
        clearCartForOrder(orderId);
        paymentInfos.remove(orderId);
    }

    // ATM／超商代碼取號成功：記下付款方式（逾期排程要靠它判斷繳費期限）和繳費資訊，
    // 並從購物車拿掉這張訂單的遊戲，避免使用者又重複結帳；訂單維持待付款
    @Transactional(rollbackFor = Exception.class)
    public void markAwaitingPayment(Integer orderId, PaymentInfo info) throws Exception {
        Order order = findOrder(orderId);
        if (!"PENDING".equals(order.getOrderStatus())) {
            return;
        }
        order.setPaymentType(info.paymentType());
        paymentInfos.put(orderId, info);
        clearCartForOrder(orderId);
    }

    // 會員取消待付款訂單：order_status → CANCELLED，並把遊戲放回購物車（方便換別的付款方式重新結帳）
    // order_items 保留不刪：ATM／超商代碼在綠界那邊不會跟著作廢，萬一使用者還是去繳費了，markPaid 才知道要給哪些遊戲
    @Transactional(rollbackFor = Exception.class)
    public Order cancelOrder(Integer memberId, Integer orderId) throws Exception {
        Order order = getMemberOrder(memberId, orderId);
        if (!"PENDING".equals(order.getOrderStatus())) {
            throw new Exception("只有待付款的訂單可以取消");
        }
        order.setOrderStatus("CANCELLED");
        paymentInfos.remove(orderId);
        for (OrderItem orderItem : order.getOrderItems()) {
            AddItemRequest req = new AddItemRequest();
            req.setGameId(orderItem.getGame().getGameId());
            req.setIsGift(orderItem.getIsGift());
            req.setRecipientMemberId(memberIdOf(orderItem.getRecipientMember()));
            try {
                cartService.addToCart(memberId, req);
            } catch (Exception e) {
                // 已經在購物車（信用卡付款沒完成時購物車本來就還在）或已經擁有，就不用放回去
            }
        }
        return order;
    }

    // 排程：每 10 分鐘把超過繳費期限的待付款訂單改成 EXPIRED（order_items 保留，原因同 cancelOrder）
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void expireOverdueOrders() {
        LocalDateTime now = LocalDateTime.now();
        for (Order order : orderRepository.findByOrderStatus("PENDING")) {
            if (now.isAfter(paymentDeadline(order))) {
                order.setOrderStatus("EXPIRED");
                paymentInfos.remove(order.getOrderId());
                log.info("訂單 {} 逾期未付款，改成 EXPIRED", order.getOrderId());
            }
        }
    }

    // 待付款訂單的繳費期限（跟 PaymentController 送給綠界的期限一致，多抓一點緩衝）：
    // - 還沒取號（信用卡付款頁沒付完就離開）：1 小時
    // - ATM：ExpireDate = 3 天，到第 3 天結束為止
    // - 超商代碼：StoreExpireDate = 7 天
    private LocalDateTime paymentDeadline(Order order) {
        String paymentType = order.getPaymentType();
        LocalDateTime createdAt = order.getCreatedAt();
        if (paymentType == null) {
            return createdAt.plusHours(1);
        }
        if (paymentType.startsWith("ATM")) {
            return createdAt.toLocalDate().plusDays(4).atStartOfDay();
        }
        return createdAt.plusDays(7).plusHours(1);
    }

    // 綠界通知付款失敗：orders.order_status → FAILED，並刪掉這張訂單的明細（order_items）
    // 訂單明細只留給付款成功的訂單；只有待付款的才處理，已付款的不能被改回來
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Integer orderId) throws Exception {
        Order order = findOrder(orderId);
        if ("PENDING".equals(order.getOrderStatus())) {
            order.setOrderStatus("FAILED");
            // orphanRemoval = true：從清單移除，JPA 就會刪掉資料庫裡對應的 order_items
            order.getOrderItems().clear();
        }
    }

    // 從購物車移除這張訂單買的項目（ATM／超商代碼取號成功、或付款成功時呼叫）
    // 只移除訂單裡有的，付款期間另外加進購物車的遊戲會留著
    @Transactional(rollbackFor = Exception.class)
    public void clearCartForOrder(Integer orderId) throws Exception {
        Order order = findOrder(orderId);
        List<CartItem> toRemove = cartService.getCartItems(order.getMember().getMemberId()).stream()
                .filter(cartItem -> order.getOrderItems().stream().anyMatch(orderItem -> sameItem(cartItem, orderItem)))
                .toList();
        cartService.clearCart(toRemove);
    }

    // 找訂單，並確認是這位會員的（前端查詢付款結果用）
    public Order getMemberOrder(Integer memberId, Integer orderId) throws Exception {
        Order order = findOrder(orderId);
        if (!order.getMember().getMemberId().equals(memberId)) {
            throw new Exception("查無訂單");
        }
        return order;
    }

    // 用訂單編號找訂單
    public Order findOrder(Integer orderId) throws Exception {
        return orderRepository.findById(orderId).orElseThrow(() -> new Exception("查無訂單，orderId = " + orderId));
    }

    // 取得會員的所有訂單（新的在前面），待付款的順便帶上 ATM／超商代碼的繳費資訊
    public List<Order> getOrders(Integer memberId) throws Exception {
        memberService.findMemberById(memberId);
        List<Order> orders = orderRepository.findByMember_MemberIdOrderByCreatedAtDesc(memberId);
        orders.forEach(order -> order.setPaymentInfo(paymentInfos.get(order.getOrderId())));
        return orders;
    }

    // 取得某張訂單的繳費資訊，沒有就是 null
    public PaymentInfo getPaymentInfo(Integer orderId) {
        return paymentInfos.get(orderId);
    }

    // 取得會員的遊戲庫（最近取得的在前面）
    public List<MemberGame> getMemberGames(Integer memberId) throws Exception {
        memberService.findMemberById(memberId);
        return memberGameRepository.findByMember_MemberIdOrderByObtainedAtDesc(memberId);
    }

    // 把購物車內容轉成一張還沒存檔的訂單（含訂單項目、總金額、點數），訂單狀態由呼叫端決定
    private Order buildOrderFromCart(Integer memberId) throws Exception {
        Member member = memberService.findMemberById(memberId);
        List<CartItem> cartItems = cartService.getCartItems(memberId);
        if (cartItems.isEmpty()) {
            throw new Exception("購物車是空的");
        }

        Order order = new Order();
        order.setMember(member);
        order.setCreatedAt(LocalDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartItem : cartItems) {
            // 加入購物車後到結帳前，可能已經從別的地方取得這款遊戲，所以再檢查一次
            Member owner = cartItem.getIsGift() ? cartItem.getRecipientMember() : member;
            if (memberGameRepository.existsByMember_MemberIdAndGame_GameIdAndIsActiveTrue(
                    owner.getMemberId(), cartItem.getGame().getGameId())) {
                throw new Exception(owner.getUsername() + " 已經擁有《" + cartItem.getGame().getGameName()
                        + "》，請先從購物車移除");
            }
            // 加入購物車後優惠券可能已經過期或下架
            if (cartItem.getCoupon() != null && !cartItem.getCoupon().isUsableNow()) {
                throw new Exception("「" + cartItem.getCoupon().getCouponName() + "」已經不能使用，請重新選擇優惠券");
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setGame(cartItem.getGame());
            orderItem.setQuantity(1);
            orderItem.setUnitPrice(cartItem.getFinalPrice());
            orderItem.setIsGift(cartItem.getIsGift());
            orderItem.setRecipientMember(cartItem.getRecipientMember());
            orderItem.setCoupon(cartItem.getCoupon());
            order.getOrderItems().add(orderItem);
            total = total.add(cartItem.getFinalPrice());
        }
        order.setTotalAmount(total);
        order.setPointsEarned(total.intValue() / AMOUNT_PER_POINT);
        return order;
    }

    // 每個訂單項目寫一筆遊戲庫紀錄：自己買的進自己的遊戲庫，禮物進受贈者的遊戲庫
    // obtainedAt：取得時間（綠界付款就是付款完成的時間）
    // 付款期間可能已經從別的地方拿到這款遊戲，已經擁有的就跳過，不重複寫入
    private void grantGames(Order order, LocalDateTime obtainedAt) {
        for (OrderItem orderItem : order.getOrderItems()) {
            boolean isGift = orderItem.getIsGift();
            Member owner = isGift ? orderItem.getRecipientMember() : order.getMember();
            if (memberGameRepository.existsByMember_MemberIdAndGame_GameIdAndIsActiveTrue(
                    owner.getMemberId(), orderItem.getGame().getGameId())) {
                continue;
            }
            MemberGame memberGame = new MemberGame();
            memberGame.setMember(owner);
            memberGame.setGame(orderItem.getGame());
            memberGame.setOrder(order);
            memberGame.setObtainedSource(isGift ? "GIFT" : "PURCHASE");
            memberGame.setObtainedAt(obtainedAt);
            memberGame.setTotalPlayMinutes(0);
            memberGame.setIsActive(true);
            memberGameRepository.save(memberGame);
        }
    }

    // 購物車項目和訂單項目是不是同一筆：同一款遊戲、同樣是不是禮物、同一位受贈者
    private boolean sameItem(CartItem cartItem, OrderItem orderItem) {
        return cartItem.getGame().getGameId().equals(orderItem.getGame().getGameId())
                && Objects.equals(cartItem.getIsGift(), orderItem.getIsGift())
                && Objects.equals(memberIdOf(cartItem.getRecipientMember()), memberIdOf(orderItem.getRecipientMember()));
    }

    private Integer memberIdOf(Member member) {
        return member == null ? null : member.getMemberId();
    }
}
