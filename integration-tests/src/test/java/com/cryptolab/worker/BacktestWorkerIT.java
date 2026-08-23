package com.cryptolab.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.BacktestWorkerService;
import com.cryptolab.experiment.application.DefaultExperimentEvaluator;
import com.cryptolab.experiment.application.DeterministicBacktestEngine;
import com.cryptolab.experiment.domain.BacktestJob;
import com.cryptolab.experiment.domain.BacktestCommand;
import com.cryptolab.experiment.domain.BacktestResult;
import com.cryptolab.experiment.domain.BacktestJobClaim;
import com.cryptolab.experiment.domain.BacktestJobClaimDecision;
import com.cryptolab.experiment.domain.CandidateCanonicalizer;
import com.cryptolab.experiment.domain.CandidateStrategy;
import com.cryptolab.experiment.domain.ExecutionConfig;
import com.cryptolab.experiment.domain.JobDispatchMetadata;
import com.cryptolab.experiment.domain.MarketDatasetChecksum;
import com.cryptolab.experiment.domain.MarketDatasetRef;
import com.cryptolab.experiment.domain.SearchContext;
import com.cryptolab.experiment.domain.SearchParameterSpace;
import com.cryptolab.experiment.domain.SearchRun;
import com.cryptolab.experiment.domain.SearchRunStatus;
import com.cryptolab.experiment.domain.StopConditions;
import com.cryptolab.experiment.port.BacktestWorkerRepository;
import com.cryptolab.experiment.port.BacktestPort;
import com.cryptolab.experiment.port.SearchRunRepository;
import com.cryptolab.infrastructure.experiment.adapter.DefaultCombinationPolicyResolver;
import com.cryptolab.infrastructure.experiment.adapter.JdbcBacktestWorkerRepository;
import com.cryptolab.infrastructure.experiment.adapter.JdbcExperimentRepository;
import com.cryptolab.infrastructure.experiment.adapter.JdbcSearchRunRepository;
import com.cryptolab.infrastructure.experiment.messaging.BacktestJobTopology;
import com.cryptolab.infrastructure.strategy.adapter.MovingAverageStrategyFactory;
import com.cryptolab.infrastructure.strategy.adapter.SpringStrategyRegistry;
import com.cryptolab.marketdata.domain.Candle;
import com.cryptolab.marketdata.domain.Timeframe;
import com.cryptolab.strategy.domain.CombinationPolicyDefinition;
import com.cryptolab.strategy.domain.StrategyDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class BacktestWorkerIT {

    private static final Instant DATASET_FROM = Instant.parse("2026-08-18T00:00:00Z");
    private static final List<Candle> CANDLES = candles();
    private static final MarketDatasetRef DATASET = new MarketDatasetRef(
            "BTCUSDT",
            Timeframe.M5,
            DATASET_FROM,
            DATASET_FROM.plus(Duration.ofMinutes(30)),
            "worker-it-v1",
            MarketDatasetChecksum.calculate(CANDLES));

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17.6-alpine"))
            .withDatabaseName("crypto_strategy_lab")
            .withUsername("crypto_lab")
            .withPassword("crypto_lab_test");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
                    DockerImageName.parse("rabbitmq:4.1-management-alpine"))
            .withAdminUser("crypto_lab")
            .withAdminPassword("crypto_lab_test");

    private static JdbcTemplate jdbc;
    private static ObjectMapper objectMapper;
    private static SearchRunRepository searchRuns;
    private static BacktestWorkerRepository workerRepository;
    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbitTemplate;
    private static RabbitAdmin rabbitAdmin;
    private static List<SimpleMessageListenerContainer> listenerContainers;
    private static TrackingBacktestPort trackingBacktest;

    @BeforeAll
    static void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        searchRuns = transactional(
                new JdbcSearchRunRepository(jdbc, objectMapper),
                SearchRunRepository.class,
                transactionManager);
        workerRepository = transactional(
                new JdbcBacktestWorkerRepository(jdbc, objectMapper),
                BacktestWorkerRepository.class,
                transactionManager);
        persistDataset();

        JdbcExperimentRepository experimentData = new JdbcExperimentRepository(jdbc, objectMapper);
        DeterministicBacktestEngine backtest = new DeterministicBacktestEngine(
                experimentData,
                experimentData,
                new SpringStrategyRegistry(List.of(new MovingAverageStrategyFactory())),
                new DefaultCombinationPolicyResolver(),
                Clock.systemUTC());
        trackingBacktest = new TrackingBacktestPort(backtest);
        BacktestWorkerService worker = new BacktestWorkerService(
                trackingBacktest,
                new DefaultExperimentEvaluator(),
                workerRepository,
                Clock.systemUTC(),
                Duration.ofMinutes(5));
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitAdmin = new RabbitAdmin(connectionFactory);
        declareTopology();

        List<SimpleMessageListenerContainer> replicas = new ArrayList<>();
        for (int replica = 1; replica <= 3; replica++) {
            RabbitBacktestJobListener listener = new RabbitBacktestJobListener(
                    worker,
                    objectMapper,
                    "worker-it-" + replica,
                    WorkerTelemetry.noop());
            SimpleMessageListenerContainer container =
                    new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(BacktestJobTopology.JOB_QUEUE);
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
            container.setConcurrentConsumers(1);
            container.setPrefetchCount(1);
            container.setDefaultRequeueRejected(false);
            container.setMessageListener((ChannelAwareMessageListener)
                    (Message message, Channel channel) -> listener.receive(message, channel));
            container.start();
            replicas.add(container);
        }
        listenerContainers = List.copyOf(replicas);
        await(() -> listenerContainers.stream().allMatch(SimpleMessageListenerContainer::isRunning),
                Duration.ofSeconds(10));
    }

    @AfterAll
    static void tearDown() {
        if (listenerContainers != null) {
            listenerContainers.forEach(SimpleMessageListenerContainer::stop);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void purgeQueues() {
        listenerContainers.forEach(container -> {
            if (!container.isRunning()) {
                container.start();
            }
        });
        trackingBacktest.disableDelay();
        rabbitAdmin.purgeQueue(BacktestJobTopology.JOB_QUEUE, true);
        rabbitAdmin.purgeQueue(BacktestJobTopology.DEAD_LETTER_QUEUE, true);
    }

    @Test
    void duplicateDeliveryCreatesExactlyOneExperimentResult() throws Exception {
        UUID experimentId = createConfirmedJob();
        Message message = jobMessage(experimentId);

        assertThat(searchRunStatus(experimentId)).isEqualTo("EVALUATING");

        rabbitTemplate.send(
                BacktestJobTopology.JOB_EXCHANGE, BacktestJobTopology.JOB_ROUTING_KEY, message);
        rabbitTemplate.send(
                BacktestJobTopology.JOB_EXCHANGE, BacktestJobTopology.JOB_ROUTING_KEY, jobMessage(experimentId));

        await(() -> "COMPLETED".equals(status("backtest_jobs", experimentId)), Duration.ofSeconds(15));
        await(() -> queueMessageCount(BacktestJobTopology.JOB_QUEUE) == 0, Duration.ofSeconds(5));
        ResultCounts afterDuplicate = resultCounts(experimentId);

        assertThat(afterDuplicate.executionAttempts()).isEqualTo(1);
        assertThat(afterDuplicate.metrics()).isEqualTo(1);
        assertThat(afterDuplicate.signals()).isEqualTo(12);
        assertThat(afterDuplicate.trades()).isPositive();
        assertThat(afterDuplicate.completedEvents()).isEqualTo(1);
        assertThat(status("experiments", experimentId)).isEqualTo("COMPLETED");
        assertThat(searchRunStatus(experimentId)).isEqualTo("COMPLETED");
        BigDecimal persistedWinRate = jdbc.queryForObject(
                "SELECT win_rate_pct FROM evaluation_metrics WHERE experiment_id = ?",
                BigDecimal.class,
                experimentId);
        BigDecimal calculatedWinRate = jdbc.queryForObject(
                """
                SELECT 100.0 * COUNT(*) FILTER (WHERE pnl > 0) / COUNT(*)
                FROM trades
                WHERE experiment_id = ?
                """,
                BigDecimal.class,
                experimentId);
        assertThat(persistedWinRate).isEqualByComparingTo(calculatedWinRate);

        rabbitTemplate.send(
                BacktestJobTopology.JOB_EXCHANGE, BacktestJobTopology.JOB_ROUTING_KEY, jobMessage(experimentId));
        await(() -> queueMessageCount(BacktestJobTopology.JOB_QUEUE) == 0, Duration.ofSeconds(5));

        assertThat(resultCounts(experimentId)).isEqualTo(afterDuplicate);
    }

    @Test
    void atomicClaimAllowsOneOwnerAndExpiredLeaseCanBeReclaimed() throws Exception {
        UUID experimentId = createConfirmedJob();
        Instant claimedAt = Instant.now();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<BacktestJobClaim> first = executor.submit(() -> {
                start.await();
                return workerRepository.claim(
                        experimentId, "claim-worker-1", Duration.ofMinutes(5), claimedAt);
            });
            Future<BacktestJobClaim> second = executor.submit(() -> {
                start.await();
                return workerRepository.claim(
                        experimentId, "claim-worker-2", Duration.ofMinutes(5), claimedAt);
            });
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .extracting(BacktestJobClaim::decision)
                    .containsExactlyInAnyOrder(
                            BacktestJobClaimDecision.CLAIMED,
                            BacktestJobClaimDecision.IN_PROGRESS);
        }

        jdbc.update(
                "UPDATE backtest_jobs SET lease_until = ? WHERE experiment_id = ?",
                timestamp(claimedAt.minusSeconds(1)),
                experimentId);
        BacktestJobClaim reclaimed = workerRepository.claim(
                experimentId,
                "claim-worker-3",
                Duration.ofMinutes(5),
                claimedAt.plusSeconds(1));

        assertThat(reclaimed.decision()).isEqualTo(BacktestJobClaimDecision.CLAIMED);
        assertThat(reclaimed.workerId()).isEqualTo("claim-worker-3");
        assertThat(jdbc.queryForObject(
                        "SELECT execution_attempts FROM backtest_jobs WHERE experiment_id = ?",
                        Integer.class,
                        experimentId))
                .isEqualTo(2);
    }

    @Test
    void malformedPoisonMessageIsRejectedToDeadLetterQueue() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setHeader("experimentId", UUID.randomUUID().toString());
        Message poison = new Message("not-json".getBytes(), properties);

        rabbitTemplate.send(
                BacktestJobTopology.JOB_EXCHANGE, BacktestJobTopology.JOB_ROUTING_KEY, poison);

        Message deadLetter = rabbitTemplate.receive(BacktestJobTopology.DEAD_LETTER_QUEUE, 10_000);
        assertThat(deadLetter).isNotNull();
        assertThat(new String(deadLetter.getBody())).isEqualTo("not-json");
        assertThat(deadLetter.getMessageProperties().getHeaders()).containsKey("x-death");
    }

    @Test
    void cancelledQueuedJobIsAcknowledgedWithoutStartingOrCreatingArtifacts() throws Exception {
        UUID experimentId = createConfirmedJob();
        UUID searchRunId = jdbc.queryForObject(
                "SELECT search_run_id FROM backtest_jobs WHERE experiment_id = ?",
                UUID.class,
                experimentId);

        assertThat(searchRuns.findSummary(searchRunId).orElseThrow().run().status())
                .isEqualTo(SearchRunStatus.EVALUATING);
        assertThat(searchRuns.cancel(searchRunId, Instant.now())).isTrue();
        rabbitTemplate.send(
                BacktestJobTopology.JOB_EXCHANGE,
                BacktestJobTopology.JOB_ROUTING_KEY,
                jobMessage(experimentId));
        await(() -> queueMessageCount(BacktestJobTopology.JOB_QUEUE) == 0, Duration.ofSeconds(10));

        assertThat(status("backtest_jobs", experimentId)).isEqualTo("CANCELLED");
        assertThat(status("experiments", experimentId)).isEqualTo("CANCELLED");
        assertThat(resultCounts(experimentId))
                .isEqualTo(new ResultCounts(0, 0, 0, 0, 0));
    }

    @Test
    void scalingFromOneToThreeIndependentConsumersDrainsFasterWithoutDuplicateCompletions()
            throws Exception {
        listenerContainers.get(1).stop();
        listenerContainers.get(2).stop();
        trackingBacktest.enableDelay(Duration.ofMillis(400));
        trackingBacktest.resetMaximumConcurrency();
        List<UUID> singleReplicaJobs = createConfirmedJobs(9);

        long singleStarted = System.nanoTime();
        publishJobs(singleReplicaJobs, false);
        awaitCompleted(singleReplicaJobs);
        long singleElapsed = System.nanoTime() - singleStarted;

        assertThat(trackingBacktest.maximumConcurrency()).isEqualTo(1);
        listenerContainers.get(1).start();
        listenerContainers.get(2).start();
        await(() -> listenerContainers.stream().allMatch(SimpleMessageListenerContainer::isRunning),
                Duration.ofSeconds(10));
        trackingBacktest.resetMaximumConcurrency();
        List<UUID> threeReplicaJobs = createConfirmedJobs(9);

        long threeStarted = System.nanoTime();
        publishJobs(threeReplicaJobs, true);
        awaitCompleted(threeReplicaJobs);
        long threeElapsed = System.nanoTime() - threeStarted;

        assertThat(trackingBacktest.maximumConcurrency()).isGreaterThanOrEqualTo(2);
        assertThat(threeElapsed).isLessThan(singleElapsed);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM experiments WHERE id = ANY (CAST(? AS uuid[])) "
                                + "AND status = 'COMPLETED'",
                        Integer.class,
                        "{" + threeReplicaJobs.stream()
                                .map(UUID::toString)
                                .collect(java.util.stream.Collectors.joining(",")) + "}"))
                .isEqualTo(threeReplicaJobs.size());
        for (UUID experimentId : threeReplicaJobs) {
            ResultCounts result = resultCounts(experimentId);
            assertThat(result.executionAttempts()).isEqualTo(1);
            assertThat(result.metrics()).isEqualTo(1);
            assertThat(result.completedEvents()).isEqualTo(1);
        }
    }

    private static List<UUID> createConfirmedJobs(int count) {
        List<UUID> jobs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            jobs.add(createConfirmedJob());
        }
        return List.copyOf(jobs);
    }

    private static void publishJobs(List<UUID> experimentIds, boolean duplicateDelivery)
            throws Exception {
        for (UUID experimentId : experimentIds) {
            rabbitTemplate.send(
                    BacktestJobTopology.JOB_EXCHANGE,
                    BacktestJobTopology.JOB_ROUTING_KEY,
                    jobMessage(experimentId));
            if (duplicateDelivery) {
                rabbitTemplate.send(
                        BacktestJobTopology.JOB_EXCHANGE,
                        BacktestJobTopology.JOB_ROUTING_KEY,
                        jobMessage(experimentId));
            }
        }
    }

    private static void awaitCompleted(List<UUID> experimentIds) {
        await(() -> experimentIds.stream()
                        .allMatch(id -> "COMPLETED".equals(status("backtest_jobs", id))),
                Duration.ofSeconds(30));
        await(() -> queueMessageCount(BacktestJobTopology.JOB_QUEUE) == 0, Duration.ofSeconds(10));
    }

    private static UUID createConfirmedJob() {
        UUID searchRunId = UUID.randomUUID();
        SearchContext context = new SearchContext(
                searchRunId,
                DATASET,
                List.of("MA"),
                Map.of("MA", "1.0"),
                new SearchParameterSpace(Map.of(
                        "MA", Map.of("fastPeriod", List.of(1), "slowPeriod", List.of(2)))),
                policy(),
                1234L,
                new StopConditions(1L, null, null),
                1);
        SearchRun created = new SearchRun(
                searchRunId,
                SearchRunStatus.CREATED,
                context,
                "random",
                "1.0",
                Instant.now(),
                null,
                null,
                false);
        searchRuns.create(created, executionConfig());
        searchRuns.transition(
                searchRunId, SearchRunStatus.CREATED, SearchRunStatus.RUNNING, null, Instant.now());
        SearchRun running = searchRuns.findSummary(searchRunId).orElseThrow().run();
        List<StrategyDefinition> definitions =
                List.of(new StrategyDefinition("MA", "1.0", Map.of("fastPeriod", 1, "slowPeriod", 2)));
        CandidateStrategy candidate = new CandidateStrategy(
                UUID.randomUUID(),
                definitions,
                policy(),
                CandidateCanonicalizer.hash(definitions, policy()));
        assertThat(searchRuns.appendCandidatesAndCreateJobs(
                        running,
                        executionConfig(),
                        new JobDispatchMetadata("return-minus-half-drawdown-v1", "it-commit", "it-build"),
                        List.of(candidate),
                        Instant.now()))
                .isEqualTo(1);
        UUID experimentId = jdbc.queryForObject(
                "SELECT experiment_id FROM backtest_jobs WHERE search_run_id = ?",
                UUID.class,
                searchRunId);
        jdbc.update(
                "UPDATE outbox_events SET published_at = ? WHERE aggregate_id = ? AND event_type = ?",
                timestamp(Instant.now()),
                experimentId,
                BacktestJobTopology.OUTBOX_EVENT_TYPE);
        jdbc.update(
                "UPDATE backtest_jobs SET status = 'QUEUED', queued_at = ?, dispatch_attempts = 1 "
                        + "WHERE experiment_id = ? AND status = 'PENDING_DISPATCH'",
                timestamp(Instant.now()),
                experimentId);
        jdbc.update(
                "UPDATE experiments SET status = 'QUEUED', version = version + 1 "
                        + "WHERE id = ? AND status = 'CREATED'",
                experimentId);
        searchRuns.finishGeneration(searchRunId, com.cryptolab.experiment.domain.SearchStopReason.MAX_CANDIDATES,
                Instant.now());
        return experimentId;
    }

    private static Message jobMessage(UUID experimentId) throws Exception {
        String payload = jdbc.queryForObject(
                "SELECT payload_json::text FROM backtest_jobs WHERE experiment_id = ?",
                String.class,
                experimentId);
        BacktestJob job = objectMapper.readValue(payload, BacktestJob.class);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("experimentId", experimentId.toString());
        return new Message(objectMapper.writeValueAsBytes(job), properties);
    }

    private static ResultCounts resultCounts(UUID experimentId) {
        return new ResultCounts(
                jdbc.queryForObject(
                        "SELECT execution_attempts FROM backtest_jobs WHERE experiment_id = ?",
                        Integer.class,
                        experimentId),
                count("experiment_signals", experimentId),
                count("trades", experimentId),
                count("evaluation_metrics", experimentId),
                jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'BacktestCompleted'",
                        Integer.class,
                        experimentId));
    }

    private static int count(String table, UUID experimentId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE experiment_id = ?",
                Integer.class,
                experimentId);
    }

    private static String status(String table, UUID experimentId) {
        String identity = table.equals("experiments") ? "id" : "experiment_id";
        return jdbc.queryForObject(
                "SELECT status FROM " + table + " WHERE " + identity + " = ?",
                String.class,
                experimentId);
    }

    private static String searchRunStatus(UUID experimentId) {
        return jdbc.queryForObject(
                """
                SELECT sr.status
                FROM search_runs sr
                JOIN backtest_jobs job ON job.search_run_id = sr.id
                WHERE job.experiment_id = ?
                """,
                String.class,
                experimentId);
    }

    private static long queueMessageCount(String queueName) {
        QueueInformation information = new QueueInformation(rabbitAdmin.getQueueInfo(queueName));
        return information.messageCount();
    }

    private static void persistDataset() {
        UUID datasetId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO market_datasets (
                    id, symbol, timeframe, from_time, to_time, dataset_version, checksum, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                datasetId,
                DATASET.symbol(),
                DATASET.timeframe().exchangeCode(),
                timestamp(DATASET.from()),
                timestamp(DATASET.to()),
                DATASET.datasetVersion(),
                DATASET.checksum(),
                timestamp(Instant.now()));
        for (int index = 0; index < CANDLES.size(); index++) {
            Candle candle = CANDLES.get(index);
            jdbc.update(
                    """
                    INSERT INTO market_dataset_candles (
                        dataset_id, sequence_no, open_time, open, high, low, close, volume
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    datasetId,
                    index,
                    timestamp(candle.openTime()),
                    candle.open(),
                    candle.high(),
                    candle.low(),
                    candle.close(),
                    candle.volume());
        }
    }

    private static List<Candle> candles() {
        List<BigDecimal> closes = List.of(
                new BigDecimal("100"),
                new BigDecimal("99"),
                new BigDecimal("101"),
                new BigDecimal("98"),
                new BigDecimal("102"),
                new BigDecimal("100"));
        java.util.ArrayList<Candle> candles = new java.util.ArrayList<>();
        for (int index = 0; index < closes.size(); index++) {
            BigDecimal close = closes.get(index);
            candles.add(new Candle(
                    "BTCUSDT",
                    Timeframe.M5,
                    DATASET_FROM.plus(Timeframe.M5.duration().multipliedBy(index)),
                    close,
                    close.add(BigDecimal.ONE),
                    close.subtract(BigDecimal.ONE),
                    close,
                    new BigDecimal("10")));
        }
        return List.copyOf(candles);
    }

    private static void declareTopology() {
        DirectExchange jobs = new DirectExchange(BacktestJobTopology.JOB_EXCHANGE, true, false);
        DirectExchange deadLetters =
                new DirectExchange(BacktestJobTopology.DEAD_LETTER_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(BacktestJobTopology.JOB_QUEUE)
                .deadLetterExchange(BacktestJobTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(BacktestJobTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(BacktestJobTopology.DEAD_LETTER_QUEUE).build();
        rabbitAdmin.declareExchange(jobs);
        rabbitAdmin.declareExchange(deadLetters);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareQueue(deadLetterQueue);
        rabbitAdmin.declareBinding(
                BindingBuilder.bind(queue).to(jobs).with(BacktestJobTopology.JOB_ROUTING_KEY));
        rabbitAdmin.declareBinding(BindingBuilder.bind(deadLetterQueue)
                .to(deadLetters)
                .with(BacktestJobTopology.DEAD_LETTER_ROUTING_KEY));
    }

    private static <T> T transactional(
            T target,
            Class<T> contract,
            DataSourceTransactionManager transactionManager) {
        TransactionInterceptor transactionInterceptor = new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(contract);
        proxyFactory.addAdvice(transactionInterceptor);
        return contract.cast(proxyFactory.getProxy());
    }

    private static ExecutionConfig executionConfig() {
        return new ExecutionConfig(
                new BigDecimal("10000"),
                new BigDecimal("0.001"),
                false,
                DeterministicBacktestEngine.FILL_POLICY,
                DeterministicBacktestEngine.VERSION);
    }

    private static CombinationPolicyDefinition policy() {
        return new CombinationPolicyDefinition("MAJORITY", "1.0", Map.of(), BigDecimal.ZERO);
    }

    private static void await(Check condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while awaiting worker state", exception);
            }
        }
        throw new AssertionError("condition was not met within " + timeout
                + "; jobs="
                + jdbc.queryForList(
                        "SELECT experiment_id, status, retry_count, execution_attempts, last_error "
                                + "FROM backtest_jobs ORDER BY created_at DESC")
                + "; queueMessages="
                + queueMessageCount(BacktestJobTopology.JOB_QUEUE));
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @FunctionalInterface
    private interface Check {
        boolean matches();
    }

    private record ResultCounts(
            int executionAttempts,
            int signals,
            int trades,
            int metrics,
            int completedEvents) {}

    private static final class TrackingBacktestPort implements BacktestPort {

        private final BacktestPort delegate;
        private final AtomicInteger current = new AtomicInteger();
        private final AtomicInteger maximum = new AtomicInteger();
        private volatile Duration delay = Duration.ZERO;

        private TrackingBacktestPort(BacktestPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public BacktestResult run(BacktestCommand command) {
            int active = current.incrementAndGet();
            maximum.accumulateAndGet(active, Math::max);
            try {
                if (!delay.isZero()) {
                    TimeUnit.MILLISECONDS.sleep(delay.toMillis());
                }
                return delegate.run(command);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("scaling proof interrupted", exception);
            } finally {
                current.decrementAndGet();
            }
        }

        private void enableDelay(Duration delay) {
            this.delay = delay;
        }

        private void disableDelay() {
            delay = Duration.ZERO;
            resetMaximumConcurrency();
        }

        private void resetMaximumConcurrency() {
            maximum.set(0);
        }

        private int maximumConcurrency() {
            return maximum.get();
        }
    }

    private record QueueInformation(org.springframework.amqp.core.QueueInformation delegate) {
        private long messageCount() {
            return delegate == null ? -1 : delegate.getMessageCount();
        }
    }
}
