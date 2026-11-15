package acontece.api.modules.usuario

import acontece.api.enums.Papel
import jakarta.persistence.Column
import jakarta.persistence.Entity

@Entity
class Participante(
    nome: String,
    email: String,

    @Column(nullable = false, unique = true, length = 11)
    var cpf: String,

    var telefone: String? = null,

    senha: String? = null,

) : Usuario(nome, email, senha) {

    override val papel: Papel
        get() = Papel.PARTICIPANTE
}
