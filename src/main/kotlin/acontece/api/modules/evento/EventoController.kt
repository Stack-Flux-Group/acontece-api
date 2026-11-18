package acontece.api.modules.evento

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.time.LocalDateTime
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
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

// O controller só recebe a requisição, chama o service e devolve a resposta.
// Mesmo padrão de /participantes e /inscricoes: recurso no plural e o verbo HTTP
// dizendo a ação (POST cria, GET lê, PUT substitui, DELETE apaga).
@Tag(name = "Eventos", description = "Qualquer usuário logado consulta; só o organizador administra")
@RestController
@RequestMapping("/eventos")
class EventoController(
    private val service: EventoService // spring injeta o service sozinho
) {

    // POST /eventos -> 201, com o link do evento novo no header Location.
    // @Valid roda as validações do DTO (@NotBlank, @Pattern...)
    @Operation(summary = "Criar evento", description = "Somente organizador. O endereço é preenchido pelo CEP.")
    @PostMapping
    fun criar(@Valid @RequestBody dto: EventoRequestDTO): ResponseEntity<EventoResponseDTO> {
        val criado = service.criar(dto)
        val location =
            ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id).toUri()
        return ResponseEntity.created(location).body(criado)
    }

    // GET /eventos?titulo=semana&inicio=2026-11-01T00:00:00&fim=2026-12-31T23:59:59&page=0&size=20
    // Filtros opcionais e se somam. @DateTimeFormat converte o texto da URL em data.
    // Resposta paginada igual a /participantes e /inscricoes: {content, page}.
    @Operation(
        summary = "Listar e buscar eventos",
        description =
            "Paginado (`page` começa em 0, `size` padrão 20 e máximo 100). Sem filtros, devolve todos. " +
                "`titulo` busca por parte do título (sem diferenciar maiúsculas); `inicio` e `fim` filtram " +
                "pela data de início do evento. Ordem padrão: data de início; `sort` aceita dataInicio, " +
                "dataFim, titulo, vagas e id (ex.: `sort=titulo,desc`).",
    )
    @GetMapping
    fun listar(
        @ParameterObject @PageableDefault(size = 20) pageable: Pageable,
        @Parameter(description = "Parte do título") @RequestParam(required = false) titulo: String?,
        @Parameter(description = "Começa a partir de (ex.: 2026-12-01T00:00:00)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        inicio: LocalDateTime?,
        @Parameter(description = "Começa até (ex.: 2026-12-31T23:59:59)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        fim: LocalDateTime?,
    ): PagedModel<EventoResponseDTO> = PagedModel(service.buscar(titulo, inicio, fim, pageable))

    @Operation(summary = "Buscar evento por ID")
    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long): EventoResponseDTO = service.buscarPorId(id)

    @Operation(summary = "Atualizar evento", description = "Somente organizador. Substitui todos os dados.")
    @PutMapping("/{id}")
    fun atualizar(@PathVariable id: Long, @Valid @RequestBody dto: EventoRequestDTO): EventoResponseDTO =
        service.atualizar(id, dto)

    // 204: deu certo e não devolve nada no corpo
    @Operation(summary = "Apagar evento", description = "Somente organizador.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletar(@PathVariable id: Long) = service.deletar(id)
}
