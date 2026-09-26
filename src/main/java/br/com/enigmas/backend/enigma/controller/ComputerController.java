package br.com.enigmas.backend.enigma.controller;

import br.com.enigmas.backend.enigma.dto.ComputerResponse;
import br.com.enigmas.backend.enigma.service.PuzzleCatalog;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/computers")
public class ComputerController {
  private final PuzzleCatalog catalog;

  public ComputerController(PuzzleCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public List<ComputerResponse> all() {
    return catalog.all().stream().map(ComputerResponse::from).toList();
  }
}
