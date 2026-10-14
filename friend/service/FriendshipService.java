package gameplatform.friend.service;

import gameplatform.friend.entity.Friendship;
import gameplatform.friend.repository.FriendshipRepository;
import gameplatform.member.entity.Member;
import gameplatform.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final MemberRepository memberRepository;

    // 1. 發送好友邀請
    @Transactional
    public String sendFriendRequest(Integer requesterId, Integer receiverId) {
        if (requesterId.equals(receiverId)) {
            // 對應資料庫 CK_friendships_no_self_friend 約束條件
            throw new RuntimeException("不能發送好友邀請給自己");
        }

        Member requester = memberRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("找不到發送者"));
        Member receiver = memberRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("找不到接收者"));

        // 檢查是否已有關係存在 (包含已是好友、已拒絕、或已經在等待確認中)
        Optional<Friendship> existing = friendshipRepository.findFriendshipBetween(requester, receiver);
        if (existing.isPresent()) {
            throw new RuntimeException("你們之間已經存在好友關聯或已發送過邀請");
        }

        Friendship friendship = Friendship.builder()
                .requester(requester)
                .receiver(receiver)
                .friendStatus("PENDING") // 預設狀態為等待中
                .build();

        friendshipRepository.save(friendship);
        return "已成功發送好友邀請給 " + receiver.getUsername();
    }

    // 2. 獲取收到的待確認邀請清單 (我是接收方，且狀態為 PENDING)
    public List<Friendship> getPendingRequests(Integer memberId) {
        Member receiver = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));
        return friendshipRepository.findByReceiverAndFriendStatus(receiver, "PENDING");
    }

    // 3. 處理好友邀請 (同意或拒絕)
    @Transactional
    public String handleFriendRequest(Integer memberId, Integer friendshipId, boolean isAccepted) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("找不到該筆好友邀請"));

        // 安全性檢查：操作者必須是這筆邀請的「接收方」
        if (!friendship.getReceiver().getMemberId().equals(memberId)) {
            throw new RuntimeException("無權限處理這筆邀請");
        }

        if (!"PENDING".equals(friendship.getFriendStatus())) {
            throw new RuntimeException("此邀請已被處理過");
        }

        if (isAccepted) {
            friendship.setFriendStatus("ACCEPTED");
            friendshipRepository.save(friendship);
            return "已接受好友邀請！";
        } else {
            // 拒絕邀請，將狀態改為 REJECTED
            friendship.setFriendStatus("REJECTED");
            friendshipRepository.save(friendship);
            return "已拒絕好友邀請。";
        }
    }

    // 4. 獲取正式好友名單
    public List<Friendship> getFriendsList(Integer memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));
        // 利用 Repository 寫好的 JQL 撈出所有 ACCEPTED 的紀錄
        return friendshipRepository.findAllAcceptedFriends(member);
    }

    // 5. 解除好友關係
    @Transactional
    public String removeFriend(Integer memberId, Integer friendMemberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));
        Member friend = memberRepository.findById(friendMemberId)
                .orElseThrow(() -> new RuntimeException("找不到好友會員"));

        Friendship friendship = friendshipRepository.findFriendshipBetween(member, friend)
                .orElseThrow(() -> new RuntimeException("你們之間沒有好友紀錄"));

        if (!"ACCEPTED".equals(friendship.getFriendStatus())) {
            throw new RuntimeException("你們尚未成為正式好友，無法刪除");
        }

        // 實務上解除好友通常是直接將紀錄刪除，方便未來可以重新加回來
        friendshipRepository.delete(friendship);
        return "已成功解除好友關係。";
    }
}