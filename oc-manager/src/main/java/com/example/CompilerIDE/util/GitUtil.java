package com.example.CompilerIDE.util;

public class GitUtil {
    public static String extractRepoName(String gitUrl) {
        if (gitUrl == null || gitUrl.isEmpty()) {
            throw new IllegalArgumentException("Git URL не может быть пустым");
        }
        String cleanUrl = gitUrl.split("\\?")[0];
        if (!cleanUrl.endsWith(".git")) {
            throw new IllegalArgumentException("Некорректный формат Git URL. Ожидался URL, заканчивающийся на .git");
        }
        int lastSlashIndex = cleanUrl.lastIndexOf('/');
        int gitIndex = cleanUrl.lastIndexOf(".git");

        if (lastSlashIndex == -1 || gitIndex == -1 || lastSlashIndex >= gitIndex) {
            throw new IllegalArgumentException("Некорректный Git URL: " + gitUrl);
        }

        return cleanUrl.substring(lastSlashIndex + 1, gitIndex);
    }
}
