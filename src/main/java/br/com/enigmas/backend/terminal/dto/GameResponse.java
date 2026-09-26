package br.com.enigmas.backend.terminal.dto;

import br.com.enigmas.backend.terminal.model.NetworkState;

public record GameResponse(String gameId, long revision, NetworkState state) {}
