package br.com.enigmas.backend.terminal.service;

import br.com.enigmas.backend.enigma.model.ComputerDefinition;
import br.com.enigmas.backend.enigma.service.*;
import br.com.enigmas.backend.terminal.model.NetworkState;
import br.com.enigmas.backend.terminal.model.NetworkState.*;
import java.text.Normalizer;
import java.util.*;
import java.util.function.DoubleSupplier;
import org.springframework.stereotype.Service;

/** Stateless game engine. A service transaction owns each mutable working snapshot. */
@Service
public class TerminalGame {
  private final PuzzleCatalog catalog;
  private final CipherService ciphers;
  private static final String SECRETS =
      "limpa\n"
          + "contexto\n"
          + "entd\n"
          + "sda\n"
          + "cncta_ent\n"
          + "cncta_sda\n"
          + "lbr_ent\n"
          + "lbr_sda\n"
          + "acordado\n"
          + "dormindo\n"
          + "transe\n"
          + "/segredos";

  public TerminalGame(PuzzleCatalog catalog, CipherService ciphers) {
    this.catalog = catalog;
    this.ciphers = ciphers;
  }

  public NetworkState initial(String epoch) {
    var state = new NetworkState();
    state.serverSessionId = epoch;
    for (var c : catalog.all()) {
      var session = new ComputerSession();
      session.inputFrom = c.inputFrom();
      session.outputTo = c.outputTo();
      state.computers.put(c.id(), session);
    }
    return state;
  }

  public NetworkState submit(NetworkState state, String id, String text) {
    return submit(state, id, text, Math::random);
  }

  public NetworkState submit(NetworkState state, String id, String text, DoubleSupplier random) {
    return submit(state, id, text, random, System.currentTimeMillis());
  }

  public NetworkState submit(
      NetworkState state, String id, String text, DoubleSupplier random, long now) {
    if (!catalog.contains(id)) throw new IllegalArgumentException("Unknown computer");
    if (trim(text).isEmpty()) return state;
    var s = state.computers.get(id);
    String command = trim(text).toLowerCase(Locale.ROOT);
    if (command.equals("limpa") || command.equals("/limpar")) {
      s.entries.clear();
      s.commands.clear();
      return state;
    }
    if (isIgnoredTranceInput(state, id, text)) return state;
    s.commands.add(text);
    String[] parts = command.split("(?U)\\s+");
    if (parts.length > 1 && (parts[0].equals("cncta_ent") || parts[0].equals("cncta_sda"))) {
      append(
          s,
          text,
          parts.length > 2
              ? "COMANDO INVÁLIDO."
              : connect(state, id, parts[0].equals("cncta_ent"), parts[1]),
          true,
          null);
      return state;
    }
    switch (command) {
      case "/segredos" -> append(s, text, SECRETS, true, null);
      case "contexto", "dormindo" -> networkCommand(state, id, text, command);
      case "entd", "sda" -> append(s, text, connection(s, command.equals("entd")), true, null);
      case "lbr_ent", "lbr_sda" ->
          append(s, text, connect(state, id, command.equals("lbr_ent"), "nenhum"), true, null);
      case "acordado" -> {
        s.mode = "acordado";
        s.trance = null;
        append(s, text, "MODO ACORDADO.", true, null);
      }
      case "transe" -> {
        s.mode = "transe";
        startTrance(s, now);
        append(s, text, "MODO TRANSE.", true, null);
      }
      default -> {
        var computer = catalog.get(id);
        if (s.mode.equals("transe")) {
          startTrance(s, now);
          append(s, text, "", false, null);
        } else if (s.mode.equals("dormindo")
            && Boolean.TRUE.equals(computer.context().revealRouteOnLocalAnswer())
            && matches(text, computer.context().answer())) {
          append(s, text, route(computer), true, null);
          s.count++;
        } else if (s.mode.equals("acordado")) {
          append(s, text, phrase(state, id, random), false, null);
          s.count++;
        } else transmit(state, id, text, random);
      }
    }
    return state;
  }

