package br.com.khawantech.files.transferencia.exception;

import br.com.khawantech.files.auth.exception.AuthenticationException;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice(basePackages = "br.com.khawantech.files.transferencia")
public class TransferenciaExceptionHandler {

    @ExceptionHandler(SessaoNaoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleSessaoNaoEncontrada(SessaoNaoEncontradaException ex) {
        log.warn("Sessão não encontrada: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .error("Not Found")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(SessaoExpiradaException.class)
    public ResponseEntity<ErrorResponse> handleSessaoExpirada(SessaoExpiradaException ex) {
        log.warn("Sessão expirada: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.GONE.value())
            .error("Gone")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.GONE).body(error);
    }

    @ExceptionHandler(SessaoLotadaException.class)
    public ResponseEntity<ErrorResponse> handleSessaoLotada(SessaoLotadaException ex) {
        log.warn("Sessão lotada: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.CONFLICT.value())
            .error("Conflict")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(LimiteArquivosExcedidoException.class)
    public ResponseEntity<ErrorResponse> handleLimiteArquivosExcedido(LimiteArquivosExcedidoException ex) {
        log.warn("Limite de arquivos excedido: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.TOO_MANY_REQUESTS.value())
            .error("Too Many Requests")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    @ExceptionHandler(ArquivoMuitoGrandeException.class)
    public ResponseEntity<ErrorResponse> handleArquivoMuitoGrande(ArquivoMuitoGrandeException ex) {
        log.warn("Arquivo muito grande: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
            .error("Payload Too Large")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    @ExceptionHandler(ChunkInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleChunkInvalido(ChunkInvalidoException ex) {
        log.warn("Chunk inválido: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HashInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleHashInvalido(HashInvalidoException ex) {
        log.warn("Hash inválido: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ConversaoNaoSuportadaException.class)
    public ResponseEntity<ErrorResponse> handleConversaoNaoSuportada(ConversaoNaoSuportadaException ex) {
        log.warn("Conversão não suportada: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(EspacoSessaoInsuficienteException.class)
    public ResponseEntity<ErrorResponse> handleEspacoSessaoInsuficiente(EspacoSessaoInsuficienteException ex) {
        log.warn("Espaço insuficiente na sessão: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.TOO_MANY_REQUESTS.value())
            .error("Too Many Requests")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .error("Not Found")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Error")
            .message("Erro de validação nos campos informados")
            .timestamp(Instant.now())
            .validationErrors(validationErrors)
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }


    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.UNAUTHORIZED.value())
            .error("Unauthorized")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Erro inesperado: {}", ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @Data
    @Builder
    public static class ErrorResponse {
        private int status;
        private String error;
        private String message;
        private Instant timestamp;
        private Map<String, String> validationErrors;
    }
}
