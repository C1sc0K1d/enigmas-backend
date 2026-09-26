package br.com.enigmas.backend.shared.exception;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail status(ResponseStatusException exception) {
    return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail invalid(MethodArgumentNotValidException exception) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "Comando inválido. Informe o computador, a sessão e uma mensagem de até 2000 caracteres.");
  }
}
