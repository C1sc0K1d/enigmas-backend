package br.com.enigmas.backend.terminal.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.enigma.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AbyssCarriersTests {
  private String encode(String text) {
    return new CipherService().encode("encodeAbyssCarriers", text);
  }

  @Test
  void matchesTheProvidedExamplesAndInverseInputs() {
    assertEquals("OZ", encode("PO"));
    assertEquals("OZTCB", encode("PORTA"));
    assertEquals("PORTA", encode("BPYRZ"));
    assertEquals("RYVVSOIQB", encode("TRAVESSIA"));
    assertEquals("TRAVESSIA", encode("CTFAMEKSZ"));
    assertNotEquals("PORTA", encode("OZTCB"));
  }

  @Test
  void coversWrappingZeroDistanceAndAllPairs() {
    assertEquals("AA", encode("AA"));
    assertEquals("AB", encode("ZA"));
    assertEquals("ZZ", encode("AZ"));
    assertEquals("A", encode("Z"));
    var outputs = new HashSet<String>();
    for (char first = 'A'; first <= 'Z'; first++) {
      for (char second = 'A'; second <= 'Z'; second++) {
        String output = encode("" + first + second);
        assertEquals(second, output.charAt(0));
        assertTrue(outputs.add(output), "Every pair must have a unique output");
        int reconstructed = Math.floorMod(output.charAt(0) - 'A' - (output.charAt(1) - 'A'), 26);
        assertEquals(first - 'A', reconstructed);
      }
    }
    assertEquals(676, outputs.size());
  }

  @Test
  void restartsPairsAtWordBoundariesAndPreservesSeparators() {
    assertEquals("oztCb", encode("poRta"));
    assertEquals("B A! OZ  TC\n123 😀 ⚠️ é", encode("A Z! PO  RT\n123 😀 ⚠️ é"));
    assertEquals("", encode(""));
  }

  @Test
  void localAnswerAndWrongRouteDoNotUnlock() throws Exception {
    var engine = new TerminalGame(new PuzzleCatalog(new JsonMapper()), new CipherService());
    var state = engine.initial("test");
    state.computers.get("sr_grdabs").mode = "dormindo";
    engine.submit(state, "sr_grdabs", "CTFAMEKSZ");
    assertFalse(state.computers.get("sr_grdabs").connectionsUnlocked);
    assertTrue(state.computers.get("sr_grdabs").entries.getLast().output().contains("inno_m1nvl"));
    state.computers.get("fnt_primdal").mode = "dormindo";
    state.computers.get("fnt_primdal").outputTo = "sr_grdabs";
    engine.submit(state, "fnt_primdal", "ENKAYIUPB");
    assertFalse(state.computers.get("sr_grdabs").connectionsUnlocked);
  }
}
