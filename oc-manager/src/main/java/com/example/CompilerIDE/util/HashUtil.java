package com.example.CompilerIDE.util;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

    public static String computeSHA256Hash(InputStream inputStream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] block = new byte[4096];
        int length;
        while ((length = inputStream.read(block)) > 0) {
            digest.update(block, 0, length);
        }
        byte[] hashBytes = digest.digest();

        // Преобразуем байты в шестнадцатеричную строку
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
