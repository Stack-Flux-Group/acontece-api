package acontece.api.modules.usuario

import acontece.api.enums.Papel
import acontece.api.enums.StatusUsuario
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import org.hibernate.annotations.ColumnDefault

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
abstract class Usuario(

    @Column(nullable = false)
    var nome: String,

    @Column(nullable = false, unique = true)
    var email: String,

    // Hash BCrypt, nunca a senha pura. Nulo so em conta antiga, de antes do login existir;
    // essas nao conseguem entrar ate ganharem uma senha.
    @Column(length = 100)
    var senha: String? = null,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // O DEFAULT deixa o `ddl-auto=update` criar a coluna NOT NULL com dados: quem ja existia entra como ATIVO.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @ColumnDefault("'ATIVO'")
    var status: StatusUsuario = StatusUsuario.ATIVO

    // Vem da subclasse, entao nao vira coluna.
    abstract val papel: Papel
}
