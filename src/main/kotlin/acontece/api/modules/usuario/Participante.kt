package acontece.api.modules.usuario

import jakarta.persistence.Column
import jakarta.persistence.Entity

@Entity
class Participante(
    nome: String,
    email: String,

    @Column(nullable = false, unique = true, length = 11)
    var cpf: String,

    var telefone: String? = null

) : Usuario(nome, email)
