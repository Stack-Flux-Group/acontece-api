package acontece.api.modules.usuario

import acontece.api.enums.Papel
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
abstract class Usuario(

    @Column(nullable = false)
    var nome: String,

    @Column(nullable = false, unique = true)
    var email: String,

    // Hash BCrypt, nunca a senha pura. Nulo só em linhas antigas, criadas antes do login existir;
    // essas contas não conseguem entrar até ganharem uma senha.
    @Column(length = 100)
    var senha: String? = null,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // Vem da própria subclasse, então não vira coluna.
    abstract val papel: Papel
}
