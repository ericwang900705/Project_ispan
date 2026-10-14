package gameplatform.friend.controller;

import gameplatform.friend.dto.FriendshipDTO;
import gameplatform.friend.entity.Friendship;
import gameplatform.friend.service.FriendshipService;
import gameplatform.friend.service.OnlinePresenceService;
import gameplatform.member.entity.Member;
import gameplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final MemberRepository memberRepository;
    private final OnlinePresenceService presenceService;

    private Integer getCurrentMemberId(Authentication authentication) {
        String username = authentication.getName(); // 這裡現在拿到的是 "gamer01"
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("找不到當前登入的帳號: " + username));
        return member.getMemberId();
    }

    // 1. 發送好友邀請
    @PostMapping("/request/{receiverId}")
    public ResponseEntity<String> sendRequest(@PathVariable Integer receiverId, Authentication authentication) {
        Integer currentUserId = getCurrentMemberId(authentication);
        String result = friendshipService.sendFriendRequest(currentUserId, receiverId);
        return ResponseEntity.ok(result);
    }

    // 2. 獲取收到的待確認邀請清單
    @GetMapping("/requests/pending")
    public ResponseEntity<List<FriendshipDTO>> getPendingRequests(Authentication authentication) {
        Integer currentUserId = getCurrentMemberId(authentication);
        List<Friendship> list = friendshipService.getPendingRequests(currentUserId);

        // 將 Entity 轉換為安全的 DTO (取出發送邀請的人的資料)
        List<FriendshipDTO> dtoList = list.stream()
                .map(f -> new FriendshipDTO(
                        f.getFriendshipId(),
                        f.getRequester().getMemberId(),
                        f.getRequester().getUsername(),
                        f.getRequester().getAvatarUrl(),
                        f.getFriendStatus(),
                        f.getCreatedAt(),
                        presenceService.isOnline(f.getRequester().getMemberId()) // 判斷發送者是否在線上
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtoList);
    }

    // 3. 處理好友邀請 (同意或拒絕)
    @PatchMapping("/requests/{friendshipId}")
    public ResponseEntity<String> handleRequest(@PathVariable Integer friendshipId, @RequestParam boolean accept,
            Authentication authentication) {
        Integer currentUserId = getCurrentMemberId(authentication);
        String result = friendshipService.handleFriendRequest(currentUserId, friendshipId, accept);
        return ResponseEntity.ok(result);
    }

    // 4. 獲取正式好友名單
    @GetMapping("/list")
    public ResponseEntity<List<FriendshipDTO>> getFriendsList(Authentication authentication) {
        Integer currentUserId = getCurrentMemberId(authentication);
        List<Friendship> list = friendshipService.getFriendsList(currentUserId);

        // 將 Entity 轉換為安全的 DTO (判斷誰是朋友，把朋友的資料塞進 DTO)
        List<FriendshipDTO> dtoList = list.stream().map(f -> {
            var friend = f.getRequester().getMemberId().equals(currentUserId) ? f.getReceiver() : f.getRequester();
            return new FriendshipDTO(
                    f.getFriendshipId(),
                    friend.getMemberId(),
                    friend.getUsername(),
                    friend.getAvatarUrl(),
                    f.getFriendStatus(),
                    f.getCreatedAt(),
                    presenceService.isOnline(friend.getMemberId())// 判斷朋友是否在線上
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtoList);
    }

    // 5. 解除好友關係
    @DeleteMapping("/{friendMemberId}")
    public ResponseEntity<String> removeFriend(@PathVariable Integer friendMemberId, Authentication authentication) {
        Integer currentUserId = getCurrentMemberId(authentication);
        String result = friendshipService.removeFriend(currentUserId, friendMemberId);
        return ResponseEntity.ok(result);
    }
}
