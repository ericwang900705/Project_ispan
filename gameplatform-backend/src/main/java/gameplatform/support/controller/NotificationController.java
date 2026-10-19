
package gameplatform.support.controller;

import gameplatform.support.model.Actor;
import gameplatform.support.service.NotificationService;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  // 查詢通知清單
  @GetMapping
  public Object list(Actor a) {
    return service.list(a);
  }

  // 單筆通知標記已讀
  @PostMapping("/{id}/read")
  public Map<String, Boolean> read(
      Actor a,
      @PathVariable long id) {

    service.read(a, id);

    return Map.of("success", true);
  }

  // 全部通知標記已讀
  @PostMapping("/read-all")
  public Map<String, Boolean> readAll(Actor a) {

    service.readAll(a);

    return Map.of("success", true);
  }
}
