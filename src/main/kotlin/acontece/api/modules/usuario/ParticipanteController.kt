package acontece.api.modules.usuario

import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

@RestController
@RequestMapping("/participantes")
class ParticipanteController(
    private val service: ParticipanteService
) {

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

    @GetMapping
    fun listar(pageable: Pageable): PagedModel<ParticipanteResponse> =
        PagedModel(service.listar(pageable))

    @PatchMapping
    fun atualizar(
        @PathVariable id: Long,
        @Valid @RequestBody request: ParticipantePatchRequest
    ): ParticipanteResponse = service.atualizar(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun excluir(@PathVariable id: Long) = service.excluir(id)
}
