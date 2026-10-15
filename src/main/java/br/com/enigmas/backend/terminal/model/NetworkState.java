package br.com.enigmas.backend.terminal.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.*;

public class NetworkState {
  public String serverSessionId;
  public int connectionsVersion = 3;
  public Map<String, ComputerSession> computers = new LinkedHashMap<>();
  public String destination;

  public static class ComputerSession {
    public boolean connectionsUnlocked;

    // Kept independently of history so clearing messages never repeats the introduction.
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public boolean connectionIntroduced;
    public String mode = "acordado";
    public String inputFrom;
    public String outputTo;
    public List<Entry> entries = new ArrayList<>();
    public List<String> commands = new ArrayList<>();
    public int count;
    public int lastPhrase = -1;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public TranceProgress trance;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public boolean tranceCompleted;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public TimedResponseProgress timedResponse;
  }

  public static class TimedResponseProgress {
    public String computerId;
    public boolean success;
    public int nextIndex;
    public long nextAt;
    public int entryId;
    public Integer mirroredEntryId;
  }

  public static class TranceProgress {
    public int nextIndex;
    public long nextAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String receiver;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Part(String text, Boolean cipher) {
    public Part(String text) {
      this(text, null);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Entry(
      int id, String text, String output, List<Part> outputParts, boolean system, String source) {}
}
