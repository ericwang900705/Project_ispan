package gameplatform.friend;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import gameplatform.support.model.Actor;
@RestController
public class FriendController {
 private final FriendService service;
 public FriendController(FriendService service){this.service=service;}
 public record Group(String roomName,List<Integer> memberIds){}
 public record Message(String content){}
 @GetMapping("/api/friends/search") public Object search(Actor a,@RequestParam String username){return service.search(a,username);}
 @GetMapping("/api/friends/list") public Object friends(Actor a){return service.list(a,false);}
 @GetMapping("/api/friends/requests/pending") public Object pending(Actor a){return service.list(a,true);}
 @PostMapping("/api/friends/request/{id}") public Object request(Actor a,@PathVariable int id){service.request(a,id);return Map.of("message","好友邀請已送出");}
 @PatchMapping("/api/friends/requests/{id}") public Object respond(Actor a,@PathVariable int id,@RequestParam boolean accept){service.respond(a,id,accept);return Map.of("message",accept?"已接受好友邀請":"已拒絕好友邀請");}
 @DeleteMapping("/api/friends/{id}") public Object remove(Actor a,@PathVariable int id){service.remove(a,id);return Map.of("message","已解除好友關係");}
 @PostMapping("/api/chat/direct/{id}") public Object direct(Actor a,@PathVariable int id){return service.direct(a,id);}
 @GetMapping("/api/chat/rooms") public Object rooms(Actor a){return service.rooms(a);}
 @PostMapping("/api/chat/group") public Object group(Actor a,@RequestBody Group g){return service.group(a,g.roomName(),g.memberIds());}
 @PostMapping("/api/chat/rooms/{id}/invite") public Object invite(Actor a,@PathVariable int id,@RequestBody List<Integer> ids){service.invite(a,id,ids);return Map.of("message","已邀請好友加入群組");}
 @PatchMapping("/api/chat/rooms/{id}/leave") public Object leave(Actor a,@PathVariable int id){service.leave(a,id);return Map.of("message","已離開聊天室");}
 @GetMapping("/api/chat/rooms/{id}/history") public Object history(Actor a,@PathVariable int id,@RequestParam(defaultValue="9223372036854775807") long before){return service.history(a,id,before);}
 @PostMapping("/api/chat/rooms/{id}/messages") public Object send(Actor a,@PathVariable int id,@RequestBody Message m){return service.send(a,id,m.content());}
}
