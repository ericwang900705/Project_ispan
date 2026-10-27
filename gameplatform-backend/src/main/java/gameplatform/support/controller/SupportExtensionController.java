package gameplatform.support.controller;

import gameplatform.support.extension.SupportExtensionService;
import gameplatform.support.model.Actor;
import gameplatform.support.model.Problem;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** 客服關聯資料、私人備註、FAQ、常用回覆及操作紀錄 API。 */
@RestController
@RequestMapping("/api")
public class SupportExtensionController {

  private final SupportExtensionService service;
  private final JdbcTemplate db;

  public SupportExtensionController(
    SupportExtensionService service,
    JdbcTemplate db
  ) {
    this.service = service;
    this.db = db;
  }

  public record LinkInput(Integer orderId, Integer couponId) {}

  public record NoteInput(String content) {}

  @GetMapping("/{scope:rooms|tickets}/{id}/related-data")
  public Object related(
    Actor a,
    @PathVariable String scope,
    @PathVariable int id
  ) {
    return service.related(a, scope, id);
  }

  @GetMapping("/{scope:rooms|tickets}/{id}/{kind:order|coupon}-options")
  public Object options(
    Actor a,
    @PathVariable String scope,
    @PathVariable int id,
    @PathVariable String kind,
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.options(a, scope, id, kind + "s", q, page);
  }

  @PostMapping("/{scope:rooms|tickets}/{id}/{kind:order|coupon}-links")
  public Object link(
    Actor a,
    @PathVariable String scope,
    @PathVariable int id,
    @PathVariable String kind,
    @RequestBody LinkInput input
  ) {
    Integer target = kind.equals("order") ? input.orderId() : input.couponId();
    if (target == null) throw new Problem(400, "請選擇關聯資料");
    service.link(a, scope, id, kind, target, false);
    return Map.of("message", "已關聯");
  }

  @DeleteMapping(
    "/{scope:rooms|tickets}/{id}/{kind:order|coupon}-links/{target}"
  )
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void unlink(
    Actor a,
    @PathVariable String scope,
    @PathVariable int id,
    @PathVariable String kind,
    @PathVariable int target
  ) {
    service.link(a, scope, id, kind, target, true);
  }

  @GetMapping("/admin/support/{scope:rooms|tickets}/{id}/notes")
  public Object notes(
    Actor a,
    @PathVariable String scope,
    @PathVariable int id,
    @RequestParam(defaultValue = "9223372036854775807") long beforeId
  ) {
    return service.notes(a, scope, id, beforeId);
  }

  @PostMapping("/admin/support/{scope:rooms|tickets}/{id}/notes")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object note(
    Actor a,
    @PathVariable String scope,
    @PathVariable int id,
    @RequestBody NoteInput input
  ) {
    return service.addNote(a, scope, id, input.content());
  }

  @GetMapping("/admin/support/{scope:rooms|tickets}/{id}/audit-logs")
  public Object audit(
    Actor a,
    @PathVariable String scope,
    @PathVariable int id,
    @RequestParam(defaultValue = "9223372036854775807") long beforeId
  ) {
    return service.logs(a, scope, id, beforeId, true);
  }

  @GetMapping("/{scope:rooms|tickets}/{id}/timeline")
  public Object timeline(
    Actor a,
    @PathVariable String scope,
    @PathVariable int id,
    @RequestParam(defaultValue = "9223372036854775807") long beforeId
  ) {
    return service.logs(a, scope, id, beforeId, false);
  }

  @GetMapping("/support/reply-templates")
  public Object replies(
    Actor a,
    @RequestParam(defaultValue = "") String q,
    @RequestParam(required = false) Integer categoryId,
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.content(
      a,
      "reply-templates",
      false,
      q,
      null,
      categoryId,
      page
    );
  }

  @GetMapping("/public/faqs")
  public Object faqs(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(required = false) Integer categoryId,
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.content(null, "faqs", false, q, null, categoryId, page);
  }

  @GetMapping("/public/faqs/{id}")
  public Object faq(@PathVariable long id) {
    var rows = db.queryForList(
      "SELECT faq_id AS id,question AS title,answer AS content,category_id AS [categoryId] FROM dbo.support_faqs WHERE faq_id=? AND status='PUBLISHED'",
      id
    );
    if (rows.isEmpty()) throw new Problem(404, "找不到此常見問題");
    return rows.getFirst();
  }

  @GetMapping("/public/faq-categories")
  public Object faqCategories() {
    return db.queryForList(
      "SELECT c.category_id AS id,c.category_name AS name FROM dbo.customer_service_categories c WHERE EXISTS(SELECT 1 FROM dbo.support_faqs f WHERE f.category_id=c.category_id AND f.status='PUBLISHED') ORDER BY c.category_id"
    );
  }

  @GetMapping("/admin/support/{kind:faqs|reply-templates}")
  public Object management(
    Actor a,
    @PathVariable String kind,
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "") String status,
    @RequestParam(required = false) Integer categoryId,
    @RequestParam(defaultValue = "0") int page
  ) {
    return service.content(a, kind, true, q, status, categoryId, page);
  }

  @PostMapping("/admin/support/{kind:faqs|reply-templates}")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object create(
    Actor a,
    @PathVariable String kind,
    @RequestBody SupportExtensionService.ContentInput input
  ) {
    return service.saveContent(a, kind, null, input);
  }

  @PutMapping("/admin/support/{kind:faqs|reply-templates}/{id}")
  public Object update(
    Actor a,
    @PathVariable String kind,
    @PathVariable long id,
    @RequestBody SupportExtensionService.ContentInput input
  ) {
    return service.saveContent(a, kind, id, input);
  }
}
