package br.com.enigmas.backend.terminal.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.terminal.dto.CommandRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "game.trance.enabled=true",
      "spring.datasource.url=jdbc:h2:mem:trance_scheduler_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
    })
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TranceSchedulerTests {
  @Autowired GameSessionService sessions;

  @Test
  void advancesWithoutBrowserRequestsAndCancelsOnReset() throws Exception {
    var game = sessions.create();
    var started =
        sessions.submit(
            game.gameId(),
            new CommandRequest(
                "h_colinas",
                "transe",
                UUID.randomUUID(),
                game.revision(),
                UUID.fromString(game.state().serverSessionId)));
    long due = started.state().computers.get("h_colinas").trance.nextAt;
    assertEquals(0, started.state().computers.get("h_colinas").count);
    var latest = started;
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
    while (latest.state().computers.get("h_colinas").count == 0 && System.nanoTime() < deadline) {
      Thread.sleep(50);
      latest = sessions.load(game.gameId());
    }
    assertTrue(System.currentTimeMillis() >= due);
    assertEquals(1, latest.state().computers.get("h_colinas").count);
    assertEquals(
        "Você parece muito seguro daquilo que está vendo.",
        latest.state().computers.get("h_colinas").entries.getLast().output());
    var reset = sessions.reset();
    Thread.sleep(2250);
    var after = sessions.load(game.gameId());
    assertEquals(reset.revision(), after.revision());
    assertEquals(reset.state().serverSessionId, after.state().serverSessionId);
    assertTrue(after.state().computers.get("h_colinas").entries.isEmpty());
    assertNull(after.state().computers.get("h_colinas").trance);
  }
}
