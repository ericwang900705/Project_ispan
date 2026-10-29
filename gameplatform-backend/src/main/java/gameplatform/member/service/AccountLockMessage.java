package gameplatform.member.service;
import java.time.*;
import java.time.format.DateTimeFormatter;
public final class AccountLockMessage {
 private AccountLockMessage() {}
 public static String until(LocalDateTime until) {
  long minutes=Math.max(1,(Duration.between(LocalDateTime.now(),until).getSeconds()+59)/60);
  String remaining=minutes>=60?(minutes/60)+" 小時 "+(minutes%60)+" 分鐘":minutes+" 分鐘";
  return "帳號已停權或暫時鎖定至 "+until.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))+"，約剩 "+remaining+"。";
 }
}
