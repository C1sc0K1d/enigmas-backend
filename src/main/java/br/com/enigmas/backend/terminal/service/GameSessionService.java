package br.com.enigmas.backend.terminal.service;

import br.com.enigmas.backend.enigma.service.PuzzleCatalog;
import br.com.enigmas.backend.terminal.dto.*;
import br.com.enigmas.backend.terminal.entity.GameSession;
import br.com.enigmas.backend.terminal.model.NetworkState;
import br.com.enigmas.backend.terminal.repository.GameSessionRepository;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class GameSessionService {
  private final GameSessionRepository repository;
  private final TerminalGame game;
  private final PuzzleCatalog catalog;
  private final ServerIdentity identity;
  private final JsonMapper mapper;
  private final ApplicationEventPublisher events;

  public GameSessionService(
      GameSessionRepository repository,
      TerminalGame game,
      PuzzleCatalog catalog,
      ServerIdentity identity,
      JsonMapper mapper,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.game = game;
    this.catalog = catalog;
    this.identity = identity;
    this.mapper = mapper;
    this.events = events;
  }

  public GameResponse create() {
    // Flyway creates this single row before requests arrive, so concurrent joins are safe.
    return load(GameEvents.SHARED_ID);
  }

  public GameResponse load(String id) {
    var row = locked(id);
    return response(row, read(row));
  }

  public GameResponse reset() {
    var row = locked(GameEvents.SHARED_ID);
    // A new cycle rejects late commands from before the reset, even with the same revision.
    var state = game.initial(UUID.randomUUID().toString());
    row.epoch = identity.id();
    row.snapshot = mapper.writeValueAsString(state);
    row.revision = 0;
    row.lastRequestId = null;
    events.publishEvent(new GameEvents.Changed(row.id));
    return response(row, state);
  }

  public GameResponse submit(String id, CommandRequest command) {
    var row = locked(id);
    var state = read(row);
    if (!state.serverSessionId.equals(command.serverSessionId().toString()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A partida reiniciou. Atualize a sessão.");
    if (command.requestId().toString().equals(row.lastRequestId)) return response(row, state);
    if (game.isIgnoredTranceInput(state, command.computerId(), command.text()))
      return response(row, state);
    if (row.revision != command.revision())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A sessão mudou. Atualize antes de enviar novamente.");
    if (!catalog.contains(command.computerId()))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Computador não encontrado.");
    game.submit(state, command.computerId(), command.text());
    trimHistory(state);
    row.snapshot = mapper.writeValueAsString(state);
    row.revision++;
    row.lastRequestId = command.requestId().toString();
    events.publishEvent(new GameEvents.Changed(id));
    return response(row, state);
  }

  public void advanceTrance(long now) {
    var existing = repository.findLocked(GameEvents.SHARED_ID);
    if (existing.isEmpty()) return;
    var row = existing.get();
    // Never revive an old cycle or mutate a dormant game merely because the timer ran.
    if (!row.epoch.equals(identity.id())) return;
    var state = mapper.readValue(row.snapshot, NetworkState.class);
    if (!game.advanceTrance(state, now)) return;
    trimHistory(state);
    row.snapshot = mapper.writeValueAsString(state);
    row.revision++;
    events.publishEvent(new GameEvents.Changed(row.id));
  }

  private void trimHistory(NetworkState state) {
    // Bound snapshots while retaining recent command recall and output.
    for (var pc : state.computers.values()) {
      if (pc.entries.size() > 500)
        pc.entries =
            new ArrayList<>(pc.entries.subList(pc.entries.size() - 500, pc.entries.size()));
      if (pc.commands.size() > 500)
        pc.commands =
            new ArrayList<>(pc.commands.subList(pc.commands.size() - 500, pc.commands.size()));
    }
  }

  private GameSession locked(String id) {
    return repository
        .findLocked(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessão não encontrada."));
  }

  private NetworkState read(GameSession row) {
    if (!row.epoch.equals(identity.id())) {
      var state = game.initial(identity.id());
      row.epoch = identity.id();
      row.snapshot = mapper.writeValueAsString(state);
      row.revision = 0;
      row.lastRequestId = null;
      return state;
    }
    return mapper.readValue(row.snapshot, NetworkState.class);
  }

  private GameResponse response(GameSession row, NetworkState state) {
    return new GameResponse(row.id, row.revision, state);
  }
}
