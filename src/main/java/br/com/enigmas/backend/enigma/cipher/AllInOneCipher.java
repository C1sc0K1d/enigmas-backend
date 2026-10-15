package br.com.enigmas.backend.enigma.cipher;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Each word carries its total in the first position and its remaining letters as offsets. */
public final class AllInOneCipher {
  private static final Pattern LATIN_LETTER = Pattern.compile("\\p{IsLatin}\\p{M}*");
  private static final Pattern WORD = Pattern.compile("[A-Za-z]+");

  private AllInOneCipher() {}

  public static String encode(String text) {
    String normalized =
        LATIN_LETTER.matcher(text)
            .replaceAll(
                m -> Matcher.quoteReplacement(
                    Normalizer.normalize(m.group(), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")));
    // Single-letter words are valid within a larger message; only a lone fragment is refused.
    long letters =
        normalized.chars().filter(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')).count();
    if (letters == 1)
      throw new CipherInputException("ERRO:\nUM FRAGMENTO NÃO DEFINE O TODO.");
    return WORD.matcher(normalized).replaceAll(m -> encodeWord(m.group().toUpperCase(Locale.ROOT)));
  }

  private static String encodeWord(String word) {
    int whole = 0;
    for (int i = 0; i < word.length(); i++) whole = (whole + word.charAt(i) - 'A') % 26;
    var result = new StringBuilder(word.length());
    result.append((char) ('A' + whole));
    for (int i = 1; i < word.length(); i++)
      result.append((char) ('A' + Math.floorMod(word.charAt(i) - 'A' - whole, 26)));
    return result.toString();
  }
}
