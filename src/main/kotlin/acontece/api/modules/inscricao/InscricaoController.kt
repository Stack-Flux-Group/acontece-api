package acontece.api.modules.inscricao

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

@RestController
@RequestMapping("/inscricoes")
class InscricaoController(
    private val service: InscricaoService
) {

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

    // Participante só enxerga as próprias inscrições; o organizador enxerga todas.
    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long, @AuthenticationPrincipal jwt: Jwt): InscricaoResponse {
        val inscricao = service.buscarPorId(id)
        if (!jwt.ehOrganizador && inscricao.participanteId != jwt.usuarioId) {
            throw AccessDeniedException("Acesso negado a esta inscrição")
        }
        return inscricao
    }

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

    @PatchMapping("/{id}/status")
    fun atualizarStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: AtualizarStatusRequest
    ): InscricaoResponse = service.atualizarStatus(id, request)
}
