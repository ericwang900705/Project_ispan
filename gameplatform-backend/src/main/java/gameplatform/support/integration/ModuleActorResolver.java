package gameplatform.support.integration;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

public final class ModuleActorResolver implements HandlerMethodArgumentResolver {
    private final ObjectProvider<CurrentActorProvider> providers;
    public ModuleActorResolver(ObjectProvider<CurrentActorProvider> providers) { this.providers=providers; }
    @Override public boolean supportsParameter(MethodParameter p) { return p.hasParameterAnnotation(CurrentModuleActor.class) && p.getParameterType()==Actor.class; }
    @Override public Object resolveArgument(MethodParameter p,ModelAndViewContainer container,NativeWebRequest request,WebDataBinderFactory binder) {
        var provider=providers.getIfAvailable();
        if(provider==null) throw new Problem(503,"尚未串接：主系統登入身分");
        Actor actor=provider.currentActor(request.getNativeRequest(HttpServletRequest.class));
        if(actor==null) throw new Problem(401,"主系統尚未提供有效登入身分");
        if(actor.id()<=0 || actor.username()==null || actor.role()==null || !java.util.Set.of("MEMBER","PUBLISHER","ADMIN","SUPPORT","REVIEWER").contains(actor.role())) throw new Problem(403,"無效的模組身分");
        return actor;
    }
}
