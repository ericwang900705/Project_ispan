
package gameplatform.support.config;

import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;

import org.springframework.core.MethodParameter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Objects;

@Component
public class SupportActorResolver implements HandlerMethodArgumentResolver {

    private final JdbcTemplate db;

    public SupportActorResolver(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Actor.class;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer model,
            NativeWebRequest request,
            WebDataBinderFactory binder) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Actor actor)) {
            throw unauthorized();
        }

        return verifyActor(actor);
    }

    /**
     * 重新查詢資料庫，確認登入者的身分仍然有效。
     *
     * 管理員：
     * ADMIN、REVIEWER、SUPPORT、STAFF
     *
     * 前台會員：
     * MEMBER、PUBLISHER
     *
     * 注意：
     * STAFF 的功能權限由 SecurityConfig 載入，
     * 這裡只負責驗證帳號身分，不授予功能權限。
     */
    public Actor verifyActor(Actor actor) {

        if (actor.id() <= 0
                || actor.username() == null
                || actor.username().isBlank()) {
            throw unauthorized();
        }

        return switch (actor.role()) {

            // 新增 STAFF，沿用原本管理員驗證方式。
            case "ADMIN", "REVIEWER", "SUPPORT", "STAFF" -> verifyAdmin(actor);

            case "MEMBER", "PUBLISHER" -> verifyMember(actor);

            default -> throw unauthorized();
        };
    }

    /**
     * 驗證管理員身分。
     *
     * 1. 帳號存在。
     * 2. 帳號狀態為 ACTIVE。
     * 3. 帳號名稱一致。
     * 4. 資料庫角色與登入時的角色一致。
     */
    private Actor verifyAdmin(Actor actor) {

        String sql = """
                SELECT a.admin_account, r.role_code
                FROM dbo.admin a
                JOIN dbo.admin_roles r
                    ON r.admin_role_id = a.admin_role_id
                WHERE a.admin_id = ?
                  AND a.admin_account = ?
                  AND a.status = 'ACTIVE'
                """;

        List<Actor> actors = db.query(
                sql,
                (rs, rowNum) -> new Actor(
                        actor.id(),
                        rs.getString("role_code"),
                        rs.getString("admin_account"),
                        ""),
                actor.id(),
                actor.username());

        if (actors.size() != 1) {
            throw unauthorized();
        }

        Actor current = actors.get(0);

        if (!Objects.equals(actor.role(), current.role())) {
            throw unauthorized();
        }

        return current;
    }

    /**
     * 驗證會員與發行商身分。
     *
     * 1. 會員存在。
     * 2. 會員沒有被停權。
     * 3. 發行商身分與 JWT 一致。
     */
    private Actor verifyMember(Actor actor) {

        String sql = """
                SELECT username, is_publisher
                FROM dbo.members
                WHERE member_id = ?
                  AND username = ?
                  AND (
                      locked_until IS NULL
                      OR locked_until <= CURRENT_TIMESTAMP
                  )
                """;

        List<Actor> actors = db.query(
                sql,
                (rs, rowNum) -> new Actor(
                        actor.id(),
                        rs.getBoolean("is_publisher")
                                ? "PUBLISHER"
                                : "MEMBER",
                        rs.getString("username"),
                        ""),
                actor.id(),
                actor.username());

        if (actors.size() != 1) {
            throw unauthorized();
        }

        Actor current = actors.get(0);

        if (!Objects.equals(actor.role(), current.role())) {
            throw unauthorized();
        }

        return current;
    }

    private static Problem unauthorized() {
        return new Problem(
                401,
                "登入已失效或身分不符，請重新登入");
    }
}
