package gameplatform.support.controller;

import static gameplatform.support.model.Models.*;

import java.util.*;


import jakarta.validation.Valid;

import gameplatform.support.integration.CurrentModuleActor;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import gameplatform.support.model.Actor;
import gameplatform.support.model.EventHub;
import gameplatform.support.model.Problem;
import gameplatform.support.service.SupportService;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="aki.modules.enabled",havingValue="true")
@RestController
@RequestMapping("/api/support")
public class SupportController {
    private final SupportService service;
    public SupportController(SupportService service) { this.service = service; }

    @GetMapping("/admin/workload")
    public Object workload(@CurrentModuleActor Actor a) {
        return service.workload(a);
    }

    @GetMapping("/admin/queue")
    public Object queue(@CurrentModuleActor Actor a, @RequestParam(defaultValue = "rooms") String kind,
            @RequestParam(defaultValue = "unassigned") String scope,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        return service.workQueue(a, kind, scope, q, page);
    }

    @GetMapping("/categories")
    public Object categories(@CurrentModuleActor Actor actor) {
        return service.categories();
    }

    @GetMapping("/games")
    public Object games(@CurrentModuleActor Actor actor, @RequestParam(defaultValue = "") String q) {
        if (q.length() > 100)
            throw new Problem(400, "搜尋字數過長");
        return service.games(q);
    }

    @GetMapping("/member/rooms/latest")
    public Object latest(@CurrentModuleActor Actor a) {
        Map<String, Object> result = new HashMap<>();
        result.put("room", service.latestRoom(a));
        return result;
    }

    @PostMapping("/member/rooms")
    public Room start(@CurrentModuleActor Actor a) {
        return service.startRoom(a);
    }

    @GetMapping("/rooms")
    public Object rooms(@CurrentModuleActor Actor a, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q) {
        return service.rooms(a, status, mine, page, q);
    }

    @GetMapping("/rooms/{id}")
    public Room room(@CurrentModuleActor Actor a, @PathVariable int id) {
        return service.getRoom(a, id);
    }

    @PostMapping("/rooms/{id}/close")
    public Room closeRoom(@CurrentModuleActor Actor a, @PathVariable int id) {
        return service.closeRoom(a, id);
    }

    @PostMapping("/admin/rooms/{id}/claim")
    public Room claim(@CurrentModuleActor Actor a, @PathVariable int id) {
        return service.claimRoom(a, id);
    }

    @PostMapping("/admin/rooms/{id}/escalate")
    public Ticket escalate(@CurrentModuleActor Actor a, @PathVariable int id,
            @Valid @RequestBody EscalateInput input) {
        return service.escalate(a, id, input);
    }

    @GetMapping("/rooms/{id}/messages")
    public MessagePage chatMessages(@CurrentModuleActor Actor a, @PathVariable int id,
            @RequestParam(required = false) Integer before,
            @RequestParam(required = false) Integer after) {
        return service.messages(a, true, id, before, after);
    }

    @PostMapping("/rooms/{id}/messages")
    public Message chatSend(@CurrentModuleActor Actor a, @PathVariable int id,
            @Valid @RequestBody MessageInput input) {
        return service.send(a, true, id, input);
    }

    @GetMapping("/tickets")
    public Object tickets(@CurrentModuleActor Actor a, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q) {
        return service.tickets(a, status, page, q);
    }

    @GetMapping("/tickets/{id}")
    public Ticket ticket(@CurrentModuleActor Actor a, @PathVariable int id) {
        return service.getTicket(a, id);
    }

    @GetMapping("/tickets/{id}/origins")
    public Object origins(@CurrentModuleActor Actor a, @PathVariable int id) {
        return service.origins(a, id);
    }

    @PostMapping("/admin/tickets/{id}/claim")
    public Ticket claimTicket(@CurrentModuleActor Actor a, @PathVariable int id) {
        return service.claimTicket(a, id);
    }

    @PostMapping("/admin/tickets/{id}/close")
    public Ticket closeTicket(@CurrentModuleActor Actor a, @PathVariable int id) {
        return service.closeTicket(a, id);
    }

    @GetMapping("/tickets/{id}/messages")
    public MessagePage ticketMessages(@CurrentModuleActor Actor a, @PathVariable int id,
            @RequestParam(required = false) Integer before,
            @RequestParam(required = false) Integer after) {
        return service.messages(a, false, id, before, after);
    }

    @PostMapping(value = "/tickets/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Message ticketSend(@CurrentModuleActor Actor a, @PathVariable int id,
            @Valid @RequestBody MessageInput input) {
        return service.send(a, false, id, input);
    }

    @PostMapping(value = "/tickets/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Message ticketImages(@CurrentModuleActor Actor actor, @PathVariable int id,
            @RequestParam(defaultValue = "") String content,
            @RequestParam("images") List<MultipartFile> images) {
        return service.sendTicketImages(actor, id, content, images);
    }

    @GetMapping("/tickets/{ticketId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> ticketImage(@CurrentModuleActor Actor actor, @PathVariable int ticketId,
            @PathVariable int attachmentId) {
        AttachmentData image = service.ticketImage(actor, ticketId, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.bytes().length)
                .header("Cache-Control", "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .body(image.bytes());
    }
}
