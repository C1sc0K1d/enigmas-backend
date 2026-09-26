package br.com.enigmas.backend.terminal.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.enigma.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

class ImpossibleFormTests {
  private final CipherService ciphers = new CipherService();

  private String encode(String text) {
    return ciphers.encode("encodeImpossibleForm", text);
  }

  @Test
  void matchesTheThreeProjections() {
    assertEquals("FWXPA", encode("PORTA"));
    assertEquals("TQZFA", encode("FWXPA"));
    assertEquals("PORTA", encode("TQZFA"));
    assertEquals("PROJECAO", encode("TZQBMGAQ"));
  }

  @Test
  void permutesAll27PositionsAndReturnsAfterThreeApplications() {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ ";
    assertEquals("AJSBKTCLUDMVENWFOXGPYHQZIR ", encode(alphabet));
    assertEquals(alphabet, encode(encode(encode(alphabet))));
    assertEquals(27, encode(alphabet).chars().distinct().count());
    assertEquals("  ", encode("  "));
  }

  @Test
  void normalizesLettersAndPreservesOtherSymbols() {
    assertEquals(encode("PROJECAO"), encode("projeção"));
    assertEquals("PROJECAO", encode(encode(encode("projeção"))));
    assertEquals("FWXPA! 123 😀 ⚠️\n", encode("porta! 123 😀 ⚠️\n"));
  }

  @Test
  void localSolutionAndWrongRouteDoNotUnlock() throws Exception {
    var engine = new TerminalGame(new PuzzleCatalog(new JsonMapper()), ciphers);
    var state = engine.initial("test");
    state.computers.get("h_colinas").mode = "dormindo";
    engine.submit(state, "h_colinas", "TZQBMGAQ");
    assertFalse(state.computers.get("h_colinas").connectionsUnlocked);
    assertTrue(state.computers.get("h_colinas").entries.getLast().output().contains("chma_vva"));
    state.computers.get("fnt_primdal").mode = "dormindo";
    state.computers.get("fnt_primdal").outputTo = "h_colinas";
    engine.submit(state, "fnt_primdal", "NBTCYMAT");
    assertFalse(state.computers.get("h_colinas").connectionsUnlocked);
  }

  @TestFactory
  Collection<DynamicTest> allAffectedRoutesStillSolve() throws Exception {
    var mapper = new JsonMapper();
    var catalog = new PuzzleCatalog(mapper);
    var engine = new TerminalGame(catalog, ciphers);
    var tests = new ArrayList<DynamicTest>();
    try (var input = getClass().getResourceAsStream("/current-route-solutions.json")) {
      for (var sample : mapper.readTree(input)) {
        tests.add(
            DynamicTest.dynamicTest(
                sample.get("id").asString(),
                () -> {
                  var state = engine.initial("test");
                  var route = mapper.treeToValue(sample.get("route"), String[].class);
                  String target = sample.get("id").asString();
                  for (var pc : state.computers.values()) {
                    pc.mode = "dormindo";
                    pc.inputFrom = null;
                    pc.outputTo = null;
                  }
                  for (int i = 0; i < route.length - 1; i++) {
                    state.computers.get(route[i]).outputTo = route[i + 1];
                    state.computers.get(route[i + 1]).inputFrom = route[i];
                  }
                  state.destination = target;
                  engine.submit(state, route[0], sample.get("input").asString());
                  assertTrue(state.computers.get(target).connectionsUnlocked);
                  var message = catalog.get(target).context().successMessage();
                  if (message != null) {
                    assertTrue(
                        state.computers.get(target).entries.getLast().output().contains(message));
                    assertTrue(
                        state.computers.get(route[0]).entries.getLast().output().contains(message));
                  }
                }));
      }
    }
    return tests;
  }
}
