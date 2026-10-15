package br.com.enigmas.backend.enigma.model;

import java.util.List;

/** Server-only puzzle definition. Never serialize this record from a controller. */
public record ComputerDefinition(
    String id,
    String name,
    String location,
    String serial,
    String welcome,
    String cipher,
    String inputFrom,
    String outputTo,
    Context context,
    List<String> awakePhrases,
    List<String> trancePhrases,
    Boolean commandOnly,
    String firstConnectionMessage,
    Integer firstConnectionLineDelayMs,
    String tranceFinalPhrase) {
  public ComputerDefinition {
    // Existing campaign definitions remain publicly accessible unless explicitly hidden.
    commandOnly = Boolean.TRUE.equals(commandOnly);
  }

  public record Context(
      String riddle,
      String answer,
      List<String> route,
      String successMessage,
      Boolean revealRouteOnLocalAnswer,
      Integer successLineDelayMs) {}
}
