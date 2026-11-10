package acontece.api.modules.evento

import jakarta.persistence.Embeddable

@Embeddable
data class Endereco(
    var cep: String,
    var logradouro: String,
    var bairro: String,
    var cidade: String,
    var uf: String,
    var numero: String? = null,
)
