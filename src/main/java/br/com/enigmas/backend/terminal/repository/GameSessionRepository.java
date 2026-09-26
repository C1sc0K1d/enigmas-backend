package br.com.enigmas.backend.terminal.repository;

import br.com.enigmas.backend.terminal.entity.GameSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSession, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from GameSession s where s.id = :id")
  Optional<GameSession> findLocked(@Param("id") String id);
}
