package acontece.api.modules.usuario

import acontece.api.config.usuarioId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@Tag(name = "Participantes", description = "Cadastro e dados dos participantes")
@RestController
@RequestMapping("/participantes")
class ParticipanteController(
    private val service: ParticipanteService
) {

    @Operation(
        summary = "Cadastrar participante",
        description = "Rota pública. A senha deve ter de 8 a 72 caracteres.",
    )
    @SecurityRequirements
    @PostMapping
    fun criar(@Valid @RequestBody request: ParticipanteRequest):
            ResponseEntity<ParticipanteResponse> {
        val criado = service.criar(request)
        val location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(criado.id)
            .toUri()
        return ResponseEntity.created(location).body(criado)
    }

    @Operation(summary = "Listar participantes", description = "Somente organizador.")
    @GetMapping
    fun listar(pageable: Pageable): PagedModel<ParticipanteResponse> =
        PagedModel(service.listar(pageable))

    // Participante só mexe no próprio cadastro; o organizador mexe em qualquer um.
    // `authentication.name` é o id do usuário (subject do token).
    @Operation(summary = "Buscar participante", description = "O próprio participante ou um organizador.")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZADOR') or #id.toString() == authentication.name")
    fun buscarPorId(@PathVariable id: Long): ParticipanteResponse = service.buscarPorId(id)

    @Operation(summary = "Atualizar participante", description = "O próprio participante ou um organizador. Só os campos enviados mudam.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZADOR') or #id.toString() == authentication.name")
    fun atualizar(
        @PathVariable id: Long,
        @Valid @RequestBody request: ParticipantePatchRequest
    ): ParticipanteResponse = service.atualizar(id, request)

    @Operation(
        summary = "Ativar ou inativar participante",
        description =
            "Somente organizador. `INATIVO` bloqueia a conta: o login responde 403 pedindo para falar com " +
                "o organizador e o token atual passa a dar 401. As inscrições em eventos que ainda não " +
                "acabaram são canceladas e não voltam ao reativar. `ATIVO` libera a conta de novo. " +
                "`EXCLUIDO` não é aceito (400): use o DELETE.",
    )
    // Só organizador (regra na SecurityConfig).
    @PatchMapping("/{id}/status")
    fun alterarStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: AlterarStatusParticipanteRequest,
    ): ParticipanteResponse = service.alterarStatus(id, request)

    @Operation(
        summary = "Excluir participante",
        description =
            "O próprio participante ou um organizador. Não dá para desfazer: nome, e-mail, CPF, telefone e " +
                "senha são apagados, as inscrições em eventos que ainda não acabaram são canceladas e o " +
                "participante passa a dar 404. As inscrições antigas ficam no histórico como " +
                "\"Participante excluído\". O e-mail e o CPF ficam livres para um novo cadastro.",
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZADOR') or #id.toString() == authentication.name")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun excluir(
        @PathVariable id: Long,
        @AuthenticationPrincipal jwt: Jwt,
    ) = service.excluir(id, peloProprioParticipante = jwt.usuarioId == id)
}
