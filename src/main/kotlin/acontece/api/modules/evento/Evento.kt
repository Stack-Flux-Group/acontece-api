package acontece.api.modules.evento

import jakarta.persistence.*
import java.time.LocalDateTime

// @Entity diz ao JPA que a classe vira uma tabela.
@Entity
class Evento(
    @Column(nullable = false) var titulo: String,
    var descricao: String,
    @Column(nullable = false) var dataInicio: LocalDateTime,
    @Column(nullable = false) var dataFim: LocalDateTime,
    @Column(nullable = false) var vagas: Int,
    @Embedded var endereco: Endereco,
    var fotoUrl: String? = null,

    // @Id @GeneratedValue(IDENTITY) faz o banco gerar o id. Ele é val e null ao criar, porque você
    // nunca altera o id depois.
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)
