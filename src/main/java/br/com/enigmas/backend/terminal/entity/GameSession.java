package br.com.enigmas.backend.terminal.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "game_sessions")
public class GameSession {
  @Id public String id;

  @Column(nullable = false)
  public String epoch;

  @Column(nullable = false, columnDefinition = "text")
  public String snapshot;

  @Column(nullable = false)
  public long revision;

  public String lastRequestId;

  protected GameSession() {}

  public GameSession(String id, String epoch, String snapshot) {
    this.id = id;
    this.epoch = epoch;
    this.snapshot = snapshot;
  }
}
