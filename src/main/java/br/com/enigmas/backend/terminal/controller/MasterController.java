package br.com.enigmas.backend.terminal.controller;

import br.com.enigmas.backend.terminal.dto.GameResponse;
import br.com.enigmas.backend.terminal.service.GameSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/master")
public class MasterController {
  private final GameSessionService sessions;
  private final String token;

  public MasterController(
      GameSessionService sessions, @Value("${master.reset-token:}") String token) {
    this.sessions = sessions;
    this.token = token;
  }

  @PostMapping("/reset")
  public ResponseEntity<GameResponse> reset(
      @RequestHeader(value = "X-Master-Token", defaultValue = "") String supplied,
      HttpServletRequest request)
      throws java.net.UnknownHostException {
    // Loopback alone is insufficient: the Angular proxy also calls from localhost.
    if (token.isBlank()
        || !InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()
        || !MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso exclusivo do mestre.");
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.reset());
  }
}
