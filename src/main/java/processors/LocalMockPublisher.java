package processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;

/**
 * Profile 'local' (sem Kafka): o canal 'entrada' não tem conector, então é um canal
 * interno da aplicação. Este @Outgoing("entrada") liga direto no @Incoming("entrada")
 * do SegregacaoProcessor.
 */
@IfBuildProfile("local")
@ApplicationScoped
public class LocalMockPublisher {

    private static final Logger LOG = Logger.getLogger(LocalMockPublisher.class);

    private static final String MOCK_JSON = """
            {
              "tipoEvento": 1,
              "arranjo": "PXE",
              "identificadorTransacao": "E18236120261005101530000000000012345",
              "valorTransacao": 1500.75,
              "valorCbs": 15.01,
              "valorIbs": 7.5,
              "documentoFiscal": "NF-20261005-987654",
              "agenciaContaDebitada": 1234,
              "produtoContaDebitada": 1,
              "numeroContaDebitada": "1234567890123",
              "agenciaContaCreditada": 5678,
              "produtoContaCreditada": 1,
              "numeroContaCreditada": "9876543210987",
              "compe": 104,
              "ispb": 360305,
              "cnpjRecebedor": "12345678000190",
              "nomeRecebedor": "LOJA EXEMPLO LTDA",
              "cpfCnpjPagador": "12345678901",
              "nomePagador": "CLIENTE TESTE",
              "dataHoraLiquidacao": "2026-10-05T10:15:32Z",
              "cnpjRaizBancoPagador": "00360305",
              "dataHoraPagamento": "2026-10-05T10:15:30Z",
              "nsuOrigem": "001202610051234567890"
            }
            """;

    @Inject
    ObjectMapper mapper;

    @Outgoing("entrada")
    public Multi<List<TransacaoPagamentoEvent>> gerar() {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(10))
                .onItem().transform(tick -> {
                    try {
                        LOG.infof("Mock local #%d enviado para o canal 'entrada'", tick);
                        return List.of(mapper.readValue(MOCK_JSON, TransacaoPagamentoEvent.class));
                    } catch (Exception e) {
                        throw new IllegalStateException("JSON mock inválido", e);
                    }
                });
    }
}
