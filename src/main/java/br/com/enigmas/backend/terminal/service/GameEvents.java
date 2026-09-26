package br.com.enigmas.backend.terminal.service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Notifications are hints: clients always reload authoritative state after reconnecting. */
@Service
public class GameEvents {
  public record Changed(String gameId) {}

  public static final String SHARED_ID = "00000000-0000-4000-8000-000000000001";
  private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService heartbeat =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var thread = new Thread(r, "game-events-heartbeat");
            thread.setDaemon(true);
            return thread;
          });

  public GameEvents() {
    heartbeat.scheduleAtFixedRate(
        () -> clients.forEach(client -> send(client, "heartbeat")), 10, 10, TimeUnit.SECONDS);
  }

  public SseEmitter subscribe() {
    var client = new SseEmitter(0L);
    clients.add(client);
    client.onCompletion(() -> clients.remove(client));
    client.onTimeout(() -> clients.remove(client));
    client.onError(error -> clients.remove(client));
    send(client, "changed");
    return client;
  }

  @TransactionalEventListener
  public void changed(Changed event) {
    if (SHARED_ID.equals(event.gameId())) clients.forEach(client -> send(client, "changed"));
  }

  private void send(SseEmitter client, String event) {
    try {
      client.send(SseEmitter.event().name(event).data("refresh").reconnectTime(2000));
    } catch (IOException | IllegalStateException error) {
      clients.remove(client);
      client.complete();
    }
  }

  @PreDestroy
  public void close() {
    heartbeat.shutdownNow();
    clients.forEach(SseEmitter::complete);
    clients.clear();
  }
}
