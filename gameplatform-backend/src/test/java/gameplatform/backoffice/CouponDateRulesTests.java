package gameplatform.backoffice;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import gameplatform.support.model.Problem;
/** 新建或改期的優惠券期限固定七天。 */
class CouponDateRulesTests {
 @Test void acceptsTodayAndSevenDays(){var start=LocalDate.now(ZoneId.of("Asia/Taipei")).atStartOfDay();assertDoesNotThrow(()->BackofficeService.validateCouponDates(start,start.plusDays(7)));}
 @Test void rejectsPastOrWrongLength(){var start=LocalDate.now(ZoneId.of("Asia/Taipei")).atStartOfDay();assertThrows(Problem.class,()->BackofficeService.validateCouponDates(start.minusDays(1),start.plusDays(6)));assertThrows(Problem.class,()->BackofficeService.validateCouponDates(start,start.plusDays(2)));assertThrows(Problem.class,()->BackofficeService.validateCouponDates(start,start.plusDays(7).minusSeconds(1)));}
}
