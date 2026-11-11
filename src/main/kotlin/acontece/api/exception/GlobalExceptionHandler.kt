package acontece.api.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException, request: HttpServletRequest):
            ResponseEntity<ApiError> =
        build(HttpStatus.NOT_FOUND, ex.message ?: "Recurso não encontrado", request)

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException, request: HttpServletRequest):
            ResponseEntity<ApiError> =
        build(HttpStatus.CONFLICT, ex.message ?: "Conflito de dados", request)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest):
            ResponseEntity<ApiError> {
        val violations = ex.bindingResult.fieldErrors.map {
            FieldViolation(field = it.field, message = it.defaultMessage ?: "Valor inválido")
        }
        return build(HttpStatus.BAD_REQUEST, "Dados inválidos na requisição", request, violations)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException, request: HttpServletRequest):
            ResponseEntity<ApiError> =
        build(HttpStatus.BAD_REQUEST, "Corpo de requisição ausente ou JSON malformado", request)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException, request: HttpServletRequest):
            ResponseEntity<ApiError> =
        build(HttpStatus.BAD_REQUEST, "Parametro '${ex.name}' com valor inválido", request)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleIntegrity(ex: DataIntegrityViolationException, request: HttpServletRequest):
            ResponseEntity<ApiError> {
        log.warn("Violação de integridade em {}: {}", request.requestURI, ex.mostSpecificCause.message)
        return build(HttpStatus.CONFLICT, "Operação viola uma regra de integridade dos dados", request)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest):
            ResponseEntity<ApiError> {
        if (ex is ErrorResponse) {
            val status = HttpStatus.valueOf(ex.statusCode.value())
            return build(status, ex.body.detail ?: status.reasonPhrase, request)
        }
        log.error("Erro inesperado em {}", request.requestURI, ex)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor", request)
    }

    private fun build(
        status: HttpStatus,
        message: String,
        request: HttpServletRequest,
        violations: List<FieldViolation> = emptyList()
    ): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(
            ApiError(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                path = request.requestURI,
                fieldErrors = violations
            )
        )
}
