package br.com.enigmas.backend.enigma.cipher;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.enigma.service.CipherService;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AllInOneCipherTests {
  private String encode(String text) {
    return new CipherService().encode("encodeAllInOne", text);
  }

  @Test
  void matchesTheExamplesAndIsNotItsOwnInverse() {
    assertEquals("NBEGN", encode("PORTA"));
    assertEquals("LQTVC", encode("NBEGN"));
    assertEquals("PORTA", encode("JDGIP"));
    assertNotEquals("PORTA", encode(encode("PORTA")));
  }

  @Test
  void eachWordHasItsOwnTotalAndSingleLetterWordsRemainValidInPhrases() {
    assertEquals("A NBEGN E OAZTA", encode("A PORTA É SONHO"));
    assertEquals("A O", encode("A O"));
    assertEquals("ZA ZB AA", encode("AZ ZA AA"));
  }

  @Test
  void normalizesCaseAndAccentsWithoutChangingSeparators() {
    assertEquals("NBEGN", encode("pÓrTa"));
    assertEquals("NBEGN", encode("PO\u0301RTA"));
    assertEquals("  NBEGN!\tOAZTA  123 😀 ⚠️\n", encode("  porta!\tsonho  123 😀 ⚠️\n"));
    assertEquals("NBEGN-OAZTA", encode("PORTA-SONHO"));
  }

  @Test
  void refusesOnlyMessagesContainingASingleLetter() {
    for (String text : new String[] {"P", "p", "  P  ", "É", "E\u0301", "P!", "1 P 😀"}) {
      var error = assertThrows(CipherInputException.class, () -> encode(text));
      assertEquals("ERRO:\nUM FRAGMENTO NÃO DEFINE O TODO.", error.getMessage());
    }
    assertEquals("", encode(""));
    assertEquals("  123 😀 ⚠️!\n", encode("  123 😀 ⚠️!\n"));
  }

  @Test
  void recoversEveryTwoLetterWordUsingTheSpecifiedInverse() {
    for (char first = 'A'; first <= 'Z'; first++) {
      for (char second = 'A'; second <= 'Z'; second++) {
        String word = "" + first + second;
        assertEquals(word, recover(encode(word)));
      }
    }
  }

  @Test
  void preservesLengthAndInformationAcrossLongerWords() {
    var random = new Random(1042);
    for (int length = 3; length <= 100; length++) {
      var word = new StringBuilder();
      for (int i = 0; i < length; i++) word.append((char) ('A' + random.nextInt(26)));
      String output = encode(word.toString());
      assertEquals(length, output.length());
      assertEquals(word.toString(), recover(output));
    }
  }

  // This independent inverse checks that the omitted first letter can always be recovered.
  private String recover(String encoded) {
    int whole = encoded.charAt(0) - 'A';
    int remainingSum = 0;
    var tail = new StringBuilder();
    for (int i = 1; i < encoded.length(); i++) {
      int original = (encoded.charAt(i) - 'A' + whole) % 26;
      remainingSum += original;
      tail.append((char) ('A' + original));
    }
    return Character.toString((char) ('A' + Math.floorMod(whole - remainingSum, 26))) + tail;
  }
}
