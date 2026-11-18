package acontece.api.modules.evento

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// ROTAS ANTIGAS (obsoletas). O app publicado ainda chama estes caminhos, então elas
// continuam respondendo igual antes. Código novo usa /eventos (EventoController).
// Quando o front migrar, pode apagar esta classe inteira.
@Tag(name = "Eventos (rotas antigas)", description = "Obsoletas: use /eventos. Mantidas só para o app atual.")
@RestController
@RequestMapping("/evento")
class EventoLegadoController(
    private val service: EventoService
) {

    @Operation(summary = "Criar evento", description = "Obsoleta: use POST /eventos.", deprecated = true)
    @PostMapping("/criar")
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(@Valid @RequestBody dto: EventoRequestDTO) = service.criar(dto)

    @Operation(summary = "Listar eventos", description = "Obsoleta: use GET /eventos.", deprecated = true)
    @GetMapping("/listar/")
    fun listar() = service.listar()

    @Operation(summary = "Buscar evento por ID", description = "Obsoleta: use GET /eventos/{id}.", deprecated = true)
    @GetMapping("/buscar/{id}")
    fun buscarPorId(@PathVariable id: Long) = service.buscarPorId(id)

    @Operation(
        summary = "Buscar evento por título",
        description = "Obsoleta: use GET /eventos?titulo=...",
        deprecated = true,
    )
    @GetMapping("/buscar/titulo")
    fun buscarPorTitulo(@RequestParam titulo: String) = service.buscarPorTitulo(titulo)

    @Operation(
        summary = "Buscar eventos por período",
        description = "Obsoleta: use GET /eventos?inicio=...&fim=...",
        deprecated = true,
    )
    @GetMapping("/buscar/periodo")
    fun buscarPorPeriodo(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) inicio: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fim: LocalDateTime,
    ) = service.buscarPorPeriodo(inicio, fim)

    @Operation(summary = "Atualizar evento", description = "Obsoleta: use PUT /eventos/{id}.", deprecated = true)
    @PutMapping("/atualizar/{id}")
    fun atualizar(@PathVariable id: Long, @Valid @RequestBody dto: EventoRequestDTO) = service.atualizar(id, dto)

    @Operation(summary = "Apagar evento", description = "Obsoleta: use DELETE /eventos/{id}.", deprecated = true)
    @DeleteMapping("/deletar/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletar(@PathVariable id: Long) = service.deletar(id)
}
