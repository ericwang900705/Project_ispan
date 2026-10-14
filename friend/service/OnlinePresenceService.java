package gameplatform.friend.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OnlinePresenceService {

    // Key: memberId, Value: 該會員目前開啟的所有 WebSocket Session ID 集合
    private final Map<Integer, Set<String>> onlineUsers = new ConcurrentHashMap<>();

    // Key: sessionId, Value: memberId (用來在斷線時快速反查是哪位會員斷線)
    private final Map<String, Integer> sessionUserMap = new ConcurrentHashMap<>();

    // 紀錄使用者上線 (回傳 true 代表他是從「離線」剛變成「上線」)
    public boolean userConnected(Integer memberId, String sessionId) {
        sessionUserMap.put(sessionId, memberId);
        Set<String> sessions = onlineUsers.computeIfAbsent(memberId, k -> ConcurrentHashMap.newKeySet());
        boolean wasOffline = sessions.isEmpty();
        sessions.add(sessionId);
        return wasOffline;
    }

    // 紀錄使用者斷線 (回傳離線的 memberId；若他還有其他分頁開著，則回傳 null)
    public Integer userDisconnected(String sessionId) {
        Integer memberId = sessionUserMap.remove(sessionId);
        if (memberId != null) {
            Set<String> sessions = onlineUsers.get(memberId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    onlineUsers.remove(memberId);
                    return memberId; // 所有分頁都關了，真正離線
                }
            }
        }
        return null;
    }

    // 查詢某位會員是否在線上
    public boolean isOnline(Integer memberId) {
        Set<String> sessions = onlineUsers.get(memberId);
        return sessions != null && !sessions.isEmpty();
    }

    // 取得目前所有在線上的會員 ID 集合
    public Set<Integer> getAllOnlineMemberIds() {
        return onlineUsers.keySet();
    }
}
