package gameplatform.integration;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Register all active controllers together; isolated service tests cannot detect mapping collisions. */
class ControllerMappingTests {
 private static <T> void register(GenericWebApplicationContext context,Class<T> type) {context.registerBean(type.getName(),type,()->mock(type));}
 @Test void allControllerMappingsRegisterWithoutConflicts() throws Exception {
  var scanner=new ClassPathScanningCandidateComponentProvider(false);
  scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
  try(var context=new GenericWebApplicationContext(new MockServletContext())) {
   for(var bean:scanner.findCandidateComponents("gameplatform")) {
    Class<?> type=Class.forName(bean.getBeanClassName());
    register(context,type);
   }
   context.refresh();
   var mappings=new RequestMappingHandlerMapping();mappings.setApplicationContext(context);
   assertDoesNotThrow(mappings::afterPropertiesSet);
   assertTrue(mappings.getHandlerMethods().size()>100);
  }
 }
}
