package br.com.enigmas.backend.terminal.controller;

import static org.junit.jupiter.api.Assertions.*;

import br.com.enigmas.backend.terminal.repository.GameSessionRepository;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "master.reset-token=test-master-only")
@ActiveProfiles("test")
class GameApiTests {
  @LocalServerPort int port;
  @Autowired JsonMapper mapper;
  @Autowired GameSessionRepository repository;
  @Autowired br.com.enigmas.backend.terminal.service.GameSessionService sessions;
  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> request(String method, String path, String id, Object body)
      throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path));
    if (id != null) builder.header("X-Game-Session", id);
    builder.header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode json(HttpResponse<String> response) {
    return mapper.readTree(response.body());
  }

  private String nextEvent(java.io.BufferedReader reader) throws Exception {
    return java.util.concurrent.CompletableFuture.supplyAsync(
            () -> {
              try {
                String line;
                while ((line = reader.readLine()) != null) {
                  if (line.startsWith("event:")) return line;
                }
                throw new IllegalStateException("Stream ended");
              } catch (java.io.IOException error) {
                throw new java.io.UncheckedIOException(error);
              }
            })
        .get(5, java.util.concurrent.TimeUnit.SECONDS);
  }

  @Test
  void trancePersistsBroadcastsIgnoresStaleInputAndStopsOnReset() throws Exception {
    var game = json(request("POST", "/games", null, null));
    String id = game.get("gameId").asString();
    var command = new LinkedHashMap<String, Object>();
    command.put("computerId", "h_colinas");
    command.put("text", "transe");
    command.put("revision", game.get("revision").asLong());
    command.put("requestId", UUID.randomUUID().toString());
    command.put("serverSessionId", game.at("/state/serverSessionId").asString());
    var started = json(request("POST", "/games/current/commands", id, command));
    long due = started.at("/state/computers/h_colinas/trance/nextAt").asLong();
    var stream =
        http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/games/events"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofInputStream());
    try (var reader =
        new java.io.BufferedReader(
            new java.io.InputStreamReader(
                stream.body(), java.nio.charset.StandardCharsets.UTF_8))) {
      assertEquals("event:changed", nextEvent(reader));
      sessions.advanceTrance(due);
      assertEquals("event:changed", nextEvent(reader));
      var latest = json(request("GET", "/games/current", id, null));
      assertEquals(1, latest.at("/state/computers/h_colinas/trance/nextIndex").asInt());
      assertEquals(
          "Você parece muito seguro daquilo que está vendo.",
          latest.at("/state/computers/h_colinas/entries/1/output").asString());
      command.put("requestId", UUID.randomUUID().toString());
      command.put("text", "ignore this even with an old revision");
      var ignored = request("POST", "/games/current/commands", id, command);
      assertEquals(200, ignored.statusCode());
      assertEquals(latest, json(ignored));
      var reset = mapper.readTree(mapper.writeValueAsString(sessions.reset()));
      assertEquals("event:changed", nextEvent(reader));
      sessions.advanceTrance(due + 100000);
      assertEquals(reset, json(request("GET", "/games/current", id, null)));
    } finally {
      var row = repository.findById(id).orElseThrow();
      row.epoch = UUID.randomUUID().toString();
      repository.saveAndFlush(row);
    }
  }

  @Test
  void masterResetRestoresInitialStateAndRejectsCommandsFromPreviousCycle() throws Exception {
    var game = json(request("POST", "/games", null, null));
    String id = game.get("gameId").asString();
    var command = new LinkedHashMap<String, Object>();
    command.put("computerId", "chma_vva");
    command.put("text", "dormindo");
    command.put("requestId", UUID.randomUUID().toString());
    command.put("revision", game.get("revision").asLong());
    command.put("serverSessionId", game.at("/state/serverSessionId").asString());
    assertEquals(200, request("POST", "/games/current/commands", id, command).statusCode());
    var before = json(request("GET", "/games/current", id, null));
    assertEquals(403, request("POST", "/master/reset", null, null).statusCode());
    assertEquals(before, json(request("GET", "/games/current", id, null)));
    var stream =
        http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/games/events"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofInputStream());
    try (var reader =
        new java.io.BufferedReader(
            new java.io.InputStreamReader(
                stream.body(), java.nio.charset.StandardCharsets.UTF_8))) {
      assertEquals("event:changed", nextEvent(reader));
      var resetResponse =
          http.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/master/reset"))
                  .header("X-Master-Token", "test-master-only")
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(200, resetResponse.statusCode());
      var reset = json(resetResponse);
      assertEquals("event:changed", nextEvent(reader));
      assertEquals(0, reset.get("revision").asLong());
      assertNotEquals(game.at("/state/serverSessionId"), reset.at("/state/serverSessionId"));
      for (var pc : reset.get("state").get("computers")) {
        assertEquals("acordado", pc.get("mode").asString());
        assertFalse(pc.get("connectionsUnlocked").asBoolean());
        assertTrue(pc.get("entries").isEmpty());
        assertTrue(pc.get("commands").isEmpty());
      }
      assertEquals("tec_la", reset.at("/state/computers/chma_vva/outputTo").asString());
      assertEquals("fnt_primdal", reset.at("/state/computers/tec_la/outputTo").asString());
      command.put("revision", 0);
      assertEquals(409, request("POST", "/games/current/commands", id, command).statusCode());
      assertEquals(reset, json(request("GET", "/games/current", id, null)));
      command.put("serverSessionId", reset.at("/state/serverSessionId").asString());
      command.put("requestId", UUID.randomUUID().toString());
      assertEquals(200, request("POST", "/games/current/commands", id, command).statusCode());
    } finally {
      var row = repository.findById(id).orElseThrow();
      row.epoch = UUID.randomUUID().toString();
      repository.saveAndFlush(row);
    }
  }

  @Test
  void streamsCommittedChangesAndSerializesConcurrentCommands() throws Exception {
    var game = json(request("POST", "/games", null, null));
    String id = game.get("gameId").asString();
    var streamRequest =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/games/events"))
            .GET()
            .build();
    var stream = http.send(streamRequest, HttpResponse.BodyHandlers.ofInputStream());
    assertEquals(200, stream.statusCode());
    assertTrue(
        stream.headers().firstValue("Content-Type").orElse("").contains("text/event-stream"));
    try (var reader =
        new java.io.BufferedReader(
            new java.io.InputStreamReader(
                stream.body(), java.nio.charset.StandardCharsets.UTF_8))) {
      assertEquals("event:changed", nextEvent(reader));
      var requests = new ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
      for (int i = 0; i < 2; i++) {
        var command =
            Map.of(
                "computerId",
                "tec_la",
                "text",
                "contexto",
                "requestId",
                UUID.randomUUID().toString(),
                "revision",
                game.get("revision").asLong(),
                "serverSessionId",
                game.at("/state/serverSessionId").asString());
        requests.add(
            http.sendAsync(
                HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/games/current/commands"))
                    .header("X-Game-Session", id)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(command)))
                    .build(),
                HttpResponse.BodyHandlers.ofString()));
      }
      var statuses = new ArrayList<Integer>();
      for (var pending : requests)
        statuses.add(pending.get(5, java.util.concurrent.TimeUnit.SECONDS).statusCode());
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
      assertEquals("event:changed", nextEvent(reader));
      var latest = json(request("GET", "/games/current", id, null));
      assertEquals(game.get("revision").asLong() + 1, latest.get("revision").asLong());
      assertEquals(latest, json(request("POST", "/games", null, null)));
    } finally {
      var row = repository.findById(id).orElseThrow();
      row.epoch = UUID.randomUUID().toString();
      repository.saveAndFlush(row);
    }
  }

  @Test
  void catalogDoesNotExposeSolutions() throws Exception {
    var response = request("GET", "/computers", null, null);
    assertEquals(200, response.statusCode());
    assertEquals(10, json(response).size());
    for (var c : json(response)) {
      assertFalse(c.has("context"));
      assertFalse(c.has("answer"));
      assertFalse(c.has("cipher"));
      assertFalse(c.has("awakePhrases"));
      assertFalse(c.has("trancePhrases"));
    }
  }

  @Test
  void sharesProgressBetweenClientsAndRejectsStaleCommands() throws Exception {
    var created = request("POST", "/games", null, null);
    assertEquals(201, created.statusCode());
    var game = json(created);
    String id = game.get("gameId").asString();
    var other = json(request("POST", "/games", null, null));
    assertEquals(id, other.get("gameId").asString());
    var command = new LinkedHashMap<String, Object>();
    command.put("computerId", "chma_vva");
    command.put("text", "dormindo");
    command.put("requestId", UUID.randomUUID().toString());
    command.put("revision", 0);
    command.put("serverSessionId", game.get("state").get("serverSessionId").asString());
    var sent = request("POST", "/games/current/commands", id, command);
    assertEquals(200, sent.statusCode());
    assertEquals("dormindo", json(sent).at("/state/computers/tec_la/mode").asString());
    assertEquals(
        json(sent),
        json(request("POST", "/games/current/commands", id, command)),
        "retry must not execute twice");
    assertEquals(json(sent), json(request("GET", "/games/current", id, null)));
    assertTrue(repository.findById(id).orElseThrow().snapshot.contains("dormindo"));
    assertEquals(
        "dormindo",
        json(request("GET", "/games/current", other.get("gameId").asString(), null))
            .at("/state/computers/chma_vva/mode")
            .asString());
    command.put("requestId", UUID.randomUUID().toString());
    assertEquals(409, request("POST", "/games/current/commands", id, command).statusCode());
    command.put("text", "x".repeat(2001));
    assertEquals(400, request("POST", "/games/current/commands", id, command).statusCode());
    assertEquals(
        404, request("GET", "/games/current", UUID.randomUUID().toString(), null).statusCode());
    // Simulate a stored snapshot from a previous server process.
    var row = repository.findById(id).orElseThrow();
    row.epoch = UUID.randomUUID().toString();
    repository.saveAndFlush(row);
    var reset = json(request("GET", "/games/current", id, null));
    assertEquals(0, reset.get("revision").asLong());
    assertEquals("acordado", reset.at("/state/computers/chma_vva/mode").asString());
    assertTrue(reset.at("/state/computers/chma_vva/entries").isEmpty());
  }
}
