package com.cryptolab.worker;

import com.cryptolab.experiment.domain.BacktestJob;
import com.cryptolab.experiment.domain.BacktestWorkerOutcome;
import com.cryptolab.experiment.port.BacktestJobProcessor;
import com.cryptolab.infrastructure.experiment.messaging.BacktestJobTopology;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class RabbitBacktestJobListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitBacktestJobListener.class);

    private final BacktestJobProcessor worker;
    private final ObjectMapper objectMapper;
    private final String workerId;
    private final WorkerTelemetry telemetry;

    public RabbitBacktestJobListener(
            BacktestJobProcessor worker,
            ObjectMapper objectMapper,
            @Value("${crypto.backtest.worker.id:${HOSTNAME:crypto-worker}}") String workerId,
            WorkerTelemetry telemetry) {
        this.worker = worker;
        this.objectMapper = objectMapper;
        this.workerId = workerId;
        this.telemetry = telemetry;
    }

    @RabbitListener(
            queues = BacktestJobTopology.JOB_QUEUE,
            containerFactory = "backtestManualAckContainerFactory")
    public void receive(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        BacktestJob job;
        try {
            job = objectMapper.readValue(message.getBody(), BacktestJob.class);
            validateIdentityHeader(message, job);
        } catch (JsonProcessingException | IllegalArgumentException poison) {
            telemetry.recordPoisonMessage();
            LOGGER.warn("backtest_job_poison eventId={} jobId={} errorType={}",
                    message.getMessageProperties().getMessageId(),
                    message.getMessageProperties().getHeaders().get("experimentId"),
                    poison.getClass().getSimpleName());
            channel.basicReject(deliveryTag, false);
            return;
        }

        long startedAt = System.nanoTime();
        try {
            LOGGER.info(
                    "backtest_job_received correlationId={} searchRunId={} candidateId={} experimentId={} jobId={} attempt={} workerId={}",
                    job.correlationId(),
                    job.correlationId(),
                    job.command().candidateId(),
                    job.command().experimentId(),
                    job.command().experimentId(),
                    job.attempt(),
                    workerId);
            BacktestWorkerOutcome outcome = worker.process(job.command().experimentId(), workerId);
            telemetry.record(outcome);
            LOGGER.info(
                    "backtest_job_finished correlationId={} searchRunId={} candidateId={} experimentId={} jobId={} outcome={} workerId={}",
                    job.correlationId(),
                    job.correlationId(),
                    job.command().candidateId(),
                    job.command().experimentId(),
                    job.command().experimentId(),
                    outcome,
                    workerId);
            if (outcome == BacktestWorkerOutcome.DEAD_LETTER) {
                channel.basicReject(deliveryTag, false);
            } else if (outcome == BacktestWorkerOutcome.REQUEUE) {
                channel.basicNack(deliveryTag, false, true);
            } else {
                channel.basicAck(deliveryTag, false);
            }
        } catch (RuntimeException infrastructureFailure) {
            telemetry.recordInfrastructureFailure();
            LOGGER.error(
                    "backtest_job_failed correlationId={} searchRunId={} candidateId={} experimentId={} jobId={} workerId={} errorType={}",
                    job.correlationId(),
                    job.correlationId(),
                    job.command().candidateId(),
                    job.command().experimentId(),
                    job.command().experimentId(),
                    workerId,
                    infrastructureFailure.getClass().getSimpleName(),
                    infrastructureFailure);
            channel.basicNack(deliveryTag, false, true);
        } finally {
            telemetry.recordDuration(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }

    private static void validateIdentityHeader(Message message, BacktestJob job) {
        Object header = message.getMessageProperties().getHeaders().get("experimentId");
        if (header == null) {
            throw new IllegalArgumentException("experimentId header is required");
        }
        UUID headerId = UUID.fromString(header.toString());
        if (!headerId.equals(job.command().experimentId())) {
            throw new IllegalArgumentException("experimentId header does not match payload");
        }
    }
}
