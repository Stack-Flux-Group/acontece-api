package acontece.api.modules.evento

import acontece.api.client.ViaCepClient
import acontece.api.exception.ResourceNotFoundException
import acontece.api.exception.ServicoExternoException
import feign.FeignException
import java.time.LocalDateTime
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class EventoService(
    private val repository: EventoRepository,
    private val viaCepClient: ViaCepClient,
    // Abre uma transação só no trecho que precisa dela (ver atualizar).
    private val transacao: TransactionTemplate,
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
    // criar e atualizar NÃO são @Transactional: o ViaCEP é consultado antes de abrir a transação,
    // para não segurar uma conexão do banco (o pool tem só 3) enquanto espera a internet.

    fun criar(dto: EventoRequestDTO): EventoResponseDTO {
        val (inicio, fim) = validarDatas(dto)
        val endereco = buscarEndereco(dto.cep, dto.numero)
        val evento =
            Evento(
                titulo = dto.titulo,
                descricao = dto.descricao,
                dataInicio = inicio,
                dataFim = fim,
                vagas = dto.vagas,
                endereco = endereco,
                fotoUrl = dto.fotoUrl,
            )
        return repository.save(evento).toResponse() // o save abre e fecha a própria transação
    }

    fun atualizar(id: Long, dto: EventoRequestDTO): EventoResponseDTO {
        val (inicio, fim) = validarDatas(dto)
        val endereco = buscarEndereco(dto.cep, dto.numero)

        // Daqui para baixo é uma transação só: se algo falhar no meio, nada é gravado.
        val atualizado =
            transacao.execute {
                val evento = buscarEntidade(id) // 404 se não existir
                evento.titulo = dto.titulo
                evento.descricao = dto.descricao
                evento.dataInicio = inicio
                evento.dataFim = fim
                evento.vagas = dto.vagas
                evento.endereco = endereco
                evento.fotoUrl = dto.fotoUrl
                evento.toResponse() // o JPA grava as mudanças sozinho ao fim da transação
            }
        return checkNotNull(atualizado)
    }

    @Transactional fun deletar(id: Long) = repository.delete(buscarEntidade(id))

    // Funções auxiliares (privadas)

    // Busca entidade ou lança 404 reaproveitada nas outras operações
    private fun buscarEntidade(id: Long): Evento =
        repository.findByIdOrNull(id)
            ?: throw ResourceNotFoundException("Evento com id $id não encontrado")

    // Data de fim tem que ser depois da data de início -> senão 400. Devolve as duas já sem null:
    // o @Valid do controller barra datas ausentes; o requireNotNull cobre quem chama o service direto.
    private fun validarDatas(dto: EventoRequestDTO): Pair<LocalDateTime, LocalDateTime> {
        val inicio = requireNotNull(dto.dataInicio) { "A data de início é obrigatória" }
        val fim = requireNotNull(dto.dataFim) { "A data de fim é obrigatória" }
        require(fim.isAfter(inicio)) { "A data de fim deve ser posterior à data de início" }
        return inicio to fim
    }

    // Chama ViaCEP e transforma a resposta no Endereco da entidade
    private fun buscarEndereco(cep: String, numero: String?): Endereco {
        val cepLimpo = cep.replace("-", "") // ViaCEP espera só números
        val resposta =
            try {
                viaCepClient.buscarCep(cepLimpo)
            } catch (ex: FeignException) {
                // Fora do ar, erro do lado deles ou passou do timeout -> 502
                throw ServicoExternoException("Não foi possível consultar o CEP agora. Tente novamente.", ex)
            }

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
