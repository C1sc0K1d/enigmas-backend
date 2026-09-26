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

  @TestFactory
  Collection<DynamicTest> eachComputerSpeaksItsSixLinesInOrderThenWaits() {
    var tests = new ArrayList<DynamicTest>();
    for (var computer : catalog.all())
      tests.add(
          DynamicTest.dynamicTest(
              computer.id(),
              () -> {
                var state = game.initial("test");
                var pc = state.computers.get(computer.id());
                send(state, computer.id(), "transe", 1000);
                assertEquals("transe", pc.mode);
                assertEquals(1, pc.entries.size());
                assertFalse(game.advanceTrance(state, 2999));
                for (int i = 0; i < 6; i++) {
                  long now = 3000 + i * 2000;
                  assertTrue(game.advanceTrance(state, now));
                  assertEquals(computer.trancePhrases().get(i), pc.entries.getLast().output());
                  assertEquals("", pc.entries.getLast().text());
                  assertFalse(game.advanceTrance(state, now));
                }
                assertNull(pc.trance);
                assertEquals(6, pc.count);
                assertFalse(game.advanceTrance(state, 50000));
                send(state, computer.id(), "outra mensagem", 50000);
                assertEquals(0, pc.trance.nextIndex);
                assertTrue(game.advanceTrance(state, 52000));
                assertEquals(computer.trancePhrases().getFirst(), pc.entries.getLast().output());
              }));
    return tests;
  }

  @Test
  void ignoresMessagesAndRepeatedTranceDuringTheCycle() {
    var state = game.initial("test");
    send(state, "tec_la", "transe", 0);
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
    send(state, "tec_la", "transe", 0);
    send(state, "tec_la", "acordado", 1000);
    assertNull(state.computers.get("tec_la").trance);
    assertFalse(game.advanceTrance(state, 2000));
    send(state, "tec_la", "transe", 3000);
    send(state, "fnt_primdal", "transe", 3000);
    send(state, "chma_vva", "dormindo", 4000);
    assertNull(state.computers.get("tec_la").trance);
    assertNull(state.computers.get("fnt_primdal").trance);
    assertFalse(game.advanceTrance(state, 10000));
  }

  @Test
  void ticksDoNotCatchUpInBurstsAndTerminalsRunIndependently() {
    var state = game.initial("test");
    send(state, "tec_la", "transe", 0);
    send(state, "h_colinas", "transe", 1000);
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
    send(state, "tec_la", "transe", 0);
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
