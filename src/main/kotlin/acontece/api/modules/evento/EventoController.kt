package acontece.api.modules.evento

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

// Funções: Recebe requisição / Chama o Service / Devolve resposta
// Rotas no padrão REST, iguais às de /participantes e /inscricoes: o recurso no plural e o verbo
// HTTP dizendo a ação (POST cria, GET lê, PUT substitui, DELETE apaga).
@Tag(name = "Eventos", description = "Qualquer usuário logado consulta; só o organizador administra")
@RestController
@RequestMapping("/eventos")
class EventoController(
    private val service: EventoService // spring injeta o service sozinho
) {

    // POST /eventos -> 201 Created, com o endereço do evento novo no cabeçalho Location.
    // @Valid dispara as validações do DTO (@NotBlank, @Pattern...)
    @Operation(summary = "Criar evento", description = "Somente organizador. O endereço é preenchido pelo CEP.")
    @PostMapping
    fun criar(@Valid @RequestBody dto: EventoRequestDTO): ResponseEntity<EventoResponseDTO> {
        val criado = service.criar(dto)
        val location =
            ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id).toUri()
        return ResponseEntity.created(location).body(criado)
    }

    // GET /eventos?titulo=semana&inicio=2026-11-01T00:00:00&fim=2026-12-31T23:59:59
    // Todos os filtros são opcionais e se somam. @DateTimeFormat converte o texto da URL em data.
    @Operation(
        summary = "Listar e buscar eventos",
        description =
            "Sem filtros, devolve todos. `titulo` busca por parte do título (sem diferenciar maiúsculas); " +
                "`inicio` e `fim` filtram pela data de início do evento. Ordenado pela data de início.",
    )
    @GetMapping
    fun listar(
        @Parameter(description = "Parte do título") @RequestParam(required = false) titulo: String?,
        @Parameter(description = "Começa a partir de (ex.: 2026-12-01T00:00:00)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        inicio: LocalDateTime?,
        @Parameter(description = "Começa até (ex.: 2026-12-31T23:59:59)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        fim: LocalDateTime?,
    ): List<EventoResponseDTO> = service.buscar(titulo, inicio, fim)

    @Operation(summary = "Buscar evento por ID")
    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long): EventoResponseDTO = service.buscarPorId(id)

    @Operation(summary = "Atualizar evento", description = "Somente organizador. Substitui todos os dados.")
    @PutMapping("/{id}")
    fun atualizar(@PathVariable id: Long, @Valid @RequestBody dto: EventoRequestDTO): EventoResponseDTO =
        service.atualizar(id, dto)

    // 204 No Content: sucesso sem corpo de resposta
    @Operation(summary = "Apagar evento", description = "Somente organizador.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletar(@PathVariable id: Long) = service.deletar(id)
}
