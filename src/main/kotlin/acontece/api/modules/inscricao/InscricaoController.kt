package acontece.api.modules.inscricao

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import acontece.api.config.ehOrganizador
import acontece.api.config.usuarioId
import acontece.api.enums.StatusInscricao
import jakarta.validation.Valid
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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

@Tag(name = "Inscrições", description = "Inscrição de participantes em eventos")
@RestController
@RequestMapping("/inscricoes")
class InscricaoController(
    private val service: InscricaoService
) {

    @Operation(
        summary = "Inscrever-se em um evento",
        description = "Somente participante. A inscrição é sempre em nome de quem está logado; só o `eventoId` é enviado.",
    )
    // Só participante se inscreve (regra na SecurityConfig), sempre em seu próprio nome.
    @PostMapping
    fun criar(
        @Valid @RequestBody request: InscricaoRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<InscricaoResponse> {
        val criada = service.criar(request.copy(participanteId = jwt.usuarioId))
        val location =
            ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(criada.id)
                .toUri()
        return ResponseEntity.created(location).body(criada)
    }

    @Operation(
        summary = "Buscar inscrição",
        description = "O participante só vê as próprias; o organizador vê todas.",
    )
    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long, @AuthenticationPrincipal jwt: Jwt): InscricaoResponse {
        val inscricao = service.buscarPorId(id)
        if (!jwt.ehOrganizador && inscricao.participanteId != jwt.usuarioId) {
            throw AccessDeniedException("Acesso negado a esta inscrição")
        }
        return inscricao
    }

    @Operation(
        summary = "Listar inscrições",
        description = "O participante recebe só as próprias (o filtro `participanteId` é ignorado); o organizador pode filtrar por evento, participante e status.",
    )
    @GetMapping
    fun listar(
        @RequestParam(required = false) eventoId: Long?,
        @RequestParam(required = false) participanteId: Long?,
        @RequestParam(required = false) status: StatusInscricao?,
        @PageableDefault(size = 20, sort = ["id"]) pageable: Pageable,
        @AuthenticationPrincipal jwt: Jwt,
    ): PagedModel<InscricaoResponse> {
        // Participante é travado no próprio id, ignorando o filtro que mandou.
        val doParticipante = if (jwt.ehOrganizador) participanteId else jwt.usuarioId
        return PagedModel(service.listar(eventoId, doParticipante, status, pageable))
    }

    @Operation(
        summary = "Alterar status da inscrição",
        description = "Somente organizador. Transições: PENDENTE → CONFIRMADA ou CANCELADA; CONFIRMADA → CANCELADA.",
    )
    @PatchMapping("/{id}/status")
    fun atualizarStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: AtualizarStatusRequest
    ): InscricaoResponse = service.atualizarStatus(id, request)

    @Operation(
        summary = "Cancelar a própria inscrição",
        description =
            "Somente participante, e só a própria inscrição (403 se for de outro). Funciona com a inscrição " +
                "PENDENTE ou CONFIRMADA; já cancelada dá 409 e evento encerrado dá 400. A vaga é liberada.",
    )
    // Sem corpo: o novo status é sempre CANCELADA e quem pede é sempre o participante do token.
    @PatchMapping("/{id}/cancelar")
    fun cancelar(@PathVariable id: Long, @AuthenticationPrincipal jwt: Jwt): InscricaoResponse =
        service.cancelarPeloParticipante(id, jwt.usuarioId)
}
