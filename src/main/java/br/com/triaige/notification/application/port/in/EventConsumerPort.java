package br.com.triaige.notification.application.port.in;

import br.com.triaige.notification.domain.model.ResultsReadyEvent;

/**
 * Port de entrada. Implementada pelo caso de uso, acionada pelo
 * adapter de entrada (consumer de Q3) apos deserializar e validar o schemaVersion do evento.
 */
public interface EventConsumerPort {

    void handleResultsReady(ResultsReadyEvent event);
}
