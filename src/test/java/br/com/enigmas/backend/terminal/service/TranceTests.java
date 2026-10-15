package br.com.enigmas.backend.terminal.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.enigma.service.*;
import br.com.enigmas.backend.terminal.model.NetworkState;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

class TranceTests {
  private final PuzzleCatalog catalog = new PuzzleCatalog(new JsonMapper());
  private final TerminalGame game = new TerminalGame(catalog, new CipherService());

  TranceTests() throws Exception {}

  private void send(NetworkState state, String id, String text, long now) {
    game.submit(state, id, text, () -> 0, now);
  }

  private void begin(NetworkState state, String id, long now) {
    send(state, id, "transe", now);
    send(state, id, "está me ouvindo?", now);
  }

  private void disconnectOutput(NetworkState state, String id) {
    var pc = state.computers.get(id);
    if (pc.outputTo != null) state.computers.get(pc.outputTo).inputFrom = null;
    pc.outputTo = null;
  }

  @TestFactory
  Collection<DynamicTest> eachComputerWaitsThenSpeaksItsSequenceAndRepeatsOnlyItsFinalReply() {
    var tests = new ArrayList<DynamicTest>();
    for (var computer : catalog.publicComputers())
      tests.add(
          DynamicTest.dynamicTest(
              computer.id(),
              () -> {
                var state = game.initial("test");
                var pc = state.computers.get(computer.id());
                disconnectOutput(state, computer.id());
                send(state, computer.id(), "transe", 1000);
                assertEquals("transe", pc.mode);
                assertEquals(1, pc.entries.size());
                assertNull(pc.trance);
                assertFalse(game.advanceTrance(state, 50000));
                send(state, computer.id(), "primeira mensagem", 51000);
                assertFalse(game.advanceTrance(state, 52999));
                for (int i = 0; i < 6; i++) {
                  long now = 53000 + i * 2000;
                  assertTrue(game.advanceTrance(state, now));
                  assertEquals(computer.trancePhrases().get(i), pc.entries.getLast().output());
                  assertEquals("", pc.entries.getLast().text());
                  assertFalse(game.advanceTrance(state, now));
                }
                assertNull(pc.trance);
                assertEquals(6, pc.count);
                assertTrue(pc.tranceCompleted);
                assertFalse(game.advanceTrance(state, 100000));
                for (int i = 0; i < 2; i++) {
                  long now = 100000 + i * 4000;
                  send(state, computer.id(), "outra mensagem", now);
                  assertTrue(game.advanceTrance(state, now + 2000));
                  assertEquals(computer.tranceFinalPhrase(), pc.entries.getLast().output());
                  assertNull(pc.trance);
                }
                send(state, computer.id(), "transe", 110000);
                assertTrue(pc.tranceCompleted);
                assertFalse(game.advanceTrance(state, 120000));
                send(state, computer.id(), "acordado", 121000);
                send(state, computer.id(), "transe", 122000);
                assertFalse(pc.tranceCompleted);
                assertNull(pc.trance);
              }));
    return tests;
  }

  @Test
  void ignoresMessagesAndRepeatedTranceDuringTheCycle() {
    var state = game.initial("test");
    disconnectOutput(state, "tec_la");
    begin(state, "tec_la", 0);
    game.advanceTrance(state, 2000);
    var before = new JsonMapper().writeValueAsString(state);
    send(state, "tec_la", "mensagem ignorada", 2500);
    send(state, "tec_la", "transe", 2600);
    assertEquals(before, new JsonMapper().writeValueAsString(state));
    game.advanceTrance(state, 4000);
    assertEquals(
        catalog.get("tec_la").trancePhrases().get(1),
        state.computers.get("tec_la").entries.getLast().output());
  }

  @Test
  void wakeAndNetworkSleepCancelPendingLines() {
    var state = game.initial("test");
    disconnectOutput(state, "tec_la");
    begin(state, "tec_la", 0);
    send(state, "tec_la", "acordado", 1000);
    assertNull(state.computers.get("tec_la").trance);
    assertFalse(game.advanceTrance(state, 2000));
    begin(state, "tec_la", 3000);
    begin(state, "fnt_primdal", 3000);
    state.computers.get("tec_la").outputTo = "fnt_primdal";
    state.computers.get("fnt_primdal").inputFrom = "tec_la";
    send(state, "chma_vva", "dormindo", 4000);
    assertNull(state.computers.get("tec_la").trance);
    assertNull(state.computers.get("fnt_primdal").trance);
    assertFalse(game.advanceTrance(state, 10000));
  }

  @Test
  void ticksDoNotCatchUpInBurstsAndTerminalsRunIndependently() {
    var state = game.initial("test");
    disconnectOutput(state, "tec_la");
    begin(state, "tec_la", 0);
    begin(state, "h_colinas", 1000);
    game.advanceTrance(state, 2000);
    assertEquals(1, state.computers.get("tec_la").count);
    assertEquals(0, state.computers.get("h_colinas").count);
    game.advanceTrance(state, 100000);
    assertEquals(2, state.computers.get("tec_la").count);
    assertEquals(1, state.computers.get("h_colinas").count);
    assertFalse(game.advanceTrance(state, 100001));
  }

  @Test
  void tranceStopsCipherTransmissionAndNeverUnlocksOrStartsFromIncomingText() {
    var state = game.initial("test");
    send(state, "chma_vva", "dormindo", 0);
    disconnectOutput(state, "tec_la");
    begin(state, "tec_la", 0);
    state.computers.get("tec_la").outputTo = "fnt_primdal";
    state.computers.get("fnt_primdal").inputFrom = "tec_la";
    var target = state.computers.get("tec_la");
    long deadline = target.trance.nextAt;
    send(state, "chma_vva", "AAA", 1000);
    assertTrue(
        state.computers.get("chma_vva").entries.getLast().output().contains("está em transe"));
    assertEquals(deadline, target.trance.nextAt);
    assertFalse(target.connectionsUnlocked);
    for (int i = 1; i <= 6; i++) game.advanceTrance(state, i * 2000);
    send(state, "chma_vva", "AAA", 20000);
    assertNull(target.trance);
    assertEquals(0, state.computers.get("fnt_primdal").count);
  }
}
