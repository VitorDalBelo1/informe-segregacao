package processors;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import io.smallrye.common.annotation.Blocking;

import java.util.List;

@ApplicationScoped
public class SegregacaoProcessor {
    private static final Logger LOG = Logger.getLogger(SegregacaoProcessor.class);

    @Incoming("entrada")
    @Blocking
    public void consumir(List<TransacaoPagamentoEvent> eventos) {
        LOG.info(String.format("Lote recebido do Kafka com %d evento(s)", eventos.size()));
    }

}
