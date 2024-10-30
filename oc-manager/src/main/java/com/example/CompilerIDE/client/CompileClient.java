package com.example.CompilerIDE.client;

import com.example.CompilerIDE.Dto.CompileRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(name = "javac-agent", url = "http://localhost:8081") // URL oc-agent-javac
public interface CompileClient {

    @PostMapping(value = "/api/compile", consumes = "application/json")
    String compileCode(@RequestBody CompileRequest request);
}


