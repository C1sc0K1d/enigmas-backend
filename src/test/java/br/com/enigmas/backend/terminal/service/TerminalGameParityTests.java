package br.com.enigmas.backend.terminal.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.enigma.service.*;
import br.com.enigmas.backend.terminal.model.NetworkState;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

class TerminalGameParityTests {
  private final JsonMapper mapper = new JsonMapper();

  @TestFactory
  Collection<DynamicTest> matchesAngularSnapshots() throws Exception {
    var catalog = org.mockito.Mockito.mock(PuzzleCatalog.class);
    try (var input = getClass().getResourceAsStream("/legacy-computers.json")) {
      var definitions =
          mapper.readValue(input, br.com.enigmas.backend.enigma.model.ComputerDefinition[].class);
      org.mockito.Mockito.when(catalog.all()).thenReturn(List.of(definitions));
      for (var definition : definitions) {
        org.mockito.Mockito.when(catalog.get(definition.id())).thenReturn(definition);
        org.mockito.Mockito.when(catalog.contains(definition.id())).thenReturn(true);
      }
    }
    var engine = new TerminalGame(catalog, new CipherService());
    var tests = new ArrayList<DynamicTest>();
    try (var input = getClass().getResourceAsStream("/game-parity.json")) {
      for (var scenario : mapper.readTree(input))
        tests.add(
            DynamicTest.dynamicTest(
                scenario.get("name").asString(),
                () -> {
                  var state = mapper.treeToValue(scenario.get("initial"), NetworkState.class);
                  for (var step : scenario.get("steps"))
                    engine.submit(
                        state, step.get("id").asString(), step.get("text").asString(), () -> 0);
                  assertEquals(scenario.get("expected"), mapper.valueToTree(state));
                }));
    }
    return tests;
  }

  @TestFactory
  Collection<DynamicTest> matchesUnicodeAndCipherExamples() throws Exception {
    var ciphers = new CipherService();
    var tests = new ArrayList<DynamicTest>();
    try (var input = getClass().getResourceAsStream("/cipher-parity.json")) {
      for (var sample : mapper.readTree(input))
        tests.add(
            DynamicTest.dynamicTest(
                sample.get("cipher").asString() + ": " + sample.get("input").asString(),
                () ->
                    assertEquals(
                        sample.get("output").asString(),
                        ciphers.encode(
                            sample.get("cipher").asString(), sample.get("input").asString()))));
    }
    return tests;
  }
}
