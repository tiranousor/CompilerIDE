package com.example.CompilerIDE.services;
import com.example.CompilerIDE.Dto.CompileRequest;
import com.example.CompilerIDE.client.CompileClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CodeCompilationService {

    private final CompileClient compileClient;

    @Autowired
    public CodeCompilationService(CompileClient compileClient) {
        this.compileClient = compileClient;
    }

    public String compileCode(CompileRequest request) {

        System.out.println();
        return compileClient.compileCode(request);
    }
}