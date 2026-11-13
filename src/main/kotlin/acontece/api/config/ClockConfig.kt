package acontece.api.config

import java.time.Clock
import java.time.ZoneId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// As datas dos eventos são digitadas em horário de Brasília e guardadas sem fuso (LocalDateTime).
// O servidor de produção roda em UTC, então "agora" não pode vir do fuso da máquina: todo código
// que compara com a data de um evento deve ler a hora deste Clock.
@Configuration
class ClockConfig {

    @Bean fun clock(): Clock = Clock.system(ZoneId.of("America/Sao_Paulo"))
}
