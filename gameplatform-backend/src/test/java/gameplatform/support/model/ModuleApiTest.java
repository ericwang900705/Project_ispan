package gameplatform.support.model;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.jdbc.core.JdbcTemplate;
import gameplatform.support.controller.PublisherController;
import gameplatform.support.service.PublisherService;
import gameplatform.support.integration.*;
import java.util.Map;
class ModuleApiTest {
    @Test void publisherApiUsesHostActorAndMissingBridgeReturns503() throws Exception {
        var beans=new StaticListableBeanFactory();
        var service=new PublisherService(new JdbcTemplate()) {
            @Override public Map<String,Object> overview(Actor actor) { return Map.of("hostActorId",actor.id()); }
        };
        var mvc=MockMvcBuilders.standaloneSetup(new PublisherController(service))
            .setCustomArgumentResolvers(new ModuleActorResolver(beans.getBeanProvider(CurrentActorProvider.class)))
            .setControllerAdvice(new Problem.Handler()).build();
        var missing=mvc.perform(get("/api/publisher/overview").header("X-Role","ADMIN")).andReturn().getResponse();
        assertEquals(503,missing.getStatus());assertTrue(missing.getContentAsString().contains("尚未串接"));
        beans.addBean("host",(CurrentActorProvider)request->new Actor(77,"PUBLISHER","verified-host"));
        var connected=mvc.perform(get("/api/publisher/overview")).andReturn().getResponse();
        assertEquals(200,connected.getStatus());assertTrue(connected.getContentAsString().contains("77"));
        assertEquals(404,mvc.perform(get("/api/aki/publisher/overview")).andReturn().getResponse().getStatus());
    }
}
