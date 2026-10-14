package gameplatform.shop;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import gameplatform.support.model.Actor;
import gameplatform.support.service.JwtService;
@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.config.import=","spring.datasource.url=jdbc:h2:mem:shop_http;MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:shop-schema.sql","ecpay.enabled=false"})
class ShopHttpTest {
 @Value("${local.server.port}") int port;
 @Autowired JwtService jwt;
 HttpResponse<String> call(String path,String token,String body)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));if(token!=null)b.header("Authorization","Bearer "+token);if(body==null)b.GET();else b.header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());}
 String token(int id,String role){return (String)jwt.issue(new Actor(id,role,"test","")).get("accessToken");}
 @Test void cartRequiresJwtAndMatchingMember()throws Exception{assertEquals(401,call("/api/cart/1",null,null).statusCode());assertEquals(200,call("/api/cart/1",token(1,"MEMBER"),null).statusCode());assertEquals(403,call("/api/cart/2",token(1,"MEMBER"),null).statusCode());assertEquals(403,call("/api/cart/1",token(1,"ADMIN"),null).statusCode());}
 @Test void reviewsReadableAnonymouslyAndGatewayCallbacksUseSignatureInsteadOfJwt()throws Exception{assertEquals(200,call("/api/reviews/game/1",null,null).statusCode());assertEquals(503,call("/api/payment/notify",null,"MerchantID=wrong&CheckMacValue=wrong").statusCode());}
 @Test void oldBypassCheckoutIsNotAvailable()throws Exception{assertEquals(404,call("/api/orders/1/checkout",token(1,"MEMBER"),"").statusCode());}
}
