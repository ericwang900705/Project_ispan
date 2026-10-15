package gameplatform.support.config;

import gameplatform.member.util.JwtTokenProvider;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import io.jsonwebtoken.Claims;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.web.bind.support.WebDataBinderFactory;

/** 使用組員 JwtTokenProvider 驗證簽章與效期，只轉接客服需要的目前身分。 */
@Component
public class SupportActorResolver implements HandlerMethodArgumentResolver {
    private final JwtTokenProvider tokens;
    private final JdbcTemplate db;
    private final String staffRole;
    @Value("${app.support.admin-role-code:ROLE_SUPER_ADMIN}")
    private String adminRole = "ROLE_SUPER_ADMIN";

    public SupportActorResolver(JwtTokenProvider tokens, JdbcTemplate db,
            @Value("${app.support.staff-role-code:ROLE_SUPPORT}") String staffRole) {
        this.tokens = tokens;
        this.db = db;
        this.staffRole = staffRole;
    }

    @Override public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Actor.class;
    }

    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer model,
            NativeWebRequest request, WebDataBinderFactory binder) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw unauthorized();
        return resolveToken(header.substring(7));
    }

    public Actor resolveToken(String token) {
        Claims claims;
        String type;
        String tokenRole;
        try {
            if (!tokens.validateToken(token)) throw new IllegalArgumentException();
            claims = tokens.getClaimsFromToken(token);
            type = claims.get("type", String.class);
            tokenRole = claims.get("role", String.class);
            if (claims.getSubject() == null || claims.getSubject().isBlank()
                    || claims.getExpiration() == null) throw new IllegalArgumentException();
        } catch (RuntimeException error) {
            throw unauthorized();
        }
        String account = claims.getSubject();
        if ("ADMIN".equals(type)) {
            if (claims.containsKey("memberId")) throw unauthorized();
            int id = identityId(claims.get("adminId"));
            var actors = db.query(
                "SELECT a.admin_account,r.role_code FROM dbo.admin a JOIN dbo.admin_roles r "
                    + "ON r.admin_role_id=a.admin_role_id WHERE a.admin_id=? AND a.admin_account=? "
                    + "AND a.status='ACTIVE' AND r.role_code=?",
                (rs, n) -> new Actor(id, supportRole(rs.getString("role_code")), rs.getString("admin_account"), ""),
                id, account, tokenRole);
            if (actors.size() != 1) throw unauthorized();
            return actors.get(0);
        }
        if (type != null || claims.containsKey("adminId") || tokenRole == null
                || !Set.of("ROLE_USER", "ROLE_MEMBER", "ROLE_PUBLISHER").contains(tokenRole)) {
            throw unauthorized();
        }
        int id = identityId(claims.get("memberId"));
        var actors = db.query(
            "SELECT username,is_publisher FROM dbo.members WHERE member_id=? AND username=? "
                + "AND (locked_until IS NULL OR locked_until<=CURRENT_TIMESTAMP)",
            (rs, n) -> new Actor(id, rs.getBoolean("is_publisher") ? "PUBLISHER" : "MEMBER", rs.getString("username"), ""),
            id, account);
        if (actors.size() != 1) throw unauthorized();
        Actor actor = actors.get(0);
        if (!Objects.equals("ROLE_PUBLISHER".equals(tokenRole), "PUBLISHER".equals(actor.role()))) {
            throw unauthorized();
        }
        return actor;
    }

    private static int identityId(Object value) {
        if (!(value instanceof Integer || value instanceof Long)) throw unauthorized();
        long id = ((Number) value).longValue();
        if (id <= 0 || id > Integer.MAX_VALUE) throw unauthorized();
        return (int) id;
    }

    private String supportRole(String code) {
        if (adminRole.equals(code)) return "ADMIN";
        if (staffRole.equals(code)) return "SUPPORT";
        throw Problem.denied();
    }

    private static Problem unauthorized() {
        return new Problem(401, "登入已失效或身分不符，請重新登入");
    }
}
