package gameplatform.support.service;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import gameplatform.support.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DashboardService {
 private final JdbcTemplate db;
 public DashboardService(JdbcTemplate db){this.db=db;}
 @Transactional(readOnly=true)
 public Map<String,Object> overview(Actor actor){
  if(actor==null||!actor.isAdmin())throw new Problem(403,"只有管理者可以查看平台儀表板");
  var month=YearMonth.now(ZoneId.of("Asia/Taipei"));var trend=new ArrayList<Map<String,Object>>();
  for(int i=5;i>=0;i--){var value=month.minusMonths(i);trend.add(Map.of("month",value.toString(),"amount",paid(value)));}
  var data=new LinkedHashMap<String,Object>();
  data.put("memberCount",db.queryForObject("SELECT COUNT(*) FROM members",Long.class));
  data.put("gameCount",db.queryForObject("SELECT COUNT(*) FROM games",Long.class));
  data.put("monthlyOrderCount",db.queryForObject("SELECT COUNT(*) FROM orders WHERE created_at>=? AND created_at<?",Long.class,start(month),start(month.plusMonths(1))));
  data.put("monthlyRevenue",paid(month));data.put("revenueTrend",trend);data.put("commerceConnected",true);data.put("updatedAt",Instant.now());return data;
 }
 private Timestamp start(YearMonth month){return Timestamp.valueOf(month.atDay(1).atStartOfDay());}
 private BigDecimal paid(YearMonth month){return db.queryForObject("SELECT COALESCE(SUM(total_amount),0) FROM orders WHERE order_status='PAID' AND paid_at>=? AND paid_at<?",BigDecimal.class,start(month),start(month.plusMonths(1)));}
}
