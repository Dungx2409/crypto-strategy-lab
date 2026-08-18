package com.cryptolab.infrastructure.shared.adapter;

import com.cryptolab.infrastructure.experiment.messaging.BacktestJobTopology;
import com.cryptolab.shared.domain.OperationalStatusSnapshot;
import com.cryptolab.shared.port.OperationalStatusProvider;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcRabbitOperationalStatusProvider implements OperationalStatusProvider {

    private final JdbcTemplate jdbcTemplate;
    private final RabbitAdmin rabbitAdmin;

    public JdbcRabbitOperationalStatusProvider(
            JdbcTemplate jdbcTemplate,
            ConnectionFactory connectionFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitAdmin = new RabbitAdmin(connectionFactory);
    }

    @Override
    public OperationalStatusSnapshot current() {
        long runningJobs = count("SELECT count(*) FROM backtest_jobs WHERE status = 'RUNNING'");
        long pendingOutbox = count(
                "SELECT count(*) FROM outbox_events WHERE published_at IS NULL AND cancelled_at IS NULL");
        try {
            QueueInformation queue = rabbitAdmin.getQueueInfo(BacktestJobTopology.JOB_QUEUE);
            return queue == null
                    ? new OperationalStatusSnapshot(false, 0, 0, runningJobs, pendingOutbox)
                    : new OperationalStatusSnapshot(
                            true,
                            queue.getMessageCount(),
                            queue.getConsumerCount(),
                            runningJobs,
                            pendingOutbox);
        } catch (RuntimeException unavailableBroker) {
            return new OperationalStatusSnapshot(false, 0, 0, runningJobs, pendingOutbox);
        }
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
