package processors;


import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Desserializa o valor do registro Kafka (JSON) em PagamentoPixEstaticoEvent. */
public class SegregacaoDeserializer extends ObjectMapperDeserializer<TransacaoPagamentoEvent> {

    public SegregacaoDeserializer() {
        super(TransacaoPagamentoEvent.class);
    }
}