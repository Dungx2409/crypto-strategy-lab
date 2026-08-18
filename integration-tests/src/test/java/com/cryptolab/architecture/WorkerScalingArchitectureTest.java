package com.cryptolab.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptolab.experiment.application.BacktestWorkerService;
import com.cryptolab.worker.CryptoStrategyWorkerApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WorkerScalingArchitectureTest {

    @Test
    void oneToThreeWorkersIsADeploymentChangeNotACoreServiceChange() throws Exception {
        String reactorRoot = System.getProperty("maven.multiModuleProjectDirectory");
        Path compose = reactorRoot == null
                ? Path.of("..", "docker-compose.yml").toAbsolutePath().normalize()
                : Path.of(reactorRoot, "docker-compose.yml");
        String yaml = Files.readString(compose);

        assertThat(yaml).contains("  worker:");
        assertThat(workerServiceBlock(yaml)).doesNotContain("container_name:");
        assertThat(CryptoStrategyWorkerApplication.class.getPackageName())
                .isEqualTo("com.cryptolab.worker");
        assertThat(Arrays.stream(BacktestWorkerService.class.getDeclaredConstructors())
                        .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .noneMatch(type -> type.getName().contains("replica")
                        || type.getName().contains("Rabbit")
                        || type.getName().contains("Spring"));
    }

    private static String workerServiceBlock(String yaml) {
        int start = yaml.indexOf("  worker:");
        int volumes = yaml.indexOf("\nvolumes:", start);
        return yaml.substring(start, volumes < 0 ? yaml.length() : volumes);
    }
}
