package gameplatform.member.exception;

/**
 * 會員登入失敗例外
 *
 * 用途：
 * 密碼驗證失敗時拋出此例外，
 * 讓登入失敗次數可以正常儲存至資料庫。
 */
public class LoginFailedException extends RuntimeException {

    public LoginFailedException(String message) {
        super(message);
    }
}