package acontece.api.modules.evento

import jakarta.persistence.criteria.Predicate
import java.time.LocalDateTime
import org.springframework.data.jpa.domain.Specification

// Monta o WHERE da busca só com os filtros preenchidos.
object EventoFiltros {

    fun de(titulo: String?, inicio: LocalDateTime?, fim: LocalDateTime?): Specification<Evento> =
        Specification { root, _, cb ->
            val condicoes = mutableListOf<Predicate>()

            // Título contém o texto, sem diferenciar maiúsculas. % e _ digitados são
            // escapados para valer como texto, não como curinga do LIKE.
            titulo?.trim()?.takeIf { it.isNotEmpty() }?.let { texto ->
                val escapado =
                    texto.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                condicoes += cb.like(cb.lower(root.get("titulo")), "%$escapado%", '\\')
            }

            // Período: pela data de INÍCIO do evento, inclusive nas duas pontas
            // (mesmo critério da rota antiga /evento/buscar/periodo).
            inicio?.let { condicoes += cb.greaterThanOrEqualTo(root.get("dataInicio"), it) }
            fim?.let { condicoes += cb.lessThanOrEqualTo(root.get("dataInicio"), it) }

            cb.and(*condicoes.toTypedArray())
        }
}
