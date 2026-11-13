package acontece.api.modules.inscricao

import acontece.api.enums.StatusInscricao
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

// Recebe a requisição, chama o service e devolve a resposta. As regras ficam no InscricaoService.
@RestController
@RequestMapping("/inscricoes")
class InscricaoController(private val service: InscricaoService) {

    // 201 CREATED + cabeçalho Location apontando para a inscrição recém-criada
    @PostMapping
    fun criar(@Valid @RequestBody request: InscricaoRequest): ResponseEntity<InscricaoResponse> {
        val criada = service.criar(request)
        val location =
            ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(criada.id)
                .toUri()
        return ResponseEntity.created(location).body(criada)
    }

    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long): InscricaoResponse = service.buscarPorId(id)

    // GET /inscricoes?eventoId=2&participanteId=1&status=CONFIRMADA&page=0&size=20
    // Todos os filtros são opcionais. Sem ordenação pedida, ordena por id para a paginação ser estável.
    @GetMapping
    fun listar(
        @RequestParam(required = false) eventoId: Long?,
        @RequestParam(required = false) participanteId: Long?,
        @RequestParam(required = false) status: StatusInscricao?,
        @PageableDefault(size = 20, sort = ["id"]) pageable: Pageable,
    ): PagedModel<InscricaoResponse> =
        PagedModel(service.listar(eventoId, participanteId, status, pageable))

    @PatchMapping("/{id}/status")
    fun atualizarStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: AtualizarStatusRequest,
    ): InscricaoResponse = service.atualizarStatus(id, request)
}
