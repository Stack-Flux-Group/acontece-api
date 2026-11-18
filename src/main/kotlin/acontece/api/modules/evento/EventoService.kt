package acontece.api.modules.evento

import acontece.api.client.ViaCepClient
import acontece.api.enums.StatusInscricao
import acontece.api.exception.ConflictException
import acontece.api.exception.ResourceNotFoundException
import acontece.api.exception.ServicoExternoException
import acontece.api.modules.inscricao.InscricaoRepository
import feign.FeignException
import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class EventoService(
    private val repository: EventoRepository,
    private val viaCepClient: ViaCepClient,
    // Para contar as inscrições ativas antes de reduzir as vagas.
    private val inscricaoRepository: InscricaoRepository,
    // Abre uma transação só no trecho que precisa dela (ver atualizar).
    private val transacao: TransactionTemplate,
) {

    // Leitura

    fun listar(): List<EventoResponseDTO> = respostas(repository.findAll())

    fun buscarPorId(id: Long): EventoResponseDTO {
        val evento = buscarEntidade(id)
        return evento.toResponse(ocupadas(id))
    }

    fun buscarPorTitulo(titulo: String): List<EventoResponseDTO> =
        respostas(repository.findByTituloContainingIgnoreCase(titulo))

    fun buscarPorPeriodo(inicio: LocalDateTime, fim: LocalDateTime): List<EventoResponseDTO> {
        // require: se a condição for falsa, lança illegalargumentexception com 400
        require(!fim.isBefore(inicio)) {
            "A data final do filtro deve ser igual ou posterior à inicial"
        }
        return respostas(repository.buscarPorPeriodo(inicio, fim))
    }

    // Busca de GET /eventos: cada filtro é opcional e os que vierem se somam (E).
    // Sem filtro nenhum, devolve todos, uma página por vez.
    fun buscar(
        titulo: String?,
        inicio: LocalDateTime?,
        fim: LocalDateTime?,
        pageable: Pageable,
    ): Page<EventoResponseDTO> {
        if (inicio != null && fim != null) {
            require(!fim.isBefore(inicio)) {
                "A data final do filtro deve ser igual ou posterior à inicial"
            }
        }
        val pagina = repository.findAll(EventoFiltros.de(titulo, inicio, fim), comOrdemValida(pageable))
        val ocupadas = ocupadasPorEvento(pagina.content)
        return pagina.map { it.toResponse(ocupadas[it.id] ?: 0) }
    }

    // Monta a resposta de vários eventos com uma consulta de contagem só, seja qual for o tamanho
    // da lista (em vez de uma por evento).
    private fun respostas(eventos: List<Evento>): List<EventoResponseDTO> {
        val ocupadas = ocupadasPorEvento(eventos)
        return eventos.map { it.toResponse(ocupadas[it.id] ?: 0) }
    }

    // id do evento -> inscrições que ocupam vaga. Eventos sem inscrição ativa ficam de fora (0).
    private fun ocupadasPorEvento(eventos: List<Evento>): Map<Long, Long> {
        if (eventos.isEmpty()) return emptyMap() // "IN ()" vazio nem chega ao banco
        return inscricaoRepository
            .contarPorEvento(eventos.map { it.id!! }, StatusInscricao.QUE_OCUPAM_VAGA)
            .associate { it.eventoId to it.ocupadas }
    }

    private fun ocupadas(eventoId: Long): Long =
        inscricaoRepository.countByEventoIdAndStatusIn(eventoId, StatusInscricao.QUE_OCUPAM_VAGA)

    // Só deixa ordenar pelos campos conhecidos: um `sort=xyz` qualquer viraria erro 500 no banco,
    // aqui vira 400 com a lista do que é aceito. O id entra por último como desempate, para a mesma
    // linha nunca aparecer em duas páginas quando várias têm o mesmo valor no campo ordenado.
    private fun comOrdemValida(pageable: Pageable): Pageable {
        val invalidos = pageable.sort.map { it.property }.filter { it !in CAMPOS_ORDENAVEIS }.toList()
        require(invalidos.isEmpty()) {
            "Não é possível ordenar por ${invalidos.joinToString()}. Use: ${CAMPOS_ORDENAVEIS.joinToString()}"
        }
        val ordem = if (pageable.sort.isSorted) pageable.sort else Sort.by("dataInicio")
        val comDesempate = if (ordem.getOrderFor("id") != null) ordem else ordem.and(Sort.by("id"))
        return PageRequest.of(pageable.pageNumber, pageable.pageSize, comDesempate)
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
        // Evento recém-criado não tem inscrições: todas as vagas estão disponíveis.
        return repository.save(evento).toResponse(vagasOcupadas = 0) // o save abre e fecha a própria transação
    }

    fun atualizar(id: Long, dto: EventoRequestDTO): EventoResponseDTO {
        val (inicio, fim) = validarDatas(dto)
        val endereco = buscarEndereco(dto.cep, dto.numero)

        // Daqui para baixo é uma transação só: se algo falhar no meio, nada é gravado.
        val atualizado =
            transacao.execute {
                // Trava a linha do evento: uma inscrição que chegue agora espera esta edição
                // terminar, então a contagem abaixo não fica velha antes de gravar.
                val evento =
                    repository.buscarComTrava(id)
                        ?: throw ResourceNotFoundException("Evento com id $id não encontrado")

                val ocupadas = ocupadas(id)
                if (dto.vagas < ocupadas) {
                    throw ConflictException(
                        "O evento já tem $ocupadas inscrições ativas; as vagas não podem ficar abaixo disso"
                    )
                }

                evento.titulo = dto.titulo
                evento.descricao = dto.descricao
                evento.dataInicio = inicio
                evento.dataFim = fim
                evento.vagas = dto.vagas
                evento.endereco = endereco
                evento.fotoUrl = dto.fotoUrl
                evento.toResponse(ocupadas) // o JPA grava as mudanças sozinho ao fim da transação
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

    private companion object {
        // Campos aceitos em `sort` na listagem de eventos.
        val CAMPOS_ORDENAVEIS = listOf("dataInicio", "dataFim", "titulo", "vagas", "id")
    }
}
