package acontece.api.modules.usuario

import acontece.api.enums.Papel
import jakarta.persistence.Entity

@Entity
class Organizador(
    nome: String,
    email: String,
    senha: String,
) : Usuario(nome, email, senha) {

    override val papel: Papel
        get() = Papel.ORGANIZADOR
}
