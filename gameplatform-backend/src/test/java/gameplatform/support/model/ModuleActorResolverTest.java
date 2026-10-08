package gameplatform.support.model;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import gameplatform.support.integration.*;
class ModuleActorResolverTest {
    public void endpoint(@CurrentModuleActor Actor actor) {}
    MethodParameter parameter() throws Exception { return new MethodParameter(getClass().getMethod("endpoint",Actor.class),0); }
    @Test void missingBridgeFailsClosedEvenWithForgedIdentityHeaders() throws Exception {
        var resolver=new ModuleActorResolver(new StaticListableBeanFactory().getBeanProvider(CurrentActorProvider.class));
        var request=new MockHttpServletRequest();request.addHeader("X-User-Id","1");request.addHeader("X-Role","ADMIN");
        var problem=assertThrows(Problem.class,()->resolver.resolveArgument(parameter(),null,new ServletWebRequest(request),null));
        assertEquals(503,problem.status);assertTrue(problem.getMessage().contains("尚未串接"));
    }
    @Test void verifiedHostActorIsUsedAndInvalidOrAbsentActorsAreDenied() throws Exception {
        var beans=new StaticListableBeanFactory();
        beans.addBean("host",(CurrentActorProvider)request->new Actor(3,"PUBLISHER","verified"));
        var resolver=new ModuleActorResolver(beans.getBeanProvider(CurrentActorProvider.class));
        assertEquals(new Actor(3,"PUBLISHER","verified"),resolver.resolveArgument(parameter(),null,new ServletWebRequest(new MockHttpServletRequest()),null));
        beans.addBean("host",(CurrentActorProvider)request->null);
        assertEquals(401,assertThrows(Problem.class,()->resolver.resolveArgument(parameter(),null,new ServletWebRequest(new MockHttpServletRequest()),null)).status);
        beans.addBean("host",(CurrentActorProvider)request->new Actor(1,"ROOT","bad"));
        assertEquals(403,assertThrows(Problem.class,()->resolver.resolveArgument(parameter(),null,new ServletWebRequest(new MockHttpServletRequest()),null)).status);
    }
    @Test void moduleIsDisabledByDefaultWithoutRequiringAnyDatabaseOrLoginBeans() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(AkiModuleConfiguration.class);context.refresh();
            assertEquals(0,context.getBeansOfType(gameplatform.support.service.SupportService.class).size());
            assertEquals(0,context.getBeansOfType(gameplatform.support.controller.PublisherController.class).size());
        }
    }
}
