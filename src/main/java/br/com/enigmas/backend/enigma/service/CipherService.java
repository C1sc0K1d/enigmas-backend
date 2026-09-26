package br.com.enigmas.backend.enigma.service;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CipherService {
  private static final Pattern WORD = Pattern.compile("[a-zA-Z]+");

  public String encode(String cipher, String text) {
    return switch (cipher) {
      case "encodeCaesar" -> caesar(text, 3);
      case "encodePrimordialSource" -> primordialSource(text);
      case "encodeCrawlingChaos" -> words(text, this::crawlingChaos);
      case "encodeAbyssCarriers" -> words(text, this::abyssCarriers);
      case "encodeImpossibleForm" -> impossibleForm(text);
      case "encodeVoraciousFlame" -> flame(text);
      case "weaveText" -> weave(text);
      case "decodeMask" -> words(text, this::mask);
      case "encodeSleeper" -> words(text, w -> caesar(w.substring(1) + w.charAt(0), w.length()));
      case "encodeDeepMist" ->
          words(
              text,
              w -> {
                var result = new StringBuilder();
                for (int i = 0; i < w.length(); i++)
                  result.append(caesar(w.substring(i, i + 1), i + 1));
                return result.reverse().toString();
              });
      case "decodeChaoticCore" -> chaotic(text);
      default -> throw new IllegalArgumentException("Unknown cipher: " + cipher);
    };
  }

  private String impossibleForm(String text) {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ ";
    String normalized =
        Pattern.compile("\\p{IsLatin}\\p{M}*")
            .matcher(text)
            .replaceAll(
                m ->
                    java.text.Normalizer.normalize(m.group(), java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", ""));
    var result = new StringBuilder();
    normalized
        .codePoints()
        .forEach(
            c -> {
              int index = alphabet.indexOf(c >= 'a' && c <= 'z' ? c - 'a' + 'A' : c);
              if (index < 0) result.appendCodePoint(c);
              else {
                int x = index % 3, y = index / 3 % 3, z = index / 9;
                result.append(alphabet.charAt(y + 3 * z + 9 * x));
              }
            });
    return result.toString();
  }

  private String abyssCarriers(String word) {
    var result = new StringBuilder();
    for (int i = 0; i < word.length(); i += 2) {
      char first = word.charAt(i);
      if (i + 1 == word.length()) {
        result.append(caesar(Character.toString(first), 1));
      } else {
        char second = word.charAt(i + 1);
        int distance =
            Math.floorMod(Character.toUpperCase(second) - Character.toUpperCase(first), 26);
        result.append(second);
        result.append((char) ((Character.isLowerCase(first) ? 'a' : 'A') + distance));
      }
    }
    return result.toString();
  }

  private String crawlingChaos(String word) {
    var circle = new ArrayDeque<Character>();
    for (int i = 0; i < word.length(); i++) circle.addLast(word.charAt(i));
    var result = new StringBuilder();
    while (!circle.isEmpty()) {
      circle.addLast(circle.removeFirst());
      result.append(circle.removeFirst());
    }
    return result.toString();
  }

  private String primordialSource(String text) {
    String alphabet = "AZBYCXDWEVFUGTHSIRJQKPLOMN";
    var result = new StringBuilder();
    text.codePoints()
        .forEach(
            c -> {
              if (c >= 'A' && c <= 'Z') result.append(alphabet.charAt(c - 'A'));
              else if (c >= 'a' && c <= 'z')
                result.append(Character.toLowerCase(alphabet.charAt(c - 'a')));
              else result.appendCodePoint(c);
            });
    return result.toString();
  }

  private String words(String text, Function<String, String> transform) {
    return WORD.matcher(text)
        .replaceAll(m -> java.util.regex.Matcher.quoteReplacement(transform.apply(m.group())));
  }

  private String caesar(String text, int shift) {
    var result = new StringBuilder();
    text.codePoints()
        .forEach(
            c -> {
              int base = c >= 'a' && c <= 'z' ? 'a' : c >= 'A' && c <= 'Z' ? 'A' : -1;
              result.appendCodePoint(base < 0 ? c : base + Math.floorMod(c - base + shift, 26));
            });
    return result.toString();
  }

  private String flame(String text) {
    int[] chars = text.codePoints().toArray();
    var result = new StringBuilder();
    for (int pos = 0, burn = 1; pos < chars.length; pos += 1 + burn, burn = burn % 3 + 1)
      result.appendCodePoint(chars[pos]);
    return result.toString();
  }

  private String weave(String text) {
    int[] chars = text.codePoints().toArray();
    var result = new StringBuilder();
    for (int i = 0; i < chars.length; i += 2) result.appendCodePoint(chars[i]);
    for (int i = chars.length % 2 == 0 ? chars.length - 1 : chars.length - 2; i >= 1; i -= 2)
      result.appendCodePoint(chars[i]);
    return result.toString();
  }

  private String mask(String word) {
    var result = new StringBuilder();
    for (int i = 0; i < word.length(); i += 2) {
      char first = word.charAt(i), second = i + 1 < word.length() ? word.charAt(i + 1) : first;
      int start = Character.toUpperCase(first) - 'A';
      int distance = Math.floorMod(Character.toUpperCase(second) - 'A' - start, 26);
      result.append(
          distance <= 1
              ? ' '
              : (char) ((Character.isLowerCase(first) ? 'a' : 'A') + (start + distance / 2) % 26));
    }
    return result.toString();
  }

  private String chaotic(String text) {
    int[] chars = text.codePoints().toArray(), noise = {2, 3, 1}, pulse = {1, 3, 5};
    var core = new StringBuilder();
    for (int pos = 0, group = 0; pos < chars.length; group++) {
      int size = noise[group % 3];
      for (int i = pos; i < Math.min(pos + size, chars.length); i++) core.appendCodePoint(chars[i]);
      pos += size + 1;
    }
    var result = new StringBuilder();
    int letter = 0;
    for (int c : core.toString().codePoints().toArray()) {
      boolean ascii = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
      result.append(
          ascii ? caesar(Character.toString(c), -pulse[letter++ % 3]) : Character.toString(c));
    }
    return result.toString();
  }
}
