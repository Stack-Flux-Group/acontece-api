package acontece.api.modules.evento

import acontece.api.client.ViaCepClient
import acontece.api.exception.ResourceNotFoundException
import java.time.LocalDateTime
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventoService(
    private val repository: EventoRepository,
    private val viaCepClient: ViaCepClient,
) {

    // Leitura

    fun listar(): List<EventoResponseDTO> = repository.findAll().map { it.toResponse() }

    fun buscarPorId(id: Long): EventoResponseDTO = buscarEntidade(id).toResponse()

    fun buscarPorTitulo(titulo: String): List<EventoResponseDTO> =
        repository.findByTituloContainingIgnoreCase(titulo).map { it.toResponse() }

    fun buscarPorPeriodo(inicio: LocalDateTime, fim: LocalDateTime): List<EventoResponseDTO> {
        // require: se a condição for falsa, lança illegalargumentexception com 400
        require(!fim.isBefore(inicio)) {
            "A data final do filtro deve ser igual ou posterior à inicial"
        }
        return repository.buscarPorPeriodo(inicio, fim).map { it.toResponse() }
    }

    // Escrita
    // @Transactional: se algo falhar no meio, nada é gravado no banco

    @Transactional
    fun criar(dto: EventoRequestDTO): EventoResponseDTO {
        validarDatas(dto)
        val evento =
            Evento(
                titulo = dto.titulo,
                descricao = dto.descricao,
                dataInicio = dto.dataInicio,
                dataFim = dto.dataFim,
                vagas = dto.vagas,
                endereco = buscarEndereco(dto.cep, dto.numero),
            )
        return repository.save(evento).toResponse()
    }

    @Transactional
    fun atualizar(id: Long, dto: EventoRequestDTO): EventoResponseDTO {
        validarDatas(dto)
        val evento = buscarEntidade(id) // 404 se não existir
        evento.titulo = dto.titulo
        evento.descricao = dto.descricao
        evento.dataInicio = dto.dataInicio
        evento.dataFim = dto.dataFim
        evento.vagas = dto.vagas
        evento.endereco = buscarEndereco(dto.cep, dto.numero)
        return repository.save(evento).toResponse()
    }

    @Transactional fun deletar(id: Long) = repository.delete(buscarEntidade(id))

    // Funções auxiliares (privadas)

    // Busca entidade ou lança 404 reaproveitada nas outras operações
    private fun buscarEntidade(id: Long): Evento =
        repository.findByIdOrNull(id)
            ?: throw ResourceNotFoundException("Evento com id $id não encontrado")

    // Data de fim tem que ser depois da data de início -> senão 400
    private fun validarDatas(dto: EventoRequestDTO) =
        require(dto.dataFim.isAfter(dto.dataInicio)) {
            "A data de fim deve ser posterior à data de início"
        }

    // Chama ViaCEP e transforma a resposta no Endereco da entidade
    private fun buscarEndereco(cep: String, numero: String?): Endereco {
        val cepLimpo = cep.replace("-", "") // ViaCEP espera só números
        val resposta = viaCepClient.buscarCep(cepLimpo)

        // CEP com formato certo, mas inexistente -> ViaCEP devolve {"erro": true}
        require(resposta.erro != true) { "CEP $cep não encontrado" }

        return Endereco(
            cep = resposta.cep ?: cepLimpo,
            logradouro = resposta.logradouro ?: "",
            bairro = resposta.bairro ?: "",
            cidade = resposta.localidade ?: "", // ViaCEP chama "localidade"
            uf = resposta.uf ?: "",
            numero = numero,
        )
    }
}
