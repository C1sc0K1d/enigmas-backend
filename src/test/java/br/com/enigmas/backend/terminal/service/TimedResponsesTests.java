package br.com.enigmas.backend.terminal.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.enigma.service.*;
import br.com.enigmas.backend.terminal.model.NetworkState;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TimedResponsesTests {
  private final JsonMapper mapper = new JsonMapper();
  private final PuzzleCatalog catalog = new PuzzleCatalog(mapper);
  private final TerminalGame game = new TerminalGame(catalog, new CipherService());

  TimedResponsesTests() throws Exception {}

  private NetworkState connect() {
    var state = game.initial("test");
    state.computers.get("tec_la").connectionsUnlocked = true;
    game.submit(state, "tec_la", "cncta_sda tdo_em1", () -> 0, 1000);
    return state;
  }

  private String output(NetworkState state, String id) {
    return state.computers.get(id).entries.getLast().output();
  }

  @Test
  void firstConnectionRevealsFiveLinesEveryFiveSecondsThenUnlocks() {
    var state = connect();
    var caller = state.computers.get("tec_la");
    assertEquals("LOCALIZAÇÃO: —", output(state, "tec_la"));
    assertTrue(game.isInputLocked(state, "tec_la"));
    String[] lines = catalog.get("tdo_em1").firstConnectionMessage().split("\\n(?=\\S)");
    assertEquals(5, lines.length);
    for (int i = 1; i < lines.length; i++) {
      long due = 1000 + i * 5000;
      assertFalse(game.advanceTimedResponses(state, due - 1));
      assertTrue(game.advanceTimedResponses(state, due));
      assertEquals(String.join("\n", Arrays.copyOf(lines, i + 1)), output(state, "tec_la"));
      assertEquals(i < lines.length - 1, game.isInputLocked(state, "tec_la"));
      assertFalse(game.advanceTimedResponses(state, due));
    }
    assertEquals(1, caller.entries.size());
    assertNull(caller.timedResponse);
    game.submit(state, "tec_la", "cncta_sda tdo_em1", () -> 0, 22000);
    assertEquals("SAÍDA CONECTADA: tdo_em1", output(state, "tec_la"));
    assertNull(caller.timedResponse);
  }

  @Test
  void allCommandsAreIgnoredWithoutMutationsWhileLocked() {
    var state = connect();
    var before = mapper.writeValueAsString(state);
    for (String text : List.of("qualquer coisa", "limpa", "/limpar", "contexto", "transe",
        "dormindo", "acordado", "entd", "sda", "lbr_ent", "lbr_sda", "/segredos",
        "cncta_sda nenhum", "cncta_ent tdo_em1")) {
      game.submit(state, "tec_la", text, () -> 0, 2000);
      assertEquals(before, mapper.writeValueAsString(state), text);
    }
  }

  @Test
  void reloadPreservesProgressAndDelayedTicksNeverBurst() {
    var state = connect();
    game.advanceTimedResponses(state, 6000);
    state = mapper.readValue(mapper.writeValueAsString(state), NetworkState.class);
    assertTrue(game.isInputLocked(state, "tec_la"));
    assertEquals(2, state.computers.get("tec_la").timedResponse.nextIndex);
    assertTrue(game.advanceTimedResponses(state, 100000));
    assertEquals(3, state.computers.get("tec_la").timedResponse.nextIndex);
    assertEquals(105000, state.computers.get("tec_la").timedResponse.nextAt);
    assertFalse(game.advanceTimedResponses(state, 100000));
    assertFalse(game.advanceTimedResponses(state, 104999));
    assertFalse(output(state, "tec_la").contains("tdo_em1 conectado."));
    var reset = game.initial("new-test");
    assertFalse(game.advanceTimedResponses(reset, 200000));
    assertFalse(game.isInputLocked(reset, "tec_la"));
    assertFalse(reset.computers.get("tdo_em1").connectionIntroduced);
  }

  @Test
  void successRevealsSixLinesEveryThreeSecondsAtBothEnds() throws Exception {
    try (var input = getClass().getResourceAsStream("/current-route-solutions.json")) {
      for (var sample : mapper.readTree(input)) {
        if (!sample.get("id").asString().equals("tdo_em1")) continue;
        var state = game.initial("test");
        var route = catalog.get("tdo_em1").context().route();
        for (var pc : state.computers.values()) {
          pc.inputFrom = null;
          pc.outputTo = null;
          pc.mode = "dormindo";
        }
        for (int i = 0; i < route.size() - 1; i++) {
          state.computers.get(route.get(i)).outputTo = route.get(i + 1);
          state.computers.get(route.get(i + 1)).inputFrom = route.get(i);
        }
        String origin = route.getFirst();
        game.submit(state, origin, sample.get("input").asString(), () -> 0, 1000);
        assertTrue(state.computers.get("tdo_em1").connectionsUnlocked);
        assertTrue(game.isInputLocked(state, origin));
        String[] lines = catalog.get("tdo_em1").context().successMessage().split("\n");
        assertEquals(6, lines.length);
        assertTrue(output(state, origin).endsWith(lines[0]));
        assertFalse(output(state, origin).contains(lines[1]));
        assertFalse(mapper.writeValueAsString(state).contains(lines[5]));
        for (int i = 1; i < lines.length; i++) {
          long due = 1000 + i * 3000;
          assertFalse(game.advanceTimedResponses(state, due - 1));
          assertTrue(game.advanceTimedResponses(state, due));
          String revealed = String.join("\n", Arrays.copyOf(lines, i + 1));
          assertTrue(output(state, origin).endsWith(revealed));
          assertEquals("LIMIAR\n" + revealed, output(state, "tdo_em1"));
          assertEquals(i < lines.length - 1, game.isInputLocked(state, origin));
          var entry = state.computers.get(origin).entries.getLast();
          assertEquals(entry.output(), entry.outputParts().stream().map(p -> p.text()).reduce("", String::concat));
        }
        assertEquals("tdo_em1", state.computers.get("tec_la").outputTo);
        assertNull(state.computers.get(origin).timedResponse);
        game.submit(state, origin, "sda", () -> 0, 17000);
        assertTrue(output(state, origin).startsWith("SAÍDA CONECTADA:"));
        return;
      }
    }
    fail("Missing hidden terminal fixture");
  }
}
