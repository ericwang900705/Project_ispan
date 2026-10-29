package gameplatform.friend;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;

/**
 * Adapted from member/friend 46f2afed: existing HERO JWT and 1013 SQL Server
 * tables.
 */
@Service
public class FriendService {
    private final JdbcTemplate db;
    private final FriendSocket socket;

    public FriendService(JdbcTemplate db, FriendSocket socket) {
        this.db = db;
        this.socket = socket;
    }

    public static int member(Actor a) {
        if (a == null || !Set.of("MEMBER", "PUBLISHER").contains(a.role()))
            throw new Problem(403, "只有會員可以使用好友服務");
        return a.id();
    }

    // SQL Server transaction-owned lock serializes relationship / membership
    // changes and sends.
    // This avoids reciprocal invitations, duplicate direct rooms and leave/send
    // races across app instances.
    private void lock() {
        // 先取用 Spring 管理的 JDBC 連線；綁定可能發生在首次取用時。
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || db.getDataSource() == null)
            throw new Problem(409, "好友服務交易尚未啟動，請稍後重試");
        db.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            if (connection.getAutoCommit()) throw new Problem(409,"好友服務交易連線尚未啟動，請稍後重試");
            return null;
        });
        Integer result = db.queryForObject(
                "IF @@TRANCOUNT=0 BEGIN TRANSACTION; DECLARE @r INT; EXEC @r=sys.sp_getapplock @Resource='HERO_FRIEND_GRAPH', @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000; SELECT @r",
                Integer.class);
        if (result == null || result < 0)
            throw new Problem(409, "好友服務忙碌，請稍後重試（鎖定代碼：" + result + "）");
    }

    private void active(int id) {
        if (id <= 0 || db.queryForObject(
                "SELECT COUNT(*) FROM members m WHERE m.member_id=? AND (m.locked_until IS NULL OR m.locked_until<=SYSDATETIME()) AND (EXISTS(SELECT 1 FROM dbo.member_oauth_accounts oa WHERE oa.member_id=m.member_id AND oa.provider='GOOGLE') OR (EXISTS(SELECT 1 FROM password_history h WHERE h.member_id=m.member_id) AND (NOT EXISTS(SELECT 1 FROM member_verifications v WHERE v.email=m.email) OR EXISTS(SELECT 1 FROM member_verifications v WHERE v.email=m.email AND v.is_verified=1))))",
                Integer.class, id) == 0)
            throw new Problem(404, "找不到已啟用的會員");
    }

    private void friends(int a, int b) {
        if (a == b || db.queryForObject(
                "SELECT COUNT(*) FROM friendships WHERE friend_status='ACCEPTED' AND ((requester_member_id=? AND receiver_member_id=?) OR (requester_member_id=? AND receiver_member_id=?))",
                Integer.class, a, b, b, a) == 0)
            throw new Problem(403, "請先成為好友");
        active(b);
    }

    private List<Integer> participants(int room) {
        return db.queryForList("SELECT member_id FROM friend_room_members WHERE friend_room_id=? AND left_at IS NULL",
                Integer.class, room);
    }

    private Map<String, Object> roomAccess(int id, int room) {
        var rows = db.queryForList(
                "SELECT r.friend_room_id AS roomId,r.room_type AS roomType,r.room_name AS roomName,r.creator_member_id AS creatorId FROM friend_rooms r JOIN friend_room_members m ON m.friend_room_id=r.friend_room_id WHERE r.friend_room_id=? AND m.member_id=? AND m.left_at IS NULL",
                room, id);
        if (rows.isEmpty())
            throw new Problem(403, "你不是這個聊天室的有效成員");
        var row = rows.getFirst();
        if ("DIRECT".equals(row.get("roomType"))) {
            var peers = participants(room).stream().filter(x -> x != id).toList();
            if (peers.size() != 1)
                throw new Problem(403, "對方已離開聊天室，請重新建立對話");
            friends(id, peers.getFirst());
        }
        return row;
    }

    private int createRoom(int creator, String type, String name) {
        return db.queryForObject(
                "INSERT INTO friend_rooms(room_type,room_name,creator_member_id) OUTPUT INSERTED.friend_room_id VALUES(?,?,?)",
                Integer.class, type, name, creator);
    }

    private void join(int room, int id) {
        if (db.update(
                "UPDATE friend_room_members SET left_at=NULL,joined_at=SYSDATETIME() WHERE friend_room_id=? AND member_id=? AND left_at IS NOT NULL",
                room, id) > 0)
            return;
        if (db.queryForObject("SELECT COUNT(*) FROM friend_room_members WHERE friend_room_id=? AND member_id=?",
                Integer.class, room, id) == 0)
            db.update("INSERT INTO friend_room_members(friend_room_id,member_id) VALUES(?,?)", room, id);
    }

    private void notifyMembers(Collection<Integer> ids, String type, int room) {
        var copy = Set.copyOf(ids);
        Runnable emit = () -> socket.publish(copy, type, room);
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emit.run();
                }
            });
        else
            emit.run();
    }

    public List<Map<String, Object>> search(Actor actor, String username) {
        int id = member(actor);
        String q = username == null ? "" : username.trim();
        if (q.length() < 2 || q.length() > 30)
            throw new Problem(400, "請輸入 2～30 字元的帳號");
        // Literal prefix search; never exposes email, national ID or private member
        // data.
        q = q.replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![");
        return db.queryForList(
                "SELECT TOP (20) member_id AS memberId,username,avatar_url AS avatarUrl FROM members m WHERE m.member_id<>? AND (m.locked_until IS NULL OR m.locked_until<=SYSDATETIME()) AND (EXISTS(SELECT 1 FROM dbo.member_oauth_accounts oa WHERE oa.member_id=m.member_id AND oa.provider='GOOGLE') OR (EXISTS(SELECT 1 FROM password_history h WHERE h.member_id=m.member_id) AND (NOT EXISTS(SELECT 1 FROM member_verifications v WHERE v.email=m.email) OR EXISTS(SELECT 1 FROM member_verifications v WHERE v.email=m.email AND v.is_verified=1)))) AND username LIKE ? ESCAPE '!' ORDER BY username",
                id, q + "%");
    }

    public List<Map<String, Object>> list(Actor actor, boolean pending) {
        int id = member(actor);
        var rows = db.queryForList(
                "SELECT f.friendship_id AS friendshipId,m.member_id AS memberId,m.username,m.avatar_url AS avatarUrl,f.friend_status AS friendStatus FROM friendships f JOIN members m ON m.member_id=CASE WHEN f.requester_member_id=? THEN f.receiver_member_id ELSE f.requester_member_id END WHERE "
                        + (pending ? "f.receiver_member_id=? AND f.friend_status='PENDING'"
                                : "(f.requester_member_id=? OR f.receiver_member_id=?) AND f.friend_status='ACCEPTED'")
                        + " AND (m.locked_until IS NULL OR m.locked_until<=SYSDATETIME()) AND (EXISTS(SELECT 1 FROM dbo.member_oauth_accounts oa WHERE oa.member_id=m.member_id AND oa.provider='GOOGLE') OR (EXISTS(SELECT 1 FROM password_history h WHERE h.member_id=m.member_id) AND (NOT EXISTS(SELECT 1 FROM member_verifications v WHERE v.email=m.email) OR EXISTS(SELECT 1 FROM member_verifications v WHERE v.email=m.email AND v.is_verified=1)))) ORDER BY f.friendship_id DESC",
                pending ? new Object[] { id, id } : new Object[] { id, id, id });
        for (var row : rows)
            row.put("online", socket.online(((Number) row.get("memberId")).intValue()));
        return rows;
    }

    @Transactional
    public void request(Actor actor, int receiver) {
        int id = member(actor);
        if (id == receiver)
            throw new Problem(400, "不能加自己為好友");
        lock();
        active(id);
        active(receiver);
        var rows = db.queryForList(
                "SELECT friendship_id,friend_status FROM friendships WHERE (requester_member_id=? AND receiver_member_id=?) OR (requester_member_id=? AND receiver_member_id=?)",
                id, receiver, receiver, id);
        if (rows.stream().anyMatch(x -> !"REJECTED".equals(x.get("friend_status"))))
            throw new Problem(409, "已有邀請、好友關係或封鎖紀錄");
        db.update(
                "DELETE FROM friendships WHERE ((requester_member_id=? AND receiver_member_id=?) OR (requester_member_id=? AND receiver_member_id=?)) AND friend_status='REJECTED'",
                id, receiver, receiver, id);
        db.update("INSERT INTO friendships(requester_member_id,receiver_member_id,friend_status) VALUES(?,?,'PENDING')",
                id, receiver);
        notifyMembers(List.of(id, receiver), "friends", 0);
    }

    @Transactional
    public void respond(Actor actor, int request, boolean accept) {
        int id = member(actor);
        lock();
        active(id);
        var rows = db.queryForList(
                "SELECT requester_member_id FROM friendships WHERE friendship_id=? AND receiver_member_id=? AND friend_status='PENDING'",
                request, id);
        if (rows.isEmpty())
            throw new Problem(404, "找不到可處理的好友邀請");
        int sender = ((Number) rows.getFirst().get("requester_member_id")).intValue();
        active(sender);
        db.update("UPDATE friendships SET friend_status=?,updated_at=SYSDATETIME() WHERE friendship_id=?",
                accept ? "ACCEPTED" : "REJECTED", request);
        notifyMembers(List.of(id, sender), "friends", 0);
    }

    @Transactional
    public void remove(Actor actor, int peer) {
        int id = member(actor);
        lock();
        friends(id, peer);
        db.update(
                "DELETE FROM friendships WHERE friend_status='ACCEPTED' AND ((requester_member_id=? AND receiver_member_id=?) OR (requester_member_id=? AND receiver_member_id=?))",
                id, peer, peer, id);
        // Both parties leave direct rooms; old messages are retained, but cannot be
        // read after removal.
        db.update(
                "UPDATE m SET left_at=SYSDATETIME() FROM friend_room_members m JOIN friend_rooms r ON r.friend_room_id=m.friend_room_id WHERE r.room_type='DIRECT' AND m.left_at IS NULL AND EXISTS(SELECT 1 FROM friend_room_members a WHERE a.friend_room_id=r.friend_room_id AND a.member_id=?) AND EXISTS(SELECT 1 FROM friend_room_members b WHERE b.friend_room_id=r.friend_room_id AND b.member_id=?)",
                id, peer);
        notifyMembers(List.of(id, peer), "friends", 0);
    }

    @Transactional
    public Map<String, Object> direct(Actor actor, int peer) {
        int id = member(actor);
        lock();
        active(id);
        friends(id, peer);
        var rooms = db.queryForList(
                "SELECT r.friend_room_id FROM friend_rooms r WHERE r.room_type='DIRECT' AND EXISTS(SELECT 1 FROM friend_room_members a WHERE a.friend_room_id=r.friend_room_id AND a.member_id=? AND a.left_at IS NULL) AND EXISTS(SELECT 1 FROM friend_room_members b WHERE b.friend_room_id=r.friend_room_id AND b.member_id=? AND b.left_at IS NULL) AND (SELECT COUNT(*) FROM friend_room_members c WHERE c.friend_room_id=r.friend_room_id)=2 ORDER BY r.friend_room_id",
                Integer.class, id, peer);
        int room = rooms.isEmpty() ? createRoom(id, "DIRECT", null) : rooms.getFirst();
        join(room, id);
        join(room, peer);
        notifyMembers(List.of(id, peer), "rooms", room);
        return roomAccess(id, room);
    }

    private List<Integer> targets(int id, List<Integer> targets) {
        if (targets == null || targets.isEmpty() || targets.size() > 19 || targets.contains(null))
            throw new Problem(400, "請選擇 1～19 位好友");
        var ids = targets.stream().distinct().toList();
        for (int peer : ids)
            friends(id, peer);
        return ids;
    }

    @Transactional
    public Map<String, Object> group(Actor actor, String name, List<Integer> targets) {
        int id = member(actor);
        lock();
        active(id);
        var peers = targets(id, targets);
        if (name == null || name.isBlank() || name.trim().length() > 100)
            throw new Problem(400, "請輸入 1～100 字的群組名稱");
        int room = createRoom(id, "GROUP", name.trim());
        join(room, id);
        for (int peer : peers)
            join(room, peer);
        notifyMembers(participants(room), "rooms", room);
        return roomAccess(id, room);
    }

    @Transactional
    public void invite(Actor actor, int room, List<Integer> targets) {
        int id = member(actor);
        lock();
        var current = roomAccess(id, room);
        if (!"GROUP".equals(current.get("roomType")))
            throw new Problem(400, "只能邀請成員加入群組");
        var peers = targets(id, targets);
        var all = new HashSet<>(participants(room));
        all.addAll(peers);
        if (all.size() > 20)
            throw new Problem(400, "群組最多 20 人");
        for (int peer : peers)
            join(room, peer);
        notifyMembers(all, "rooms", room);
    }

    @Transactional
    public void leave(Actor actor, int room) {
        int id = member(actor);
        lock();
        roomAccess(id, room);
        var before = participants(room);
        db.update(
                "UPDATE friend_room_members SET left_at=SYSDATETIME() WHERE friend_room_id=? AND member_id=? AND left_at IS NULL",
                room, id);
        notifyMembers(before, "rooms", room);
    }

    public List<Map<String, Object>> rooms(Actor actor) {
        int id = member(actor);
        var rows = db.queryForList(
                "SELECT r.friend_room_id AS roomId,r.room_type AS roomType,r.room_name AS roomName,r.creator_member_id AS creatorId FROM friend_rooms r JOIN friend_room_members m ON m.friend_room_id=r.friend_room_id WHERE m.member_id=? AND m.left_at IS NULL ORDER BY r.friend_room_id DESC",
                id);
        for (var row : rows) {
            int room = ((Number) row.get("roomId")).intValue();
            row.put("members", db.queryForList(
                    "SELECT m.member_id AS memberId,m.username,m.avatar_url AS avatarUrl FROM friend_room_members x JOIN members m ON m.member_id=x.member_id WHERE x.friend_room_id=? AND x.left_at IS NULL",
                    room));
        }
        rows.removeIf(r -> "DIRECT".equals(r.get("roomType")) && ((List<?>) r.get("members")).size() != 2);
        return rows;
    }

    public List<Map<String, Object>> history(Actor actor, int room, long before) {
        int id = member(actor);
        roomAccess(id, room);
        return db.queryForList(
                "SELECT TOP (50) x.friend_message_id AS messageId,x.member_id AS senderId,m.username AS senderName,m.avatar_url AS avatarUrl,x.content,x.message_type AS messageType,x.created_at AS createdAt FROM friend_messages x JOIN members m ON m.member_id=x.member_id WHERE x.friend_room_id=? AND x.friend_message_id<? AND x.created_at>=(SELECT joined_at FROM friend_room_members WHERE friend_room_id=? AND member_id=?) ORDER BY x.friend_message_id DESC",
                room, before, room, id).reversed();
    }

    @Transactional
    public Map<String, Object> send(Actor actor, int room, String text) {
        int id = member(actor);
        if (text == null || text.isBlank() || text.length() > 2000)
            throw new Problem(400, "訊息須為 1～2000 字");
        lock();
        active(id);
        roomAccess(id, room);
        Long message = db.queryForObject(
                "INSERT INTO friend_messages(friend_room_id,member_id,message_type,content) OUTPUT INSERTED.friend_message_id VALUES(?,?,'TEXT',?)",
                Long.class, room, id, text.trim());
        notifyMembers(participants(room), "message", room);
        return Map.of("messageId", Objects.requireNonNull(message));
    }
}
