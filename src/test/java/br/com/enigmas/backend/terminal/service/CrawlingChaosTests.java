package br.com.enigmas.backend.terminal.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.enigma.service.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CrawlingChaosTests {
  private String encode(String text) {
    return new CipherService().encode("encodeCrawlingChaos", text);
  }

  @Test
  void followsTheSpecifiedRemovalOrders() {
    assertEquals("BD AEC".replace(" ", ""), encode("ABCDE"));
    assertEquals("BDFCAE", encode("ABCDEF"));
    assertEquals("OTPAR", encode("PORTA"));
    assertEquals("PORTA", encode("RPAOT"));
    assertEquals("NAOGEN", encode("ENGANO"));
    assertEquals("ENGANO", encode("NEANOG"));
    assertNotEquals("PORTA", encode("OTPAR"));
  }

  @Test
  void handlesShortWordsAndPreservesEachLetterExactlyOnce() {
    assertEquals("", encode(""));
    assertEquals("A", encode("A"));
    assertEquals("BA", encode("AB"));
    assertEquals("BAC", encode("ABC"));
    assertEquals("BDCA", encode("ABCD"));
    assertEquals("AAAAAAA", encode("AAAAAAA"));
    for (int length = 1; length <= 2000; length++) {
      String word = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(77).substring(0, length);
      assertArrayEquals(word.chars().sorted().toArray(), encode(word).chars().sorted().toArray());
    }
  }

  @Test
  void treatsWordsSeparatelyAndKeepsCaseAndSeparators() {
    assertEquals("otPaR NAOGEN! 123 😀 ⚠️ é\n", encode("PoRta ENGANO! 123 😀 ⚠️ é\n"));
  }

  @Test
  void localAndWrongRouteAnswersDoNotUnlock() throws Exception {
    var engine = new TerminalGame(new PuzzleCatalog(new JsonMapper()), new CipherService());
    var state = engine.initial("test");
    state.computers.get("caosra_st").mode = "dormindo";
    engine.submit(state, "caosra_st", "NEANOG");
    assertFalse(state.computers.get("caosra_st").connectionsUnlocked);
    assertTrue(state.computers.get("caosra_st").entries.getLast().output().contains("h_colinas"));
    state.computers.get("fnt_primdal").mode = "dormindo";
    state.computers.get("fnt_primdal").outputTo = "caosra_st";
    engine.submit(state, "fnt_primdal", "ZIAZXM");
    assertFalse(state.computers.get("caosra_st").connectionsUnlocked);
    assertTrue(state.computers.get("caosra_st").entries.getLast().output().contains("ENGANO"));
  }
}
