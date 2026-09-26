package br.com.enigmas.backend.terminal.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "game.trance.enabled", havingValue = "true", matchIfMissing = true)
public class TranceScheduler {
  private final GameSessionService sessions;

  public TranceScheduler(GameSessionService sessions) {
    this.sessions = sessions;
  }

  @Scheduled(fixedDelay = 250)
  public void tick() {
    sessions.advanceTrance(System.currentTimeMillis());
  }
}
