package gameplatform.support.model;

/** Identity supplied by the host after authentication. IDs refer to members/admin respectively. */
public record Actor(int id, String role, String username) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
    public boolean isMember() { return "MEMBER".equals(role); }
    public boolean canSupport() { return isAdmin() || "SUPPORT".equals(role); }
    public boolean canReview() { return isAdmin() || "REVIEWER".equals(role); }
    public String senderType() { return isMember() ? "MEMBER" : "ADMIN"; }
}
