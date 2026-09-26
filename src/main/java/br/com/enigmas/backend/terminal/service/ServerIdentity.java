package br.com.enigmas.backend.terminal.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ServerIdentity {
  private final String id = UUID.randomUUID().toString();

  public String id() {
    return id;
  }
}
