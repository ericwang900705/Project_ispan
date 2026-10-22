package gameplatform.backoffice;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import gameplatform.admin.service.AdminPermissionService;
import gameplatform.support.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
class BackofficeTests {
 JdbcTemplate db;AdminPermissionService permissions;BackofficeService service;Actor actor=new Actor(1,"ADMIN","admin","");
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);permissions=mock(AdminPermissionService.class);service=new BackofficeService(db,permissions);when(permissions.forActor(actor)).thenReturn(List.copyOf(BackofficeService.ACCESS.values()));}
 @Test void deniedRoleCannotReadAnyManagementData(){when(permissions.forActor(actor)).thenReturn(List.of());for(String area:BackofficeService.ACCESS.keySet())assertEquals(403,assertThrows(Problem.class,()->service.list(actor,area,"","",0)).httpStatus());verifyNoInteractions(db);}
 @Test void unknownAreaCannotBecomeSqlIdentifier(){assertEquals(404,assertThrows(Problem.class,()->service.list(actor,"orders;DROP TABLE members","","",0)).httpStatus());verifyNoInteractions(db);}
 @Test void negativePageIsRejected(){assertThrows(Problem.class,()->service.list(actor,"orders","","",-1));verifyNoInteractions(db);}
 @Test void invalidStatusIsNotSql(){assertThrows(Problem.class,()->service.list(actor,"orders","","PAID' OR 1=1--",0));verifyNoInteractions(db);}
 @Test void keywordIsOnlyABoundValue(){when(db.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());service.list(actor,"orders","' OR 1=1--","",0);verify(db).queryForList(contains("CHARINDEX(?,"),eq("' OR 1=1--"),eq(0));}
 @Test void refundPermissionDoesNotPermitAllOrderDetails(){when(permissions.forActor(actor)).thenReturn(List.of("REFUND_MANAGE"));assertThrows(Problem.class,()->service.order(actor,9,false));verifyNoInteractions(db);}
 @Test void reviewActionCannotRestoreDeletedRow(){when(db.update(anyString(),any(Object[].class))).thenReturn(0);assertEquals(409,assertThrows(Problem.class,()->service.moderate(actor,7,new BackofficeService.ReviewInput("NORMAL","合法內容"))).httpStatus());verify(db,never()).update(startsWith("INSERT INTO dbo.admin_operation_logs"),any(Object[].class));}
 @Test void reviewReasonRequiredBeforeWrite(){assertThrows(Problem.class,()->service.moderate(actor,7,new BackofficeService.ReviewInput("HIDDEN"," ")));verifyNoInteractions(db);}
 @Test void tagsInUseCannotBeDeleted(){when(db.queryForObject(anyString(),eq(Integer.class),eq(7))).thenReturn(2);assertEquals(409,assertThrows(Problem.class,()->service.deleteTag(actor,7)).httpStatus());verify(db,never()).update(startsWith("DELETE"),any(Object[].class));}
 @Test void tagNameCannotBeEmpty(){assertThrows(Problem.class,()->service.saveTag(actor,null,new BackofficeService.TagInput(" ")));verifyNoInteractions(db);}
 BackofficeService.CouponInput coupon(String type,String value){return new BackofficeService.CouponInput("測試券",type,new BigDecimal(value),BigDecimal.ZERO,null,1,"ACTIVE",LocalDateTime.now(),LocalDateTime.now().plusDays(1));}
 @Test void rateMustBeFraction(){assertThrows(Problem.class,()->BackofficeService.validateCoupon(coupon("RATE","80")));}
 @Test void amountMustBePositive(){assertThrows(Problem.class,()->BackofficeService.validateCoupon(coupon("AMOUNT","0")));}
 @Test void validRateAndAmountAccepted(){assertDoesNotThrow(()->BackofficeService.validateCoupon(coupon("RATE","0.80")));assertDoesNotThrow(()->BackofficeService.validateCoupon(coupon("AMOUNT","100")));}
 @Test void nullCouponRejected(){assertThrows(Problem.class,()->BackofficeService.validateCoupon(null));}
 @Test void unauthorizedWriterCannotCreateCoupon(){when(permissions.forActor(actor)).thenReturn(List.of("ORDER_VIEW"));assertThrows(Problem.class,()->service.saveCoupon(actor,null,coupon("RATE","0.8")));verifyNoInteractions(db);}
 @Test void changedReportSnapshotCannotHideNewContent(){when(db.queryForList(contains("ugc_reports"),eq(4))).thenReturn(List.of(new HashMap<>(Map.of("status","PENDING","target_id",7,"snapshot_text","old"))));when(db.queryForList(contains("game_reviews"),eq(7))).thenReturn(List.of(Map.of("content","edited","status","NORMAL")));assertEquals(409,assertThrows(Problem.class,()->service.report(actor,4,new BackofficeService.ReportInput(true,"違規"))).httpStatus());verify(db,never()).update(anyString(),any(Object[].class));}
}
