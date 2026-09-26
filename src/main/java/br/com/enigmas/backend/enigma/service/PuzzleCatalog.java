package br.com.enigmas.backend.enigma.service;

import br.com.enigmas.backend.enigma.model.ComputerDefinition;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PuzzleCatalog {
  private final List<ComputerDefinition> computers;

  public PuzzleCatalog(JsonMapper mapper) throws IOException {
    try (var stream = new ClassPathResource("puzzles/computers.json").getInputStream()) {
      computers = List.of(mapper.readValue(stream, ComputerDefinition[].class));
    }
    var ids = new HashSet<String>();
    for (var computer : computers) {
      if (!ids.add(computer.id())
          || computer.awakePhrases().isEmpty()
          || computer.trancePhrases() == null
          || computer.trancePhrases().size() != 6)
        throw new IllegalStateException("Invalid puzzle catalog");
    }
    for (var c : computers) {
      if (!ids.containsAll(c.context().route()) || !c.context().route().getLast().equals(c.id()))
        throw new IllegalStateException("Invalid puzzle route: " + c.id());
    }
  }

  public List<ComputerDefinition> all() {
    return computers;
  }

  public ComputerDefinition get(String id) {
    return computers.stream()
        .filter(c -> c.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown computer"));
  }

  public boolean contains(String id) {
    return computers.stream().anyMatch(c -> c.id().equals(id));
  }
}
