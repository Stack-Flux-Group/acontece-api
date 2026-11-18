package acontece.api.modules.evento

import jakarta.persistence.*
import java.time.LocalDateTime

// @Entity: a classe vira uma tabela no banco.
@Entity
class Evento(
    @Column(nullable = false) var titulo: String,
    @Column(length = 2000) var descricao: String,
    @Column(nullable = false) var dataInicio: LocalDateTime,
    @Column(nullable = false) var dataFim: LocalDateTime,
    @Column(nullable = false) var vagas: Int,
    @Embedded var endereco: Endereco,
    @Column(length = 1000) var fotoUrl: String? = null,

    // O banco gera o id. É val e null ao criar porque nunca muda depois.
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)
