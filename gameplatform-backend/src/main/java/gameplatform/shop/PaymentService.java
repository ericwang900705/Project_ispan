package gameplatform.shop;

import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.PropertySource;
import org.springframework.transaction.annotation.Transactional;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;

/** 綠界付款：沿用 CART 測試介接，保留商城訂單與 JWT 身分驗證。 */
@Service
@PropertySource(value = "classpath:ecpay.properties", encoding = "UTF-8")
public class PaymentService {
    final ShopService shop;
    final boolean enabled;
    final String merchant, key, iv, action, backend, frontend;

    public PaymentService(ShopService shop, @Value("${ecpay.enabled:false}") boolean enabled,
            @Value("${ecpay.merchant-id:}") String merchant, @Value("${ecpay.hash-key:}") String key,
            @Value("${ecpay.hash-iv:}") String iv,
            @Value("${ecpay.action-url:https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5}") String action,
            @Value("${ecpay.backend-url:${app.backend-url:http://127.0.0.1:8080}}") String backend,
            @Value("${ecpay.frontend-url:${app.frontend-url:http://127.0.0.1:5173}}") String frontend) {
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
        // CART 本機測試可透過瀏覽器回跳完成；伺服器通知仍需公開網址。
        boolean localStage = testMode() && isLoopback(uri) && "http".equals(uri.getScheme());
        if ((!"https".equals(uri.getScheme()) && !localStage) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null)
            throw new Problem(503, "付款回呼網址須為 HTTPS；測試環境可使用本機 HTTP");
        try {
            URI client = URI.create(frontend);
            if (client.getHost() == null || client.getUserInfo() != null || client.getFragment() != null
                    || client.getQuery() != null || !("https".equals(client.getScheme())
                            || (testMode() && isLoopback(client) && "http".equals(client.getScheme()))))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new Problem(503, "前台網址須為 HTTPS；測試環境可使用本機 HTTP");
        }
    }

    private static boolean isLoopback(URI uri) {
        return Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(
                uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT));
    }

    public boolean testMode() {
        return "https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5".equals(action);
    }

    public boolean publicCallback() {
        try {
            return "https".equals(URI.create(backend).getScheme()) && !isLoopback(URI.create(backend));
        } catch (IllegalArgumentException e) {
            return false;
        }
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
        shop.recordPaymentEnvironment(((Number) order.get("orderId")).intValue(), testMode());
        String trade = order.get("orderNo").toString();
        shop.addPaymentAttempt(((Number) order.get("orderId")).intValue(), trade, method, testMode());
        return form(order, method, trade);
    }

    @Transactional
    public Map<String, Object> resume(Actor actor, int member, int orderId) {
        ready();
        var order = shop.pendingPayment(actor, member, orderId, testMode());
        String method = Objects.toString(order.get("paymentType"), "");
        if (!Set.of("Credit", "ATM", "CVS").contains(method))
            throw new Problem(409, "此訂單付款方式需人工核對");
        String trade = "RP" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase(Locale.ROOT);
        shop.addPaymentAttempt(orderId, trade, method, testMode());
        return form(order, method, trade);
    }

    private Map<String, Object> form(Map<String, Object> order, String method, String trade) {
        var p = new LinkedHashMap<String, String>();
        p.put("MerchantID", merchant);
        p.put("MerchantTradeNo", trade);
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
        return shop.paymentFromAttempt(input, info, testMode());
    }

    // 補查只接受綠界回應的簽章、特店、交易編號及金額；不信任瀏覽器付款成功參數。
    public Object sync(Actor actor,int member,int orderId) {
        ready();
        String current=shop.paymentSyncState(actor,member,orderId);
        if("PAID".equals(current))return Map.of("status","success","message","此訂單已付款");
        if("REFUNDED".equals(current))return Map.of("status","refunded","message","此訂單已退款");
        String status="pending";
        for(var attempt:shop.paymentSyncAttempts(actor,member,orderId,testMode())) {
            String trade=attempt.get("merchant_trade_no").toString();
            var response=queryTrade(trade);
            if(!merchant.equals(response.get("MerchantID")) || !trade.equals(response.get("MerchantTradeNo")))
                throw new Problem(502,"付款核對的交易資料不符");
            var fields=new HashMap<>(response);String received=fields.remove("CheckMacValue");
            String expected=EcpayCheckMacValueUtil.generate(fields,key,iv);
            if(received==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),received.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
                throw new Problem(502,"付款核對簽章無效，請稍後重試");
            if("1".equals(response.get("TradeStatus"))) {
                if(!testMode() && "1".equals(response.get("SimulatePaid")))throw new Problem(502,"正式環境不接受模擬付款");
                var verified=new HashMap<>(response);verified.put("RtnCode","1");
                status=shop.paymentFromAttempt(verified,false,testMode());break;
            }
        }
        return Map.of("status",status,"message","success".equals(status)?"付款已確認，訂單已更新":"付款狀態已重新核對，請查看訂單結果");
    }

    Map<String,String> queryTrade(String trade) {
        var params=new LinkedHashMap<String,String>();params.put("MerchantID",merchant);params.put("MerchantTradeNo",trade);
        params.put("TimeStamp",Long.toString(Instant.now().getEpochSecond()));
        params.put("CheckMacValue",EcpayCheckMacValueUtil.generate(params,key,iv));
        String body=params.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        try {
            var client=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            String endpoint=testMode()?"https://payment-stage.ecpay.com.tw/Cashier/QueryTradeInfo/V5":"https://payment.ecpay.com.tw/Cashier/QueryTradeInfo/V5";
            var request=java.net.http.HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/x-www-form-urlencoded").POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            var result=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(result.statusCode()!=200 || result.body().length()>65536)throw new IllegalStateException();
            var response=new LinkedHashMap<String,String>();
            for(String pair:result.body().split("&")){String[] parts=pair.split("=",2);if(parts.length!=2)throw new IllegalStateException();String name=URLDecoder.decode(parts[0],StandardCharsets.UTF_8);if(response.putIfAbsent(name,URLDecoder.decode(parts[1],StandardCharsets.UTF_8))!=null)throw new IllegalStateException();}
            return response;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new Problem(503,"付款核對暫時中斷，請稍後重試");}
        catch(Exception e){throw new Problem(503,"暫時無法向綠界確認付款，請稍後重試");}
    }

    public URI redirect(String status, String trade) {
        String target = trade != null && trade.startsWith("RP") ? "/orders" : "/shopping-cart";
        return URI.create(frontend + "/#" + target + "?payment=" + URLEncoder.encode(status, StandardCharsets.UTF_8)
                + "&tradeNo=" + URLEncoder.encode(trade == null ? "" : trade, StandardCharsets.UTF_8));
    }
}
