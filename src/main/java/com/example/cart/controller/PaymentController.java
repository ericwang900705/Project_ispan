package com.example.cart.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cart.model.Order;
import com.example.cart.model.PaymentInfo;
import com.example.cart.service.OrderService;
import com.example.cart.util.EcpayCheckMacValueUtil;

/**
 * 串接綠界科技（ECPay）全方位金流 AioCheckOut 介面。
 *
 * <p>
 * 用的是綠界官方公開的「測試特店」帳號（MerchantID 3002607，HashKey/HashIV 見 application.properties），
 * 會進到綠界的測試付款頁，可以用官方公告的測試信用卡操作，不會真的扣款。
 *
 * <p>
 * 完整流程：
 * <ol>
 * <li>前端呼叫 {@link #checkout}：把購物車變成一筆「待付款」訂單，拿到要送往綠界的表單參數（含簽章）</li>
 * <li>前端用這些參數組一個 {@code <form method="post">}，整頁導向綠界的付款頁</li>
 * <li>使用者在綠界頁面完成（測試用的）付款</li>
 * <li>綠界把使用者的瀏覽器導回 {@link #result}（OrderResultURL）：付款成功就把訂單改成已付款、
 * 遊戲加進遊戲庫，再轉回前端購物車頁並帶上付款結果。
 * ATM／超商代碼不是即時付款，改成導回 {@link #infoRedirect}（ClientRedirectURL），
 * 帶回的是虛擬帳號／繳費代碼，訂單維持待付款</li>
 * <li>綠界伺服器另外會打 {@link #notify}（ReturnURL）做後端對後端的最終確認；
 * 本機 localhost 沒有對外公開的網址，綠界連不進來，要部署後（或用 ngrok）才會真的被打到，這不是程式寫錯</li>
 * </ol>
 *
 * <p>
 * 綠界回傳的交易資料存到哪裡：
 * <ul>
 * <li>MerchantTradeNo（我方訂單編號）：由 orders.order_id 組成，回來時拆出 order_id 找訂單</li>
 * <li>RtnCode（付款結果）→ orders.order_status：PAID / FAILED</li>
 * <li>TradeAmt（實付金額）→ 跟 orders.total_amount 比對，對不上就不算付款成功</li>
 * <li>TradeNo（綠界交易編號）→ orders.ecpay_trade_no</li>
 * <li>PaymentType（付款方式）→ orders.payment_type</li>
 * <li>PaymentDate（付款時間）→ orders.paid_at、member_games.obtained_at</li>
 * </ul>
 *
 * <p>
 * 登入功能做好之前，跟購物車一樣用網址上的 memberId 代表目前的會員。
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    // 綠界回傳的付款時間格式，例如 2026/10/08 15:03:21
    private static final DateTimeFormatter ECPAY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    @Value("${ecpay.merchant-id}")
    private String merchantId;
    @Value("${ecpay.hash-key}")
    private String hashKey;
    @Value("${ecpay.hash-iv}")
    private String hashIv;
    @Value("${ecpay.action-url}")
    private String actionUrl;
    @Value("${app.backend-url}")
    private String backendUrl;
    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final OrderService orderService;

    // 建構子注入：Spring 會自動把 OrderService 的實例傳進來
    public PaymentController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 前端送出結帳時的請求內容：只需要付款方式，買哪些遊戲、金額多少都由後端從購物車算 */
    public record CheckoutRequest(String choosePayment) { // "Credit"｜"ATM"｜"CVS"
    }

    /** 回給前端的結果：actionUrl 是表單要送往的綠界網址，params 是要塞進隱藏欄位的表單內容 */
    public record CheckoutResponse(String actionUrl, Map<String, String> params) {
    }

    /** 前端查詢付款結果時拿到的資料（購物車頁的付款結果區塊用） */
    public record OrderResponse(Integer orderId, BigDecimal totalAmount, String orderStatus, Integer pointsEarned,
            String bankCode, String virtualAccount, String paymentNo, String expireDate) {
    }

    /**
     * 建立待付款訂單並產生送往綠界的表單參數（含簽章 CheckMacValue）。
     * POST /api/payment/{memberId}/checkout，body：{ "choosePayment": "Credit" }
     */
    @PostMapping("/{memberId}/checkout")
    public CheckoutResponse checkout(@PathVariable Integer memberId, @RequestBody CheckoutRequest request)
            throws Exception {
        String choosePayment = request.choosePayment();
        if (!"Credit".equals(choosePayment) && !"ATM".equals(choosePayment) && !"CVS".equals(choosePayment)) {
            throw new Exception("不支援的付款方式");
        }

        Order order = orderService.createPendingOrder(memberId);
        String tradeNo = buildTradeNo(order.getOrderId());
        // 綠界只收整數金額：遊戲價格都是整數元，四捨五入只是保險
        String totalAmount = order.getTotalAmount().setScale(0, RoundingMode.HALF_UP).toPlainString();

        Map<String, String> params = new HashMap<>();
        params.put("MerchantID", merchantId);
        params.put("MerchantTradeNo", tradeNo);
        params.put("MerchantTradeDate", new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
        params.put("PaymentType", "aio"); // 綠界固定值
        params.put("TotalAmount", totalAmount);
        params.put("TradeDesc", "遊戲商城訂單");
        params.put("ItemName", buildItemName(order));
        params.put("ReturnURL", backendUrl + "/api/payment/notify"); // 綠界伺服器對後端的非同步通知
        params.put("ChoosePayment", choosePayment);
        params.put("ClientBackURL", frontendUrl + "/shopping-cart"); // 綠界頁面上「返回商店」按鈕要導去的網址
        params.put("OrderResultURL", backendUrl + "/api/payment/result"); // 付款完成後，使用者瀏覽器會被導到這裡
        params.put("EncryptType", "1"); // 固定值，代表用 SHA256 簽章

        // ATM / 超商代碼才需要繳費期限；信用卡是即時付款，不需要這兩個欄位
        if ("ATM".equals(choosePayment)) {
            params.put("ExpireDate", "3"); // ATM 轉帳期限：3 天
        } else if ("CVS".equals(choosePayment)) {
            params.put("StoreExpireDate", "10080"); // 超商代碼繳費期限：10080 分鐘＝7 天
        }

        // ATM / 超商代碼不是即時付款：綠界會先「取號」（產生虛擬帳號或繳費代碼），使用者之後才去繳費。
        // 取號結果不會送到 OrderResultURL，要另外用這兩個參數接；
        // 注意設了 ClientRedirectURL 之後，綠界頁面上「返回商店」（ClientBackURL）的按鈕會失效。
        if ("ATM".equals(choosePayment) || "CVS".equals(choosePayment)) {
            params.put("PaymentInfoURL", backendUrl + "/api/payment/info"); // 綠界伺服器對後端的取號通知
            params.put("ClientRedirectURL", backendUrl + "/api/payment/info-redirect"); // 取號後，使用者瀏覽器會被導到這裡
        }

        // 簽章一定要用「目前所有欄位」去算，所以放在最後一步，算完才把結果加進去
        params.put("CheckMacValue", EcpayCheckMacValueUtil.generate(params, hashKey, hashIv));
        return new CheckoutResponse(actionUrl, params);
    }

    /**
     * OrderResultURL：使用者在綠界頁面完成付款後，瀏覽器會被導來這裡（POST）。
     * 驗證簽章、更新訂單狀態，再 302 轉回前端購物車頁並帶上結果。
     */
    @PostMapping("/result")
    public ResponseEntity<Void> result(@RequestParam Map<String, String> params) {
        return redirectToFrontend(applyPaymentResult(params), params);
    }

    /**
     * ReturnURL：綠界伺服器對後端的非同步通知（本機測試時連不進來，見類別上的說明）。
     * 依綠界規定，收到通知後必須回覆純文字 {@code "1|OK"}，否則綠界會持續重送通知。
     */
    @PostMapping("/notify")
    public ResponseEntity<String> notify(@RequestParam Map<String, String> params) {
        applyPaymentResult(params);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("1|OK");
    }

    /**
     * ClientRedirectURL：ATM／超商代碼取號完成後，使用者瀏覽器會被導來這裡（POST）。
     * 存下虛擬帳號／繳費代碼後轉回前端，前端再用訂單編號呼叫 {@link #order} 把繳費資訊抓回去顯示
     * （不直接把帳號放在轉址的網址列上，避免留在瀏覽紀錄裡）。
     */
    @PostMapping("/info-redirect")
    public ResponseEntity<Void> infoRedirect(@RequestParam Map<String, String> params) {
        return redirectToFrontend(applyPaymentInfo(params), params);
    }

    /**
     * PaymentInfoURL：綠界伺服器對後端的取號通知（跟 ReturnURL 一樣，本機 localhost 收不到）。
     * 一樣必須回覆純文字 {@code "1|OK"}。
     */
    @PostMapping("/info")
    public ResponseEntity<String> info(@RequestParam Map<String, String> params) {
        applyPaymentInfo(params);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("1|OK");
    }

    /**
     * 前端用訂單編號查詢付款結果與繳費資訊（虛擬帳號／繳費代碼／繳費期限）。只能查自己的訂單。
     * GET /api/payment/{memberId}/orders/{tradeNo}（tradeNo 是綠界導回來時網址上的 MerchantTradeNo）
     */
    @GetMapping("/{memberId}/orders/{tradeNo}")
    public OrderResponse order(@PathVariable Integer memberId, @PathVariable String tradeNo) throws Exception {
        Integer orderId = parseOrderId(tradeNo);
        if (orderId == null) {
            throw new Exception("查無訂單");
        }
        Order order = orderService.getMemberOrder(memberId, orderId);
        PaymentInfo info = orderService.getPaymentInfo(orderId);
        if (info == null) {
            info = new PaymentInfo(null, null, null, null, null);
        }
        return new OrderResponse(order.getOrderId(), order.getTotalAmount(), order.getOrderStatus(),
                order.getPointsEarned(), info.bankCode(), info.virtualAccount(), info.paymentNo(), info.expireDate());
    }

    /**
     * 驗證簽章、記下取號結果，回傳 "pending"（取號成功、等待繳費）或 "fail"。
     * 取號成功的 RtnCode 不是 1：ATM 是 2、超商代碼是 10100073，
     * 所以不能丟進 {@link #applyPaymentResult}，否則會被當成付款失敗。
     * 這裡不改訂單狀態（維持待付款），真正付款成功要等綠界打 ReturnURL。
     */
    private String applyPaymentInfo(Map<String, String> params) {
        if (!verifyCheckMac(params)) {
            return "fail"; // 簽章不對，不相信這筆通知
        }
        String rtnCode = params.get("RtnCode");
        if (!"2".equals(rtnCode) && !"10100073".equals(rtnCode)) {
            return "fail";
        }
        Integer orderId = parseOrderId(params.get("MerchantTradeNo"));
        PaymentInfo info = new PaymentInfo(params.get("PaymentType"), params.get("BankCode"),
                params.get("vAccount"), params.get("PaymentNo"), params.get("ExpireDate"));
        try {
            // 取號成功：記下繳費資訊（「我的訂單」頁也看得到），購物車拿掉這張訂單的遊戲
            orderService.markAwaitingPayment(orderId, info);
        } catch (Exception e) {
            return "fail"; // 查無訂單
        }
        logTransaction("取號", orderId, params);
        return "pending";
    }

    /** 驗證簽章、依 RtnCode 更新訂單，回傳 "success" 或 "fail" 給呼叫端決定要怎麼呈現 */
    private String applyPaymentResult(Map<String, String> params) {
        if (!verifyCheckMac(params)) {
            return "fail"; // 簽章不對，不相信這筆通知
        }
        Integer orderId = parseOrderId(params.get("MerchantTradeNo"));
        boolean success = "1".equals(params.get("RtnCode"));
        logTransaction(success ? "付款成功" : "付款失敗", orderId, params);
        try {
            if (success) {
                orderService.markPaid(orderId, parseAmount(params.get("TradeAmt")), parsePaymentDate(params.get("PaymentDate")),
                        params.get("TradeNo"), params.get("PaymentType"));
            } else {
                orderService.markFailed(orderId);
            }
        } catch (Exception e) {
            log.warn("綠界付款結果處理失敗：{}", e.getMessage()); // 查無訂單、金額不符
            return "fail";
        }
        return success ? "success" : "fail";
    }

    /** 重新算一次簽章，跟對方送來的 CheckMacValue 比對，確認資料真的來自綠界、沒有被竄改 */
    private boolean verifyCheckMac(Map<String, String> params) {
        String received = params.get("CheckMacValue");
        if (received == null) {
            return false;
        }
        Map<String, String> withoutMac = new HashMap<>(params);
        withoutMac.remove("CheckMacValue");
        String expected = EcpayCheckMacValueUtil.generate(withoutMac, hashKey, hashIv);
        return expected.equalsIgnoreCase(received);
    }

    /** 302 轉回前端購物車頁，網址帶上付款結果和綠界訂單編號，例如 /shopping-cart?payment=success&tradeNo=EC15T... */
    private ResponseEntity<Void> redirectToFrontend(String status, Map<String, String> params) {
        String tradeNo = params.getOrDefault("MerchantTradeNo", "");
        String redirectUrl = frontendUrl + "/shopping-cart?payment=" + status + "&tradeNo=" + tradeNo;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    /** 商品名稱：綠界規定多個商品用 # 分隔，最長 400 字，這裡保守截在 200 字 */
    private String buildItemName(Order order) {
        String itemName = order.getOrderItems().stream()
                .map(item -> item.getGame().getGameName())
                .collect(Collectors.joining("#"));
        return itemName.length() > 200 ? itemName.substring(0, 200) : itemName;
    }

    /**
     * 綠界訂單編號 MerchantTradeNo = "EC" + order_id + "T" + 時間，例如 EC15TMG1A2B3C。
     * 綠界規定只能英數字、最長 20 字、不能重複；測試帳號 3002607 是所有開發者共用的，
     * 只用 order_id（EC15）很容易跟別人撞號，所以後面再加上時間（毫秒轉 36 進位，8 個字）。
     * 這樣 orders 不用多一個欄位存綠界編號，回來時用 {@link #parseOrderId} 拆出 order_id 就好。
     */
    private String buildTradeNo(Integer orderId) {
        return "EC" + orderId + "T" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    }

    /** 從 MerchantTradeNo 拆出 order_id，例如 EC15TMG1A2B3C → 15；格式不對回傳 null */
    private Integer parseOrderId(String tradeNo) {
        if (tradeNo == null || !tradeNo.matches("EC\\d+T[0-9A-Z]+")) {
            return null;
        }
        return Integer.valueOf(tradeNo.substring(2, tradeNo.indexOf('T')));
    }

    /** 綠界的實付金額（TradeAmt），沒有或格式不對回傳 null（markPaid 會判定金額不符） */
    private BigDecimal parseAmount(String tradeAmt) {
        try {
            return new BigDecimal(tradeAmt);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 綠界的付款時間（PaymentDate），沒有或格式不對就用現在時間 */
    private LocalDateTime parsePaymentDate(String paymentDate) {
        try {
            return LocalDateTime.parse(paymentDate, ECPAY_DATE);
        } catch (RuntimeException e) {
            return LocalDateTime.now();
        }
    }

    /** 每次綠界通知都印在後端主控台（付款失敗、取號這些不會寫進 orders 的也留得到紀錄） */
    private void logTransaction(String event, Integer orderId, Map<String, String> params) {
        log.info("綠界{}：order_id={}，綠界交易編號={}，付款方式={}，金額={}，付款時間={}，訊息={}", event, orderId,
                params.get("TradeNo"), params.get("PaymentType"), params.get("TradeAmt"), params.get("PaymentDate"),
                params.get("RtnMsg"));
    }
}
