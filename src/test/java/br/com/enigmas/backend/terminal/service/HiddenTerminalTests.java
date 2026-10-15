package br.com.enigmas.backend.terminal.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.enigma.service.*;
import br.com.enigmas.backend.terminal.model.NetworkState;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HiddenTerminalTests {
  private static final String HIDDEN = "tdo_em1";
  private static final String INTRO =
      "LOCALIZAÇÃO: —\nDISTÂNCIA: —\nCAMINHO FÍSICO: INEXISTENTE\n"
          + "TENTANDO CONEXÃO...\n\ntdo_em1 conectado.";
  private final JsonMapper mapper = new JsonMapper();
  private final PuzzleCatalog catalog = new PuzzleCatalog(mapper);
  private final TerminalGame game = new TerminalGame(catalog, new CipherService());

  HiddenTerminalTests() throws Exception {}

  private NetworkState initial() {
    var state = game.initial("test");
    state.computers.get("tec_la").connectionsUnlocked = true;
    return state;
  }

  private void send(NetworkState state, String input) {
    game.submit(state, "tec_la", input, () -> 0);
    // These tests focus on game rules; timing and input locking have dedicated coverage.
    while (state.computers.get("tec_la").timedResponse != null)
      game.advanceTimedResponses(state, state.computers.get("tec_la").timedResponse.nextAt);
  }

  private String output(NetworkState state) {
    return state.computers.get("tec_la").entries.getLast().output();
  }

  @Test
  void startsAwakeDisconnectedAndOutsideThePublicCatalog() {
    var state = initial();
    var hidden = state.computers.get(HIDDEN);
    assertEquals("acordado", hidden.mode);
    assertNull(hidden.inputFrom);
    assertNull(hidden.outputTo);
    assertFalse(hidden.connectionsUnlocked);
    assertFalse(hidden.connectionIntroduced);
    assertEquals(10, catalog.publicComputers().size());
    assertTrue(catalog.publicComputers().stream().noneMatch(c -> c.id().equals(HIDDEN)));
    assertThrows(IllegalArgumentException.class, () -> game.submit(state, HIDDEN, "contexto"));
  }

  @Test
  void introducesOnceAndKeepsReciprocalConnections() {
    var state = initial();
    send(state, "cncta_sda tdo_em1");
    assertEquals(INTRO, output(state));
    assertEquals(HIDDEN, state.computers.get("tec_la").outputTo);
    assertEquals("tec_la", state.computers.get(HIDDEN).inputFrom);
    assertNull(state.computers.get("fnt_primdal").inputFrom);
    send(state, "cncta_sda tdo_em1");
    assertEquals("SAÍDA CONECTADA: tdo_em1", output(state));
    send(state, "limpa");
    send(state, "lbr_sda");
    assertNull(state.computers.get(HIDDEN).inputFrom);
    // Database serialization and reconnection must not replay the introduction.
    state = mapper.readValue(mapper.writeValueAsString(state), NetworkState.class);
    send(state, "cncta_sda tdo_em1");
    assertEquals("SAÍDA CONECTADA: tdo_em1", output(state));
    var reset = initial();
    send(reset, "cncta_sda tdo_em1");
    assertEquals(INTRO, output(reset));
  }

  @Test
  void acceptsAnInputConnectionAndSharesTheIntroductionAcrossComputers() {
    var state = initial();
    send(state, "cncta_ent tdo_em1");
    assertEquals(INTRO, output(state));
    assertEquals("tec_la", state.computers.get(HIDDEN).outputTo);
    assertEquals(HIDDEN, state.computers.get("tec_la").inputFrom);
    send(state, "lbr_ent");
    assertNull(state.computers.get(HIDDEN).outputTo);
    state.computers.get("inno_m1nvl").connectionsUnlocked = true;
    game.submit(state, "inno_m1nvl", "cncta_sda tdo_em1");
    assertEquals("SAÍDA CONECTADA: tdo_em1",
        state.computers.get("inno_m1nvl").entries.getLast().output());
  }

  @Test
  void rejectedOrIncompleteConnectionsDoNotConsumeTheIntroduction() {
    var state = game.initial("test");
    send(state, "cncta_sda tdo_em1");
    assertEquals("Chave não encontrada ou não digitada nos ultimos 30 dias.", output(state));
    assertFalse(state.computers.get(HIDDEN).connectionIntroduced);
    state.computers.get("tec_la").connectionsUnlocked = true;
    send(state, "cncta_sda tdo_em1 extra");
    send(state, "cncta_sda");
    assertFalse(state.computers.get(HIDDEN).connectionIntroduced);
    send(state, "cncta_sda tdo_em1");
    assertEquals(INTRO, output(state));
  }

  @Test
  void awakeRepliesReachTheOriginWithoutRepeatingThePreviousPhrase() {
    var phrases = catalog.get(HIDDEN).awakePhrases();
    assertEquals(5, new HashSet<>(phrases).size());
    var seen = new HashSet<String>();
    for (int i = 0; i < phrases.size(); i++) {
      var state = initial();
      send(state, "dormindo");
      send(state, "cncta_sda tdo_em1");
      double choice = (i + 0.1) / phrases.size();
      game.submit(state, "tec_la", "PORTA", () -> choice);
      var entry = state.computers.get(HIDDEN).entries.getLast();
      seen.add(entry.output());
      assertEquals("PRATO", entry.text());
      assertEquals("tec_la", entry.source());
      assertTrue(output(state).contains("tdo_em1: " + entry.output()));
      assertFalse(output(state).contains("TRANSMISSÃO INTERROMPIDA"));
      game.submit(state, "tec_la", "PORTA", () -> choice);
      assertNotEquals(entry.output(), state.computers.get(HIDDEN).entries.getLast().output());
      assertFalse(state.computers.get(HIDDEN).connectionsUnlocked);
    }
    assertEquals(new HashSet<>(phrases), seen);
  }

  @Test
  void contextShowsTheRiddleAndTheElevenComputersInOrder() {
    var state = initial();
    send(state, "cncta_sda tdo_em1");
    send(state, "contexto");
    var route = List.of("sr_grdabs", "grd_s0nhadr", "nkai_a", "fnt_primdal",
        "h_colinas", "inno_m1nvl", "chma_vva", "caosra_st", "sultao_d", "tec_la", HIDDEN);
    assertEquals(route, catalog.get(HIDDEN).context().route());
    var expected = new ArrayList<String>();
    expected.add("INÍCIO: sr_grdabs");
    expected.add("PERCURSO: 11 computadores.");
    for (int i = 0; i < route.size(); i++) expected.add((i + 1) + ". " + route.get(i));
    expected.add("DESTINO: tdo_em1");
    assertEquals(catalog.get(HIDDEN).context().riddle() + "\n\n" + String.join("\n", expected), output(state));
    assertTrue(output(state).startsWith("Você insiste em perguntar de qual lado estou."));
    assertFalse(output(state).contains("LIMIAR"));
    assertEquals(HIDDEN, state.destination);
  }

  @Test
  void receivesTheWovenPayloadAndReturnsOnlyTheCipherResult() {
    var state = initial();
    send(state, "cncta_sda tdo_em1");
    send(state, "dormindo");
    send(state, "PAOTR");
    var received = state.computers.get(HIDDEN).entries.getLast();
    assertEquals("PORTA", received.text());
    assertEquals("NBEGN", received.output());
    assertEquals("tec_la: PORTA\ntdo_em1: NBEGN", output(state));
    assertEquals(Boolean.TRUE, received.outputParts().getFirst().cipher());
    send(state, "JPDIG");
    assertEquals("PORTA", state.computers.get(HIDDEN).entries.getLast().output());
  }

  @Test
  void rejectedFragmentIsVisibleAtTheSenderAndNeverForwarded() {
    var state = initial();
    send(state, "cncta_sda tdo_em1");
    var hidden = state.computers.get(HIDDEN);
    hidden.outputTo = "inno_m1nvl";
    state.computers.get("inno_m1nvl").inputFrom = HIDDEN;
    send(state, "dormindo");
    int nextEntries = state.computers.get("inno_m1nvl").entries.size();
    send(state, "P");
    assertEquals("ERRO:\nUM FRAGMENTO NÃO DEFINE O TODO.", output(state));
    assertEquals(output(state), hidden.entries.getLast().output());
    assertTrue(hidden.entries.getLast().system());
    assertNull(hidden.entries.getLast().outputParts());
    assertEquals(nextEntries, state.computers.get("inno_m1nvl").entries.size());
    assertFalse(hidden.connectionsUnlocked);
    assertEquals("inno_m1nvl", hidden.outputTo);
    assertEquals(HIDDEN, state.computers.get("tec_la").outputTo);
    send(state, "lbr_sda");
    send(state, "cncta_sda tdo_em1");
    hidden.outputTo = null;
    state.computers.get("inno_m1nvl").inputFrom = null;
    send(state, "PAOTR");
    assertEquals("NBEGN", hidden.entries.getLast().output());
  }

  @Test
  void awakeTerminalDoesNotApplyTheFragmentRule() {
    var state = initial();
    send(state, "dormindo");
    send(state, "cncta_sda tdo_em1");
    send(state, "P");
    assertTrue(catalog.get(HIDDEN).awakePhrases().contains(
        state.computers.get(HIDDEN).entries.getLast().output()));
    assertFalse(output(state).contains("ERRO:"));
  }

  @Test
  void exactKeyOnAnIncompleteRouteShowsTheRequiredRouteWithoutUnlocking() {
    var state = initial();
    send(state, "cncta_sda tdo_em1");
    send(state, "dormindo");
    // The web produces PTXTLC; the hidden terminal then produces LIMIAR.
    send(state, "pćtlxt");
    var entry = state.computers.get(HIDDEN).entries.getLast();
    assertTrue(entry.output().startsWith("LIMIAR\nINÍCIO: sr_grdabs"));
    assertTrue(output(state).contains("11. tdo_em1"));
    assertFalse(state.computers.get(HIDDEN).connectionsUnlocked);
    assertEquals(HIDDEN, state.computers.get("tec_la").outputTo);
    send(state, "OCGL TPXT");
    assertEquals("UM LIMIAR", state.computers.get(HIDDEN).entries.getLast().output());
    assertFalse(output(state).contains("INÍCIO:"));
    assertFalse(state.computers.get(HIDDEN).connectionsUnlocked);
  }

  @Test
  void fullRouteCannotUnlockAnAwakeHiddenTerminal() throws Exception {
    try (var input = getClass().getResourceAsStream("/current-route-solutions.json")) {
      for (var sample : mapper.readTree(input)) {
        if (!sample.get("id").asString().equals(HIDDEN)) continue;
        var state = initial();
        var route = catalog.get(HIDDEN).context().route();
        for (var pc : state.computers.values()) {
          pc.inputFrom = null;
          pc.outputTo = null;
          pc.mode = "dormindo";
        }
        for (int i = 0; i < route.size() - 1; i++) {
          state.computers.get(route.get(i)).outputTo = route.get(i + 1);
          state.computers.get(route.get(i + 1)).inputFrom = route.get(i);
        }
        var hidden = state.computers.get(HIDDEN);
        hidden.mode = "acordado";
        game.submit(state, route.getFirst(), sample.get("input").asString(), () -> 0);
        assertFalse(hidden.connectionsUnlocked);
        assertTrue(catalog.get(HIDDEN).awakePhrases().contains(hidden.entries.getLast().output()));
        hidden.mode = "dormindo";
        // Solving must stop the message before following the extra link, without removing it.
        hidden.outputTo = route.getFirst();
        state.computers.get(route.getFirst()).inputFrom = HIDDEN;
        game.submit(state, route.getFirst(), sample.get("input").asString(), () -> 0);
        assertTrue(hidden.connectionsUnlocked);
        assertTrue(hidden.entries.getLast().output().startsWith("LIMIAR\n"));
        assertEquals(route.getFirst(), hidden.outputTo);
        assertEquals(HIDDEN, state.computers.get(route.getFirst()).inputFrom);
        return;
      }
    }
    fail("Missing hidden terminal route fixture");
  }

  @Test
  void remoteTranceWaitsThenDeliversSevenLinesAndAFixedFinalReply() {
    var state = initial();
    send(state, "cncta_sda tdo_em1");
    state.destination = "sultao_d";
    game.submit(state, "chma_vva", "transe", () -> 0, 1000);
    var hidden = state.computers.get(HIDDEN);
    assertEquals("transe", hidden.mode);
    assertEquals("acordado", state.computers.get("chma_vva").mode);
    assertEquals("acordado", state.computers.get("tec_la").mode);
    assertEquals("sultao_d", state.destination);
    assertNull(hidden.trance);
    assertFalse(game.advanceTrance(state, 2000));
    game.submit(state, "chma_vva", "está me ouvindo?", () -> 0, 1000);
    assertEquals("chma_vva", hidden.trance.receiver);
    assertEquals(7, catalog.get(HIDDEN).trancePhrases().size());
    assertFalse(game.advanceTrance(state, 2999));
    // A persisted sequence resumes, including delivery to its caller.
    state = mapper.readValue(mapper.writeValueAsString(state), NetworkState.class);
    hidden = state.computers.get(HIDDEN);
    for (int i = 0; i < 7; i++) {
      long now = 3000 + i * 2000;
      assertTrue(game.advanceTrance(state, now));
      String line = catalog.get(HIDDEN).trancePhrases().get(i);
      assertEquals(line, hidden.entries.getLast().output());
      assertEquals(HIDDEN + ": " + line, state.computers.get("chma_vva").entries.getLast().output());
      assertFalse(game.advanceTrance(state, now));
    }
    assertEquals(7, hidden.count);
    assertNull(hidden.trance);
    assertFalse(game.advanceTrance(state, 50000));
    assertEquals(1, state.computers.get("tec_la").entries.size());
    state = mapper.readValue(mapper.writeValueAsString(state), NetworkState.class);
    hidden = state.computers.get(HIDDEN);
    assertTrue(hidden.tranceCompleted);
    for (int i = 0; i < 2; i++) {
      long now = 50000 + i * 4000;
      game.submit(state, "chma_vva", "outra mensagem", () -> 0, now);
      assertTrue(game.advanceTrance(state, now + 2000));
      assertEquals(catalog.get(HIDDEN).tranceFinalPhrase(), hidden.entries.getLast().output());
      assertEquals(HIDDEN + ": " + catalog.get(HIDDEN).tranceFinalPhrase(),
          state.computers.get("chma_vva").entries.getLast().output());
      assertNull(hidden.trance);
    }
  }

  @Test
  void remoteTranceIgnoresMessagesAndRepeatedStartsWhileActive() {
    var state = initial();
    send(state, "cncta_sda tdo_em1");
    game.submit(state, "tec_la", "transe", () -> 0, 0);
    game.submit(state, "tec_la", "primeira mensagem", () -> 0, 0);
    var before = mapper.writeValueAsString(state);
    game.submit(state, "tec_la", "qualquer mensagem", () -> 0, 1000);
    game.submit(state, "tec_la", "transe", () -> 0, 1000);
    game.submit(state, "chma_vva", "transe", () -> 0, 1000);
    game.submit(state, "chma_vva", "outro jogador", () -> 0, 1000);
    assertEquals(before, mapper.writeValueAsString(state));
    send(state, "dormindo");
    assertEquals("dormindo", state.computers.get(HIDDEN).mode);
    assertNull(state.computers.get(HIDDEN).trance);
    assertFalse(game.advanceTrance(state, 2000));
  }

  @Test
  void disconnectedCallerStopsReceivingTranceAndResetClearsIt() {
    var state = initial();
    send(state, "cncta_sda tdo_em1");
    game.submit(state, "tec_la", "transe", () -> 0, 0);
    game.submit(state, "tec_la", "primeira mensagem", () -> 0, 0);
    send(state, "limpa");
    assertTrue(game.advanceTrance(state, 2000));
    assertEquals(1, state.computers.get("tec_la").entries.size());
    send(state, "lbr_sda");
    int count = state.computers.get("tec_la").entries.size();
    assertTrue(game.advanceTrance(state, 4000));
    assertEquals(count, state.computers.get("tec_la").entries.size());
    assertNull(initial().computers.get(HIDDEN).trance);
    assertFalse(game.advanceTrance(initial(), 10000));
  }

  @Test
  void tranceRejectsACycleWithoutStartingAnySequence() {
    var state = initial();
    send(state, "cncta_sda tdo_em1");
    state.computers.get(HIDDEN).outputTo = "tec_la";
    state.computers.get("tec_la").inputFrom = HIDDEN;
    send(state, "transe");
    assertEquals("TRANSMISSÃO INTERROMPIDA: ciclo detectado em tec_la.", output(state));
    assertTrue(state.computers.values().stream().allMatch(pc -> pc.trance == null));
  }

  @Test
  void sleepingCipherDoesNotAcceptAnUnrelatedWord() {
    var state = initial();
    send(state, "cncta_sda tdo_em1");
    send(state, "dormindo");
    assertEquals("dormindo", state.computers.get(HIDDEN).mode);
    send(state, "PORTA");
    assertEquals("tec_la: PRATO\ntdo_em1: NENGB", output(state));
    assertFalse(state.computers.get(HIDDEN).connectionsUnlocked);
    assertEquals(HIDDEN, state.computers.get("tec_la").outputTo);
    assertEquals("encodeAllInOne", catalog.get(HIDDEN).cipher());
    assertEquals("LIMIAR", catalog.get(HIDDEN).context().answer());
  }
}
