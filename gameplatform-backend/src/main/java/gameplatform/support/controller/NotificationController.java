package gameplatform.support.controller;
import gameplatform.support.model.Actor;
import gameplatform.support.service.NotificationService;

import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService service;
  public NotificationController(NotificationService service){this.service=service;}
  @GetMapping public Object list(Actor a){return service.list(a);}
  @PostMapping("/{id}/read") public void read(Actor a,@PathVariable long id){service.read(a,id);}
  @PostMapping("/read-all") public void readAll(Actor a){service.readAll(a);}
}
