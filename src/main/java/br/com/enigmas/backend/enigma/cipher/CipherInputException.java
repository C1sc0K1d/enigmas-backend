package br.com.enigmas.backend.enigma.cipher;

/** A valid game message that the cipher refuses, rather than an HTTP request failure. */
public final class CipherInputException extends RuntimeException {
  public CipherInputException(String message) {
    super(message);
  }
}
