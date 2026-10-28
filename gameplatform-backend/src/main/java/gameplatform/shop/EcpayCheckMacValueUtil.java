package gameplatform.shop;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * 綠界科技（ECPay）CheckMacValue 簽章工具。
 *
 * <p>
 * 每一筆送往綠界的請求、以及綠界回傳給我們的每一筆通知，都帶著這個簽章；
 * 雙方各自用同一套公式重新算一次，結果一致才代表資料沒有被竄改、確實來自雙方。
 * 官方規定的算法（EncryptType=1，SHA256）：
 * <ol>
 * <li>把所有參數（不含 CheckMacValue 本身）依參數名稱由 A 到 Z 排序</li>
 * <li>串成一個字串：{@code HashKey=金鑰&參數1=值1&參數2=值2...&HashIV=金鑰}</li>
 * <li>對整串字串做 URL encode</li>
 * <li>轉成全部小寫</li>
 * <li>把一般 URL encode 會跳脫、但 .NET 的 UrlEncode 不會跳脫的符號換回原字元：
 * {@code %2d→- ，%5f→_ ，%2e→. ，%21→! ，%2a→* ，%28→( ，%29→)}</li>
 * <li>整串做 SHA256 雜湊，轉成大寫十六進位字串</li>
 * </ol>
 *
 * <p>
 * 這裡每一步都必須跟官方文件一字不差，只要哪個環節跟綠界算出來的不一樣，
 * 綠界就會判定簽章錯誤而拒絕請求（常見錯誤訊息：CheckMacValue 檢查錯誤）。
 */
public final class EcpayCheckMacValueUtil {

    private EcpayCheckMacValueUtil() {
    }

    /** 依官方演算法計算簽章，傳入的 params 不應該包含 CheckMacValue 這個 key 自己 */
    public static String generate(Map<String, String> params, String hashKey, String hashIv) {
        // 綠界規定依參數名稱 A-Z 排序、不分大小寫，Java 的 TreeMap 預設大寫排在小寫前面，所以要指定不分大小寫
        TreeMap<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sorted.putAll(params);

        StringBuilder raw = new StringBuilder();
        raw.append("HashKey=").append(hashKey);
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            raw.append('&').append(entry.getKey()).append('=').append(entry.getValue());
        }
        raw.append("&HashIV=").append(hashIv);

        String encoded = urlEncode(raw.toString()).toLowerCase(java.util.Locale.ROOT);
        // Java 的 URLEncoder 會把 ! ( ) 跳脫成 %21 %28 %29，但 .NET 的 UrlEncode 不會跳脫這幾個符號，
        // 所以這裡要換回原字元，才能跟綠界伺服器重算出來的結果一致。
        // （-、_、. 這三個 Java 本來就不會跳脫，下面幾行對它們只是保險、不會真的被用到）
        encoded = encoded.replace("%2d", "-")
                .replace("%5f", "_")
                .replace("%2e", ".")
                .replace("%21", "!")
                .replace("%2a", "*")
                .replace("%28", "(")
                .replace("%29", ")");

        return sha256Hex(encoded).toUpperCase(java.util.Locale.ROOT);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 一定存在，不會發生這個例外", e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new IllegalStateException("SHA-256 與 UTF-8 一定存在，不會發生這個例外", e);
        }
    }
}
