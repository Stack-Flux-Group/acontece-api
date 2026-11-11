package acontece.api.exception

import java.time.Instant

data class ApiError(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val fieldErrors: List<FieldViolation> = emptyList()
)

data class FieldViolation(
    val field: String,
    val message: String
)
