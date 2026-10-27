package gameplatform.support.controller;

import gameplatform.support.model.Actor;
import gameplatform.support.service.NotificationService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping
  public Object list(Actor a) {
    return service.list(a);
  }

  @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  @PostMapping("/{id}/read")
  public void read(Actor a, @PathVariable long id) {
    service.read(a, id);
  }

  @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  @PostMapping("/read-all")
  public void readAll(Actor a) {
    service.readAll(a);
  }
}
