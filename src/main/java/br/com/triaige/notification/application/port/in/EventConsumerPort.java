package br.com.triaige.notification.application.port.in;

import br.com.triaige.notification.domain.model.ResultsReadyEvent;

/** Acionada pelo consumidor da {@code triaige-results-ready} depois de validar o schemaVersion. */
public interface EventConsumerPort {

    void handleResultsReady(ResultsReadyEvent event);
}
