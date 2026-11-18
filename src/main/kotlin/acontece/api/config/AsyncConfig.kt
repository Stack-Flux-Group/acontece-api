package acontece.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

// Liga o @Async usado pelo AlertaTelegramListener (envio de alertas em outra thread).
@Configuration
@EnableAsync
class AsyncConfig
