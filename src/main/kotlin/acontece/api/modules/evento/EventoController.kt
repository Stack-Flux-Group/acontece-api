package acontece.api.modules.evento

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

// Funções: Recebe requisição / Chama o Service / Devolve resposta

// @RestController - Define que a classe é um controlador REST
// @RequestMapping - Define o path base para todas as rotas do controlador
@Tag(name = "Eventos", description = "Qualquer usuário logado consulta; só o organizador administra")
@RestController
@RequestMapping("/evento")
class EventoController(
    private val service: EventoService // spring injeta o service sozinho
) {

    // POST /eventos -> cria evento. 201 CREATED se der certo.
    // @Valid dispara as validações do RequestDTO (@NotBlank, @Pattern...)
    // @RequestBody transforma o JSON enviado no objeto dto
    @Operation(summary = "Criar evento", description = "Somente organizador. O endereço é preenchido pelo CEP.")
    @PostMapping("/criar")
    @ResponseStatus(HttpStatus.CREATED)
    fun criar(@Valid @RequestBody dto: EventoRequestDTO) = service.criar(dto)

    // GET Listar todos os eventos
    @Operation(summary = "Listar eventos")
    @GetMapping("/listar/") fun listar() = service.listar()

    // GET Busca evento por ID
    @Operation(summary = "Buscar evento por ID")
    @GetMapping("/buscar/{id}") fun buscarPorId(@PathVariable id: Long) = service.buscarPorId(id)

    // GET Busca evento por Titulo
    // ?titulo=xxx vira @RequestParam
    @Operation(summary = "Buscar evento por título")
    @GetMapping("/buscar/titulo")
    fun buscarPorTitulo(@RequestParam titulo: String) = service.buscarPorTitulo(titulo)

    // GET /eventos/periodo?inicio=2026-11-01T00:00:00&fim=2026-12-31T23:59:59
    // @DateTimeFormat diz ao Spring como converter o texto da URL em data
    @Operation(summary = "Buscar eventos por período", description = "Datas no formato 2026-12-01T19:00:00.")
    @GetMapping("/buscar/periodo")
    fun buscarPorPeriodo(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) inicio: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fim: LocalDateTime,
    ) = service.buscarPorPeriodo(inicio, fim)

    // PUT /eventos/5 -> atualiza todos os dados do evento
    @Operation(summary = "Atualizar evento", description = "Somente organizador. Substitui todos os dados.")
    @PutMapping("/atualizar/{id}")
    fun atualizar(@PathVariable id: Long, @Valid @RequestBody dto: EventoRequestDTO) =
        service.atualizar(id, dto)

    // DELETE /eventos/5 -> remove. 204 NO CONTENT (sucesso sem corpo de resposta)
    @Operation(summary = "Apagar evento", description = "Somente organizador.")
    @DeleteMapping("/deletar/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletar(@PathVariable id: Long) = service.deletar(id)
}
