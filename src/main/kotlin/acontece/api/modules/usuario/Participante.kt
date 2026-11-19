package acontece.api.modules.usuario

import acontece.api.enums.Papel
import acontece.api.enums.StatusUsuario
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

    // Exclusao: a linha fica (as inscricoes apontam pra ela), mas os dados pessoais somem.
    // E-mail e CPF viram valores impossiveis, entao a pessoa pode se cadastrar de novo com os mesmos dados.
    fun anonimizar() {
        val id = requireNotNull(id) { "Participante ainda não persistido" }
        nome = "Participante excluído"
        email = "conta-excluida-$id"
        cpf = "X" + id.toString().padStart(10, '0')
        telefone = null
        senha = null
        status = StatusUsuario.EXCLUIDO
    }
}
