package com.example.agentJava.services;

import com.example.agentJava.controller.CodeCompilerController;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class DockerService {

    public static final Map<String, String[]> templates = new HashMap<>();
    static {
        templates.put("java", new String[]{"sh", "-c", "javac Main.java && java Main"});
        templates.put("cpp", new String[]{"sh", "-c", "g++ -o main main.cpp && ./main"});
        templates.put("python3", new String[]{"python3", "main.py"});
    }

    public String runAndGetOutput(String language, String folderPath, String id) throws IOException, InterruptedException {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
        String containerId = language + "-compiler-container";

        dockerClient.copyArchiveToContainerCmd(containerId)
                .withHostResource(folderPath + "/" + id + "/.")
                .withRemotePath("/" + language)
                .exec();

        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(language + "-compiler-container")
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(templates.get(language))
                .exec();
        OutputStream stdout = new ByteArrayOutputStream();
        OutputStream stderr = new ByteArrayOutputStream();
        dockerClient.execStartCmd(execCreateCmdResponse.getId())
                .exec(new ExecStartResultCallback(stdout, stderr))
                .awaitCompletion();

        String output = stdout.toString();
        String error = stderr.toString();


        return (!error.isEmpty()) ? error : output;
    }
}
