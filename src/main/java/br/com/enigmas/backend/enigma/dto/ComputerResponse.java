package br.com.enigmas.backend.enigma.dto;

import br.com.enigmas.backend.enigma.model.ComputerDefinition;

public record ComputerResponse(
    String id, String name, String location, String serial, String welcome) {
  public static ComputerResponse from(ComputerDefinition computer) {
    return new ComputerResponse(
        computer.id(), computer.name(), computer.location(), computer.serial(), computer.welcome());
  }
}
