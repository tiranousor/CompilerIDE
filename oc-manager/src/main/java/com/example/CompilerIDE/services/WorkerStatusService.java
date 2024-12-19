package com.example.CompilerIDE.services;

import com.example.CompilerIDE.dto.ContainerStatsDTO;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder; // Убедитесь что импорт есть
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import io.lettuce.core.output.ScanOutput;
import org.springframework.stereotype.Service;
import com.sun.management.OperatingSystemMXBean;
import java.io.Closeable;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkerStatusService {
    private final DockerClient dockerClient;

    public WorkerStatusService() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
    }

    public List<ContainerStatsDTO> getAllContainerStats() {
        List<Container> containers = dockerClient.listContainersCmd().exec();
        System.out.println(containers.size());
        List<ContainerStatsDTO> statsList = new ArrayList<>();

        for (Container container : containers) {
            String containerId = container.getId();

            var statsCallback = dockerClient.statsCmd(containerId)
                    .withNoStream(true)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Statistics>() {
                        @Override
                        public void onNext(Statistics stats) {
                            if (stats == null) return;

                            String name = (container.getNames() != null && container.getNames().length > 0)
                                    ? container.getNames()[0] : containerId;

                            double cpuPercent = calculateCpuPercent(stats);
                            long memoryUsage = stats.getMemoryStats() != null && stats.getMemoryStats().getUsage() != null
                                    ? stats.getMemoryStats().getUsage() : 0;
                            long memoryLimit = stats.getMemoryStats() != null && stats.getMemoryStats().getLimit() != null
                                    ? stats.getMemoryStats().getLimit() : 0;

                            statsList.add(new ContainerStatsDTO(containerId, name, cpuPercent, memoryUsage, memoryLimit));
                        }
                    });

            try {
                statsCallback.awaitCompletion();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }

        return statsList;
    }
    public SystemMemoryInfo getSystemMemoryInfo() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        long totalMemory = osBean.getTotalPhysicalMemorySize();
        long freeMemory = osBean.getFreePhysicalMemorySize();
        long usedMemory = totalMemory - freeMemory;
        return new SystemMemoryInfo(totalMemory, usedMemory);
    }
    private double calculateCpuPercent(Statistics stats) {
        if (stats.getCpuStats() == null || stats.getPreCpuStats() == null) {
            return 0.0;
        }

        Long cpuDelta = stats.getCpuStats().getCpuUsage().getTotalUsage() -
                stats.getPreCpuStats().getCpuUsage().getTotalUsage();

        Long systemDelta = stats.getCpuStats().getSystemCpuUsage() -
                stats.getPreCpuStats().getSystemCpuUsage();

        double cpuPercent = 0.0;
        if (systemDelta > 0 && cpuDelta > 0) {
            cpuPercent = (cpuDelta.doubleValue() / systemDelta.doubleValue()) * stats.getCpuStats().getOnlineCpus() * 100.0;
        }
        return cpuPercent;
    }
    public static class SystemMemoryInfo {
        private long totalMemory;
        private long usedMemory;

        public SystemMemoryInfo(long totalMemory, long usedMemory) {
            this.totalMemory = totalMemory;
            this.usedMemory = usedMemory;
        }

        public long getTotalMemory() {
            return totalMemory;
        }

        public long getUsedMemory() {
            return usedMemory;
        }
    }
}
