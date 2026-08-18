package com.cryptolab.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cryptolab.experiment.domain.BacktestCommand;
import com.cryptolab.experiment.domain.BacktestJob;
import com.cryptolab.experiment.domain.BacktestWorkerOutcome;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.port.BacktestJobProcessor;
import com.cryptolab.marketdata.domain.Timeframe;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RabbitBacktestJobListenerTest {

    private static final UUID EXPERIMENT_ID =
            UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final long DELIVERY_TAG = 42L;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void acknowledgesOnlyAfterTheTransactionalProcessorHasReturnedSuccessfully() throws Exception {
        AtomicBoolean committed = new AtomicBoolean();
        BacktestJobProcessor processor = (experimentId, workerId) -> {
            assertThat(experimentId).isEqualTo(EXPERIMENT_ID);
            assertThat(workerId).isEqualTo("worker-test");
            committed.set(true);
            return BacktestWorkerOutcome.COMPLETED;
        };
        Channel channel = mock(Channel.class);
        doAnswer(invocation -> {
                    assertThat(committed)
                            .as("the processor transaction must return before Rabbit ACK")
                            .isTrue();
                    return null;
                })
                .when(channel)
                .basicAck(DELIVERY_TAG, false);

        listener(processor).receive(validMessage(), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicReject(DELIVERY_TAG, false);
    }

    @Test
    void requeuesDeliveryThatArrivesBeforeOutboxConfirmationIsCommitted() throws Exception {
        BacktestJobProcessor processor = (experimentId, workerId) -> BacktestWorkerOutcome.REQUEUE;
        Channel channel = mock(Channel.class);

        listener(processor).receive(validMessage(), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void rejectsMalformedPayloadWithoutRequeueSoRabbitRoutesItToDlq() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        BacktestJobProcessor processor = (experimentId, workerId) -> {
            invoked.set(true);
            return BacktestWorkerOutcome.COMPLETED;
        };
        Channel channel = mock(Channel.class);
        MessageProperties properties = properties();
        Message malformed = new Message("not-json".getBytes(), properties);

        listener(processor).receive(malformed, channel);

        assertThat(invoked).isFalse();
        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void rejectsPermanentlyFailedJobWithoutRequeue() throws Exception {
        BacktestJobProcessor processor =
                (experimentId, workerId) -> BacktestWorkerOutcome.DEAD_LETTER;
        Channel channel = mock(Channel.class);

        listener(processor).receive(validMessage(), channel);

        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, true);
    }

    private RabbitBacktestJobListener listener(BacktestJobProcessor processor) {
        return new RabbitBacktestJobListener(
                processor, objectMapper, "worker-test", WorkerTelemetry.noop());
    }

    private Message validMessage() throws Exception {
        BacktestCommand command = new BacktestCommand(
                EXPERIMENT_ID,
                UUID.fromString("71000000-0000-0000-0000-000000000002"),
                new MarketDatasetRef(
                        "BTCUSDT",
                        Timeframe.M5,
                        Instant.parse("2026-08-18T00:00:00Z"),
                        Instant.parse("2026-08-18T01:00:00Z"),
                        "worker-test-v1",
                        "worker-test-checksum"),
                new ExecutionConfig(
                        new BigDecimal("10000"),
                        new BigDecimal("0.001"),
                        false,
                        "NEXT_CANDLE_OPEN",
                        "deterministic-next-open-v1"));
        BacktestJob job = new BacktestJob(command, 0, "worker-test-correlation");
        return new Message(objectMapper.writeValueAsBytes(job), properties());
    }

    private static MessageProperties properties() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setHeader("experimentId", EXPERIMENT_ID.toString());
        return properties;
    }
}
