package gameplatform.backoffice;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import gameplatform.support.model.Problem;

/** 後台優惠券規則必須同時通過折扣、時間與適用對象驗證。 */
class CouponScopeValidationTests {
    BackofficeService.CouponInput input(String scope,Integer publisher,Integer tag) {
        return new BackofficeService.CouponInput("測試券","RATE",new BigDecimal("0.8"),BigDecimal.ZERO,null,1,
            "ACTIVE",LocalDateTime.now(),LocalDateTime.now().plusDays(1),scope,publisher,tag);
    }
    @Test void validScopesAccepted() {
        assertDoesNotThrow(()->BackofficeService.validateCoupon(input("ALL",null,null)));
        assertDoesNotThrow(()->BackofficeService.validateCoupon(input("PUBLISHER",7,null)));
        assertDoesNotThrow(()->BackofficeService.validateCoupon(input("TAG",null,3)));
    }
    @Test void missingOrMixedTargetsRejected() {
        assertThrows(Problem.class,()->BackofficeService.validateCoupon(input("PUBLISHER",null,null)));
        assertThrows(Problem.class,()->BackofficeService.validateCoupon(input("TAG",7,3)));
        assertThrows(Problem.class,()->BackofficeService.validateCoupon(input("ALL",7,null)));
        assertThrows(Problem.class,()->BackofficeService.validateCoupon(input("UNKNOWN",null,null)));
    }
}
