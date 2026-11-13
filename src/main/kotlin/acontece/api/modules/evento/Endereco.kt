package acontece.api.modules.evento

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Endereco(
    @Column(nullable = false) var cep: String,
    @Column(nullable = false) var logradouro: String,
    @Column(nullable = false) var bairro: String,
    @Column(nullable = false) var cidade: String,
    @Column(nullable = false) var uf: String,
    var numero: String? = null,
)
