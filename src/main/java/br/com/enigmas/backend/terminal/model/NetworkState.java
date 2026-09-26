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
    public String mode = "acordado";
    public String inputFrom;
    public String outputTo;
    public List<Entry> entries = new ArrayList<>();
    public List<String> commands = new ArrayList<>();
    public int count;
    public int lastPhrase = -1;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public TranceProgress trance;
  }

  public static class TranceProgress {
    public int nextIndex;
    public long nextAt;
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
