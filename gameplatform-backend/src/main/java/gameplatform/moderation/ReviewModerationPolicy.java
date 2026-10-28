package gameplatform.moderation;

import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** 評論違規與禁言；寫入使用呼叫端同一交易，固定先鎖會員再鎖評論。 */
public class ReviewModerationPolicy {
    private final JdbcTemplate db;
    public ReviewModerationPolicy(JdbcTemplate db){this.db=db;}
    public void lockMember(int member){
        if(db.update("UPDATE dbo.members SET failed_login_count=failed_login_count WHERE member_id=?",member)!=1)throw new Problem(404,"找不到會員");
    }
    private LocalDateTime until(int member){
        var rows=db.queryForList("SELECT muted_until FROM dbo.member_review_mutes WHERE member_id=?",member);
        if(rows.isEmpty())return null;
        Object value=rows.getFirst().get("muted_until");
        return value instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)value;
    }
    private int strikes(int member){
        Integer n=db.queryForObject("SELECT COUNT(*) FROM dbo.review_violations WHERE member_id=? AND reversed_at IS NULL AND confirmed_at>=?",Integer.class,member,LocalDateTime.now().minusDays(30));
        return n==null?0:n;
    }
    public Map<String,Object> status(int member){
        var end=until(member);var out=new LinkedHashMap<String,Object>();
        out.put("muted",end!=null&&end.isAfter(LocalDateTime.now()));out.put("mutedUntil",end==null?null:end.toString());
        out.put("violationsIn30Days",strikes(member));out.put("policy","30 天內 2 則不同評論確認違規，禁言 6 小時");return out;
    }
    public void ensureCanPost(int member){
        var end=until(member);
        if(end!=null&&end.isAfter(LocalDateTime.now()))throw new Problem(403,"評論功能暫停至 "+end.toString().replace('T',' ')+"，期間仍可登入、購買與聯絡客服。");
    }
    private void audit(Actor actor,String action,int member,String reason){
        db.update("INSERT INTO dbo.admin_operation_logs(admin_id,action_code,target_id,detail) VALUES(?,?,?,?)",actor.id(),action,member,reason);
    }
    public void confirm(Actor actor,int review,int member,Integer report,String reason){
        var rows=db.queryForList("SELECT violation_id,reversed_at FROM dbo.review_violations WHERE review_id=?",review);
        // 同一評論的重複檢舉不重新計次或延長禁言。
        if(!rows.isEmpty()&&rows.getFirst().get("reversed_at")==null)return;
        if(rows.isEmpty())db.update("INSERT INTO dbo.review_violations(review_id,member_id,report_id,admin_id,reason) VALUES(?,?,?,?,?)",review,member,report,actor.id(),reason);
        else db.update("UPDATE dbo.review_violations SET report_id=?,admin_id=?,reason=?,confirmed_at=CURRENT_TIMESTAMP,reversed_at=NULL,reversed_by_admin_id=NULL WHERE review_id=?",report,actor.id(),reason,review);
        if(strikes(member)>=2){
            var end=LocalDateTime.now().plusHours(6);var old=until(member);if(old!=null&&old.isAfter(end))end=old;
            String detail="30 天內至少 2 則評論確認違規，暫停新增與修改評論至 "+end.toString().replace('T',' ');
            if(db.update("UPDATE dbo.member_review_mutes SET muted_until=?,reason=?,updated_at=CURRENT_TIMESTAMP WHERE member_id=?",end,detail,member)==0)db.update("INSERT INTO dbo.member_review_mutes(member_id,muted_until,reason) VALUES(?,?,?)",member,end,detail);
            audit(actor,"REVIEW_MUTE",member,detail);
        }
    }
    public void reverse(Actor actor,int review,int member){
        int changed=db.update("UPDATE dbo.review_violations SET reversed_at=CURRENT_TIMESTAMP,reversed_by_admin_id=? WHERE review_id=? AND reversed_at IS NULL",actor.id(),review);
        var end=until(member);
        if(changed>0&&strikes(member)<2&&end!=null&&end.isAfter(LocalDateTime.now())){
            db.update("UPDATE dbo.member_review_mutes SET muted_until=CURRENT_TIMESTAMP,reason=?,updated_at=CURRENT_TIMESTAMP WHERE member_id=?","撤銷違規後未達累犯門檻",member);
            audit(actor,"REVIEW_UNMUTE",member,"恢復評論，撤銷違規後未達累犯門檻");
        }
    }
}
