package com.example.CompilerIDE.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public class HashUtil {

    /**
     * Вычисляет SHA-256 хеш для данных из InputStream.
     *
     * @param inputStream входной поток данных
     * @return строковое представление хеша в шестнадцатеричном формате
     * @throws Exception если произошла ошибка при вычислении хеша
     */
    public static String computeSHA256Hash(InputStream inputStream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] block = new byte[4096];
        int length;
        while ((length = inputStream.read(block)) > 0) {
            digest.update(block, 0, length);
        }
        byte[] hashBytes = digest.digest();

        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Вычисляет SHA-256 хеш для указанного файла.
     *
     * @param file файл для вычисления хеша
     * @return строковое представление хеша в шестнадцатеричном формате
     * @throws Exception если произошла ошибка при чтении файла или вычислении хеша
     */
    public static String computeSHA256Hash(File file) throws Exception {
        try (InputStream fis = new FileInputStream(file)) {
            return computeSHA256Hash(fis);
        }
    }
}