  public boolean isIgnoredTranceInput(NetworkState state, String id, String text) {
    var s = state.computers.get(id);
    if (s == null || !s.mode.equals("transe") || s.trance == null) return false;
    String command = trim(text).toLowerCase(Locale.ROOT);
    if (List.of(
            "acordado",
            "dormindo",
            "limpa",
            "/limpar",
            "contexto",
            "entd",
            "sda",
            "lbr_ent",
            "lbr_sda",
            "/segredos")
        .contains(command)) return false;
    String[] parts = command.split("(?U)\\s+");
    return !(parts.length > 1 && (parts[0].equals("cncta_ent") || parts[0].equals("cncta_sda")));
  }

  private void startTrance(ComputerSession s, long now) {
    s.trance = new TranceProgress();
    s.trance.nextAt = now + 2000;
  }

  public boolean advanceTrance(NetworkState state, long now) {
    boolean changed = false;
    for (var entry : state.computers.entrySet()) {
      var s = entry.getValue();
      if (!s.mode.equals("transe") || s.trance == null || now < s.trance.nextAt) continue;
      var phrases = catalog.get(entry.getKey()).trancePhrases();
      append(s, "", phrases.get(s.trance.nextIndex++), false, null);
      s.count++;
      if (s.trance.nextIndex == phrases.size()) s.trance = null;
      else s.trance.nextAt = now + 2000;
      changed = true;
    }
    return changed;
  }

  private String connection(ComputerSession s, boolean input) {
    return (input ? "ENTRADA: " : "SAÍDA CONECTADA: ")
        + Objects.toString(input ? s.inputFrom : s.outputTo, "nenhum");
  }

  private String connect(NetworkState state, String id, boolean input, String target) {
    var s = state.computers.get(id);
    if (!s.connectionsUnlocked) return "Chave não encontrada ou não digitada nos ultimos 30 dias.";
    if (!target.equals("nenhum") && !state.computers.containsKey(target))
      return "CONEXÃO RECUSADA: PC desconhecido.";
    if (target.equals(id)) return "CONEXÃO RECUSADA.";
    String source = input ? (target.equals("nenhum") ? null : target) : id;
    String destination = input ? id : target.equals("nenhum") ? null : target;
    if (source != null) {
      var old = state.computers.get(source);
      if (old.outputTo != null) state.computers.get(old.outputTo).inputFrom = null;
      old.outputTo = null;
    }
    if (destination != null) {
      var old = state.computers.get(destination);
      if (old.inputFrom != null) state.computers.get(old.inputFrom).outputTo = null;
      old.inputFrom = null;
    }
    if (source != null && destination != null) {
      state.computers.get(source).outputTo = destination;
      state.computers.get(destination).inputFrom = source;
    }
    return connection(s, input);
  }

  private void networkCommand(NetworkState state, String origin, String text, String command) {
    var route = new ArrayList<String>();
    String current = origin;
    while (current != null && !route.contains(current)) {
      route.add(current);
      current = state.computers.get(current).outputTo;
    }
    String cycle = current;
    if (command.equals("dormindo")) {
      for (int i = 0; i < route.size(); i++) {
        var s = state.computers.get(route.get(i));
        s.mode = "dormindo";
        s.trance = null;
        if (i > 0) append(s, text, "MODO DORMINDO.", true, route.get(i - 1));
      }
      append(
          state.computers.get(origin),
          text,
          cycle == null ? "MODO DORMINDO." : cycle(cycle),
          true,
          null);
    } else if (cycle != null) append(state.computers.get(origin), text, cycle(cycle), true, null);
    else {
      state.destination = route.getLast();
      var c = catalog.get(state.destination);
      String output = c.context().riddle() + "\n\n" + route(c);
      if (!state.destination.equals(origin))
        append(
            state.computers.get(state.destination),
            text,
            output,
            true,
            route.get(route.size() - 2));
      append(state.computers.get(origin), text, output, true, null);
    }
  }

