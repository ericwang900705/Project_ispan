package gameplatform.shop;

import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;

@Service
public class PaymentService {
    final ShopService shop;
    final boolean enabled;
    final String merchant, key, iv, action, backend, frontend;

    public PaymentService(ShopService shop, @Value("${ecpay.enabled:false}") boolean enabled,
            @Value("${ecpay.merchant-id:}") String merchant, @Value("${ecpay.hash-key:}") String key,
            @Value("${ecpay.hash-iv:}") String iv,
            @Value("${ecpay.action-url:https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5}") String action,
            @Value("${ecpay.backend-url:}") String backend,
            @Value("${ecpay.frontend-url:http://localhost:5173}") String frontend) {
        this.shop = shop;
        this.enabled = enabled;
        this.merchant = merchant;
        this.key = key;
        this.iv = iv;
        this.action = action;
        this.backend = backend.replaceAll("/+$", "");
        this.frontend = frontend.replaceAll("/+$", "");
    }

    void ready() {
        if (!enabled || merchant.isBlank() || key.isBlank() || iv.isBlank() || backend.isBlank())
            throw new Problem(503, "尚未設定綠界金流，請先設定測試特店及公開回呼網址");
        if (!Set.of("https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5",
                "https://payment.ecpay.com.tw/Cashier/AioCheckOut/V5").contains(action))
            throw new Problem(503, "綠界付款網址設定錯誤");
        URI uri;
        try {
            uri = URI.create(backend);
        } catch (Exception e) {
            throw new Problem(503, "付款回呼網址設定錯誤");
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getFragment() != null
                || uri.getQuery() != null)
            throw new Problem(503, "請設定公開 HTTPS 付款回呼網址");
    }

    public boolean available() {
        try {
            ready();
            return true;
        } catch (Problem e) {
            return false;
        }
    }

    @Transactional
    public Map<String, Object> checkout(Actor a, int member, String method) {
        shop.owner(a, member);
        ready();
        if (!Set.of("Credit", "ATM", "CVS").contains(method))
            throw new Problem(400, "不支援的付款方式");
        var order = shop.createOrder(a, member, method);
        var p = new LinkedHashMap<String, String>();
        p.put("MerchantID", merchant);
        p.put("MerchantTradeNo", order.get("orderNo").toString());
        p.put("MerchantTradeDate",
                LocalDateTime.now(ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        p.put("PaymentType", "aio");
        p.put("TotalAmount", ShopService.money(order.get("totalAmount")).toBigIntegerExact().toString());
        p.put("TradeDesc", "HERO遊戲商城訂單");
        p.put("ItemName", "HERO遊戲訂單");
        p.put("ReturnURL", backend + "/api/payment/notify");
        p.put("OrderResultURL", backend + "/api/payment/result");
        p.put("ClientBackURL", frontend + "/#/orders");
        p.put("ChoosePayment", method);
        p.put("EncryptType", "1");
        if (!"Credit".equals(method)) {
            p.put("PaymentInfoURL", backend + "/api/payment/info");
            p.put("ClientRedirectURL", backend + "/api/payment/info-redirect");
            if ("ATM".equals(method))
                p.put("ExpireDate", "3");
            else
                p.put("StoreExpireDate", "10080");
        }
        p.put("CheckMacValue", EcpayCheckMacValueUtil.generate(p, key, iv));
        return Map.of("actionUrl", action, "params", p, "orderId", order.get("orderId"));
    }

    public String callback(Map<String, String> input, boolean info) {
        ready();
        String received = input.get("CheckMacValue");
        if (received == null || !merchant.equals(input.get("MerchantID")))
            throw new Problem(400, "金流特店或簽章無效");
        var fields = new HashMap<>(input);
        fields.remove("CheckMacValue");
        String expected = EcpayCheckMacValueUtil.generate(fields, key, iv);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                received.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
            throw new Problem(400, "金流簽章無效");
        if ("1".equals(input.get("SimulatePaid")) && action.startsWith("https://payment.ecpay.com.tw/"))
            throw new Problem(400, "正式環境不接受模擬付款");
        return shop.payment(input, info);
    }

    public URI redirect(String status, String trade) {
        return URI.create(frontend + "/#/shopping-cart?payment=" + URLEncoder.encode(status, StandardCharsets.UTF_8)
                + "&tradeNo=" + URLEncoder.encode(trade == null ? "" : trade, StandardCharsets.UTF_8));
    }
}
