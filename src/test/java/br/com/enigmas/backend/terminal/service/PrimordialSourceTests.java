package br.com.enigmas.backend.terminal.service;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.enigma.service.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PrimordialSourceTests {
  private String encode(String text) {
    return new CipherService().encode("encodePrimordialSource", text);
  }

  @Test
  void matchesTheCompleteAlphabet() {
    assertEquals("AZBYCXDWEVFUGTHSIRJQKPLOMN", encode("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
    assertEquals("abcdefghijklmnopqrstuvwxyz", encode("acegikmoqsuwyzxvtrpnljhfdb"));
  }

  @Test
  void matchesAllProvidedExamples() {
    assertEquals("SHRQA", encode("PORTA"));
    assertEquals("JWRIA", encode("SHRQA"));
    assertEquals("PORTA", encode("VXRNA"));
    assertEquals("RCQHRTH", encode("RETORNO"));
    assertEquals("RETORNO", encode("RINXRZX"));
    assertNotEquals("RETORNO", encode("RCQHRTH"));
  }

  @Test
  void preservesCaseAndNonAlphabetCharacters() {
    assertEquals("Shrqa  retorno!\n123 é 😀 ⚠️", encode("Porta  rinxrzx!\n123 é 😀 ⚠️"));
    assertEquals("", encode(""));
  }

  @Test
  void localAndIncompleteRoutesDoNotUnlock() throws Exception {
    var engine = new TerminalGame(new PuzzleCatalog(new JsonMapper()), new CipherService());
    var state = engine.initial("test");
    state.computers.get("fnt_primdal").mode = "dormindo";
    engine.submit(state, "fnt_primdal", "RINXRZX");
    assertFalse(state.computers.get("fnt_primdal").connectionsUnlocked);
    assertTrue(state.computers.get("fnt_primdal").entries.getLast().output().contains("chma_vva"));
    state.computers.get("tec_la").mode = "dormindo";
    engine.submit(state, "tec_la", "RXIZNRX");
    assertFalse(state.computers.get("fnt_primdal").connectionsUnlocked);
    assertTrue(state.computers.get("fnt_primdal").entries.getLast().output().contains("RETORNO"));
  }
}
