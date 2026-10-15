package gameplatform.support.controller;

import static gameplatform.support.model.Models.*;

import java.util.*;


import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import gameplatform.support.model.Actor;
import gameplatform.support.model.EventHub;
import gameplatform.support.model.Problem;
import gameplatform.support.service.SupportService;

@RestController
@RequestMapping("/api")
public class SupportController {
    private final SupportService service;
    private final EventHub events;

    public SupportController(SupportService s, EventHub e) {
        service = s;
        events = e;
    }

    @GetMapping("/public/config")
    public Object config(Actor a) {
        return Map.of("demo", false);
    }

    @GetMapping("/auth/me")
    public Object me(Actor a) {
        return Map.of("id", a.id(), "role", a.role(), "username", a.username(), "demo", false);
    }

    @GetMapping(value = "/events", produces = "text/event-stream")
    public SseEmitter events(Actor a, @RequestHeader("Authorization") String authorization) {
        return events.subscribe(a, authorization.substring(7));
    }

    @GetMapping("/admin/workload")
    public Object workload(Actor a) {
        return service.workload(a);
    }

    @GetMapping("/admin/queue")
    public Object queue(Actor a, @RequestParam(defaultValue = "rooms") String kind,
            @RequestParam(defaultValue = "unassigned") String scope,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        return service.workQueue(a, kind, scope, q, page);
    }

    @GetMapping("/categories")
    public Object categories(Actor a) {
        return service.categories();
    }

    @GetMapping("/games")
    public Object games(Actor a, @RequestParam(defaultValue = "") String q) {
        if (q.length() > 100)
            throw new Problem(400, "搜尋字數過長");
        return service.games(q);
    }

    @GetMapping("/member/rooms/latest")
    public Object latest(Actor a) {
        Map<String, Object> result = new HashMap<>();
        result.put("room", service.latestRoom(a));
        return result;
    }

    @PostMapping("/member/rooms")
    public Room start(Actor a) {
        return service.startRoom(a);
    }

    @GetMapping("/rooms")
    public Object rooms(Actor a, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "") String q) {
        return service.rooms(a, status, mine, page, q);
    }

    @GetMapping("/rooms/{id}")
    public Room room(Actor a, @PathVariable int id) {
        return service.getRoom(a, id);
    }

    @PostMapping("/rooms/{id}/close")
    public Room closeRoom(Actor a, @PathVariable int id) {
        return service.closeRoom(a, id);
    }

    @PostMapping("/admin/rooms/{id}/claim")
    public Room claim(Actor a, @PathVariable int id) {
        return service.claimRoom(a, id);
    }

    @PostMapping("/admin/rooms/{id}/escalate")
    public Ticket escalate(Actor a, @PathVariable int id,
            @Valid @RequestBody EscalateInput input) {
        return service.escalate(a, id, input);
    }

    @GetMapping("/rooms/{id}/messages")
    public MessagePage chatMessages(Actor a, @PathVariable int id,
            @RequestParam(required = false) Integer before,
            @RequestParam(required = false) Integer after) {
        return service.messages(a, true, id, before, after);
    }

    @PostMapping("/rooms/{id}/messages")
    public Message chatSend(Actor a, @PathVariable int id,
            @Valid @RequestBody MessageInput input) {
        return service.send(a, true, id, input);
    }

    @GetMapping("/tickets")
    public Object tickets(Actor a, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String waitingFor) {
        return service.tickets(a, status, page, q, categoryId, priority, waitingFor);
    }

    public record PriorityInput(String priority) {}

    @org.springframework.web.bind.annotation.PutMapping("/admin/tickets/{id}/priority")
    public Ticket priority(Actor a, @PathVariable int id,
            @RequestBody PriorityInput input) {
        return service.updateTicketPriority(a, id, input.priority());
    }

    @GetMapping("/tickets/{id}")
    public Ticket ticket(Actor a, @PathVariable int id) {
        return service.getTicket(a, id);
    }

    @GetMapping("/tickets/{id}/origins")
    public Object origins(Actor a, @PathVariable int id) {
        return service.origins(a, id);
    }

    @PostMapping("/admin/tickets/{id}/claim")
    public Ticket claimTicket(Actor a, @PathVariable int id) {
        return service.claimTicket(a, id);
    }

    @PostMapping("/admin/tickets/{id}/close")
    public Ticket closeTicket(Actor a, @PathVariable int id) {
        return service.closeTicket(a, id);
    }

    @GetMapping("/tickets/{id}/messages")
    public MessagePage ticketMessages(Actor a, @PathVariable int id,
            @RequestParam(required = false) Integer before,
            @RequestParam(required = false) Integer after) {
        return service.messages(a, false, id, before, after);
    }

    @PostMapping(value = "/tickets/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Message ticketSend(Actor a, @PathVariable int id,
            @Valid @RequestBody MessageInput input) {
        return service.send(a, false, id, input);
    }

    @PostMapping(value = "/tickets/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Message ticketImages(Actor actor, @PathVariable int id,
            @RequestParam(defaultValue = "") String content,
            @RequestParam("images") List<MultipartFile> images) {
        return service.sendTicketImages(actor, id, content, images);
    }

    @GetMapping("/tickets/{ticketId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> ticketImage(Actor actor, @PathVariable int ticketId,
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
