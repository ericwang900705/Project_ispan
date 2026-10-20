package gameplatform.member.service;
import gameplatform.member.entity.*;
import gameplatform.member.repository.MemberLoginLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
@Service @RequiredArgsConstructor
public class MemberLoginAudit {
 private final MemberLoginLogRepository logs;private final HttpServletRequest request;
 public void record(Member member,String status){String ip=request.getRemoteAddr();if(ip==null||ip.isBlank())ip="unknown";if(ip.equals("::1")||ip.equals("0:0:0:0:0:0:0:1"))ip="127.0.0.1";var log=new MemberLoginLog();log.setMember(member);log.setIpAddress(ip.substring(0,Math.min(45,ip.length())));log.setLoginStatus(status);log.setLoginAt(LocalDateTime.now());logs.save(log);}
}
