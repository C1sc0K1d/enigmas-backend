package br.com.enigmas.backend.terminal.controller;

import br.com.enigmas.backend.terminal.dto.*;
import br.com.enigmas.backend.terminal.service.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GameController {
  private final GameSessionService sessions;
  private final ServerIdentity identity;
  private final GameEvents events;

  public GameController(GameSessionService sessions, ServerIdentity identity, GameEvents events) {
    this.sessions = sessions;
    this.identity = identity;
    this.events = events;
  }

  @GetMapping(value = "/games/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> events() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Accel-Buffering", "no")
        .body(events.subscribe());
  }

  @GetMapping("/session")
  public ResponseEntity<Map<String, String>> identity() {
    return noCache(Map.of("sessionId", identity.id()));
  }

  @PostMapping("/games")
  public ResponseEntity<GameResponse> create() {
    return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(sessions.create());
  }

  @GetMapping("/games/current")
  public ResponseEntity<GameResponse> current(@RequestHeader("X-Game-Session") UUID id) {
    return noCache(sessions.load(id.toString()));
  }

  @PostMapping("/games/current/commands")
  public ResponseEntity<GameResponse> submit(
      @RequestHeader("X-Game-Session") UUID id, @Valid @RequestBody CommandRequest request) {
    return noCache(sessions.submit(id.toString(), request));
  }

  private <T> ResponseEntity<T> noCache(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
