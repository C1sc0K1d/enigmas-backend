package br.com.enigmas.backend.terminal.service;

import br.com.enigmas.backend.enigma.service.PuzzleCatalog;
import br.com.enigmas.backend.terminal.model.NetworkState;
import br.com.enigmas.backend.terminal.model.NetworkState.*;
import java.util.*;

/** Persists delivery progress so refreshes and additional browsers cannot skip or replay lines. */
final class TimedResponses {
  private final PuzzleCatalog catalog;

  TimedResponses(PuzzleCatalog catalog) {
    this.catalog = catalog;
  }

  String start(NetworkState state, String origin, String computerId, boolean success, long now) {
    var pc = catalog.get(computerId);
    String message = success ? pc.context().successMessage() : pc.firstConnectionMessage();
    Integer delay = success ? pc.context().successLineDelayMs() : pc.firstConnectionLineDelayMs();
    var lines = lines(message);
    if (delay == null || delay <= 0 || lines.length <= 1) return message;
    var caller = state.computers.get(origin);
    var progress = new TimedResponseProgress();
    progress.computerId = computerId;
    progress.success = success;
    progress.nextIndex = 1;
    progress.nextAt = now + delay;
    progress.entryId = nextEntryId(caller);
    if (success && !origin.equals(computerId))
      progress.mirroredEntryId = nextEntryId(state.computers.get(computerId));
    caller.timedResponse = progress;
    return lines[0];
  }

  boolean advance(NetworkState state, long now) {
    boolean changed = false;
    for (var session : state.computers.values()) {
      var progress = session.timedResponse;
      if (progress == null || now < progress.nextAt) continue;
      var pc = catalog.get(progress.computerId);
      String message = progress.success ? pc.context().successMessage() : pc.firstConnectionMessage();
      int delay = progress.success ? pc.context().successLineDelayMs() : pc.firstConnectionLineDelayMs();
      var lines = lines(message);
      String line = lines[progress.nextIndex++];
      extend(session, progress.entryId, line);
      if (progress.mirroredEntryId != null)
        extend(state.computers.get(progress.computerId), progress.mirroredEntryId, line);
      if (progress.nextIndex == lines.length) session.timedResponse = null;
      else progress.nextAt = now + delay; // A delayed server never releases several lines in a burst.
      changed = true;
    }
    return changed;
  }

  private String[] lines(String message) {
    // A blank separator belongs to the preceding line and does not add another timed step.
    return message.split("\\n(?=\\S)");
  }

  private int nextEntryId(ComputerSession session) {
    return session.entries.isEmpty() ? 0 : session.entries.getLast().id() + 1;
  }

  private void extend(ComputerSession session, int entryId, String line) {
    for (int i = 0; i < session.entries.size(); i++) {
      var entry = session.entries.get(i);
      if (entry.id() != entryId) continue;
      List<Part> parts = null;
      if (entry.outputParts() != null) {
        parts = new ArrayList<>(entry.outputParts());
        parts.add(new Part("\n" + line));
      }
      session.entries.set(i, new Entry(entry.id(), entry.text(), entry.output() + "\n" + line,
          parts, entry.system(), entry.source()));
      return;
    }
  }
}