  private void transmit(NetworkState state, String origin, String text, DoubleSupplier random) {
    var visited = new ArrayList<String>();
    var trace = new ArrayList<Trace>();
    String current = origin, source = null, payload = text, notice = "";
    while (current != null) {
      if (visited.contains(current)) {
        notice = cycle(current);
        break;
      }
      visited.add(current);
      var c = catalog.get(current);
      var s = state.computers.get(current);
      var context = c.context();
      if (s.mode.equals("transe")) {
        notice = "TRANSMISSÃO INTERROMPIDA: " + current + " está em transe.";
        break;
      }
      boolean awake = s.mode.equals("acordado");
      String output = awake ? phrase(state, current, random) : ciphers.encode(c.cipher(), payload);
      trace.add(new Trace(current, output, !awake));
      s.count++;
      boolean correctRoute = visited.equals(context.route());
      boolean recognized = !awake && matches(output, context.answer());
      boolean solved = recognized && correctRoute;
      String response =
          recognized
              ? solved
                  ? Objects.requireNonNullElse(
                      context.successMessage(), "DESTINO ALCANÇADO: " + current + ".")
                  : route(c)
              : "";
      if (solved) s.connectionsUnlocked = true;
      if (!current.equals(origin)) {
        var outputParts = new ArrayList<Part>();
        outputParts.add(new Part(output, !awake));
        if (!response.isEmpty()) outputParts.add(new Part("\n" + response));
        append(s, payload, outputParts, false, source);
      }
      if (awake) {
        notice = "TRANSMISSÃO INTERROMPIDA: " + current + " está acordado.";
        break;
      }
      if (recognized) {
        notice = response;
        break;
      }
      if (current.equals(state.destination)) {
        notice =
            !correctRoute
                ? "TRANSMISSÃO INTERROMPIDA: percurso inválido."
                : context.successMessage() != null
                    ? "TRANSMISSÃO INTERROMPIDA: resposta inválida."
                    : "DESTINO ALCANÇADO: " + current + ".";
        break;
      }
      source = current;
      current = s.outputTo;
      payload = output;
      if (current == null && state.destination != null)
        notice = "TRANSMISSÃO INTERROMPIDA: " + source + " está sem saída.";
    }
    var output = new ArrayList<Part>();
    for (int i = 0; i < trace.size(); i++) {
      var t = trace.get(i);
      if (trace.size() > 1) output.add(new Part((i > 0 ? "\n" : "") + t.id + ": "));
      output.add(new Part(t.text, t.cipher));
    }
    if (!notice.isEmpty())
      output.add(
          new Part((output.stream().anyMatch(p -> !p.text().isEmpty()) ? "\n" : "") + notice));
    if (notice.startsWith("TRANSMISSÃO INTERROMPIDA:"))
      append(state.computers.get(origin), text, notice, false, null);
    else append(state.computers.get(origin), text, output, false, null);
  }

  private record Trace(String id, String text, boolean cipher) {}

  private String cycle(String id) {
    return "TRANSMISSÃO INTERROMPIDA: ciclo detectado em " + id + ".";
  }

  private boolean matches(String actual, String expected) {
    return normalize(actual).equals(normalize(expected));
  }

  private String normalize(String text) {
    return trim(Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", ""))
        .toUpperCase(Locale.ROOT);
  }

  private String trim(String text) {
    return text.replaceAll("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$", "");
  }

  private String route(ComputerDefinition c) {
    var r = c.context().route();
    var lines = new ArrayList<String>();
    lines.add("INÍCIO: " + r.getFirst());
    lines.add("PERCURSO: " + r.size() + " computador" + (r.size() == 1 ? "" : "es") + ".");
    for (int i = 0; i < r.size(); i++) lines.add((i + 1) + ". " + r.get(i));
    lines.add("DESTINO: " + c.name());
    return String.join("\n", lines);
  }

  private String phrase(NetworkState state, String id, DoubleSupplier random) {
    var s = state.computers.get(id);
    var phrases = catalog.get(id).awakePhrases();
    var choices = new ArrayList<Integer>();
    for (int i = 0; i < phrases.size(); i++) if (i != s.lastPhrase) choices.add(i);
    s.lastPhrase =
        choices.isEmpty() ? 0 : choices.get((int) (random.getAsDouble() * choices.size()));
    return phrases.get(s.lastPhrase);
  }

  private void append(
      ComputerSession s, String text, String output, boolean system, String source) {
    s.entries.add(
        new Entry(
            s.entries.isEmpty() ? 0 : s.entries.getLast().id() + 1,
            text,
            output,
            null,
            system,
            source));
  }

  private void append(
      ComputerSession s, String text, List<Part> parts, boolean system, String source) {
    s.entries.add(
        new Entry(
            s.entries.isEmpty() ? 0 : s.entries.getLast().id() + 1,
            text,
            parts.stream().map(Part::text).reduce("", String::concat),
            parts,
            system,
            source));
  }
}
