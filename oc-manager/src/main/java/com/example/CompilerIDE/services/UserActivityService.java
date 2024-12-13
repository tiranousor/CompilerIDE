package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.DailyStats;
import com.example.CompilerIDE.providers.LoginTimestamp;
import com.example.CompilerIDE.providers.UnbanRequest;
import com.example.CompilerIDE.repositories.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserActivityService {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private LoginTimestampRepository loginTimestampRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    @Autowired
    private UnbanRequestRepository unbanRequestRepository;

    public long countRegisteredUsers() {
        return clientRepository.count(); // Подсчитываем всех пользователей в базе данных
    }

    public List<Client> getUsersSorted(String sort) {
        List<Client> users = clientRepository.findAll();
        switch (sort) {
            case "alphabetical":
                return users.stream()
                        .sorted(Comparator.comparing(Client::getUsername))
                        .collect(Collectors.toList());
            case "role":
                return users.stream()
                        .sorted(Comparator.comparing(Client::getRole).reversed())
                        .collect(Collectors.toList());
            case "onlyAdmins":
                return users.stream()
                        .filter(user -> "ROLE_ADMIN".equals(user.getRole()))
                        .collect(Collectors.toList());
            case "onlyUsers":
                return users.stream()
                        .filter(user -> "ROLE_USER".equals(user.getRole()))
                        .collect(Collectors.toList());
            case "bannedFirst":
                return users.stream()
                        .filter(user -> "ROLE_BANNED".equals(user.getRole()))
                        .collect(Collectors.toList());
            case "activity":
                Map<Long, Long> userIdToOnlineTime = getUserOnlineTimeMap();
                return users.stream()
                        .sorted(Comparator.comparing(
                                user -> userIdToOnlineTime.getOrDefault(user.getId(), 0L), Comparator.reverseOrder()))
                        .collect(Collectors.toList());

            case "registrationDateDesc":
            default:
                return users.stream()
                        .sorted(Comparator.comparing(Client::getCreatedAt).reversed())
                        .collect(Collectors.toList());
        }
    }
    private Map<Long, Long> getUserOnlineTimeMap() {
        return loginTimestampRepository.findAll().stream()
                .filter(ts -> ts.getLogoutTime() != null)
                .collect(Collectors.groupingBy(
                        ts -> ts.getClient().getId(),
                        Collectors.summingLong(ts -> java.time.Duration.between(ts.getLoginTime(), ts.getLogoutTime()).getSeconds())
                ));
    }
    @Transactional
    public void addAdmins(List<Long> userIds) {
        System.out.println("Adding admins for userIds: " + userIds);
        List<Client> users = clientRepository.findAllById(userIds);
        System.out.println("Found users: " + users);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("No users found with the provided IDs.");
        }
        users.forEach(user -> {
            System.out.println("Updating user: " + user.getUsername() + " from role " + user.getRole() + " to ROLE_ADMIN");
            user.setRole("ROLE_ADMIN");
        });
        clientRepository.saveAll(users);
        System.out.println("Successfully updated roles for users: " + users.stream().map(Client::getUsername).collect(Collectors.toList()));
    }


   public long countTotalProjects() {
        return projectRepository.count(); // Подсчитываем все проекты в базе данных
    }

    public List<Map.Entry<Client, Long>> getTopUsersByOnlineTime(int limit) {
        Map<Client, Long> userOnlineTime = loginTimestampRepository.findAll().stream()
                .filter(ts -> ts.getLogoutTime() != null)
                .collect(Collectors.groupingBy(
                        LoginTimestamp::getClient,
                        Collectors.summingLong(ts -> java.time.Duration.between(ts.getLoginTime(), ts.getLogoutTime()).getSeconds())
                ));

        return userOnlineTime.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateDailyStats() {
        LocalDate today = LocalDate.now();
        long userCount = clientRepository.count();
        long projectCount = projectRepository.count();
        DailyStats stats = dailyStatsRepository.findByDate(today)
                .orElse(new DailyStats());
        stats.setDate(today);
        stats.setUserCount(userCount);
        stats.setProjectCount(projectCount);
        dailyStatsRepository.save(stats);
    }

    public List<DailyStats> getLast7DaysStats() {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);
        return dailyStatsRepository.findAllByDateBetween(weekAgo, today);
    }

    public long countNewUsersInLastDays(int days) {
        LocalDate cutoff = LocalDate.now().minusDays(days);
        return clientRepository.findAll().stream()
                .filter(client -> client.getCreatedAt().isAfter(cutoff.atStartOfDay()))
                .count();
    }

    public long countBannedUsers() {
        return clientRepository.findAll().stream()
                .filter(client -> "ROLE_BANNED".equals(client.getRole()))
                .count();
    }

    public long countActiveUsersInLastDays(int days) {
        LocalDate cutoff = LocalDate.now().minusDays(days);
        return loginTimestampRepository.findAll().stream()
                .filter(ts -> ts.getLoginTime().isAfter(cutoff.atStartOfDay()))
                .map(LoginTimestamp::getClient)
                .distinct()
                .count();
    }

    public long calculateTotalOnlineTime() {
        List<LoginTimestamp> timestamps = loginTimestampRepository.findAll();
        return timestamps.stream()
                .filter(ts -> ts.getLogoutTime() != null)
                .mapToLong(ts -> java.time.Duration.between(ts.getLoginTime(), ts.getLogoutTime()).getSeconds())
                .sum();
    }

    // Вспомогательный класс для передачи данных активности
    public static class ActivityData {
        private String date;
        private Long loginCount;

        public ActivityData(String date, Long loginCount) {
            this.date = date;
            this.loginCount = loginCount;
        }

        public String getDate() {
            return date;
        }

        public Long getLoginCount() {
            return loginCount;
        }
    }
    @Transactional
    public void banUsers(List<Long> userIds) {
        System.out.println("Banning users with IDs: " + userIds);
        List<Client> users = clientRepository.findAllById(userIds);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("No users found with the provided IDs.");
        }
        users.forEach(user -> {
            if (!"ROLE_BANNED".equals(user.getRole())) {
                System.out.println("Updating user: " + user.getUsername() + " to ROLE_BANNED");
                user.setRole("ROLE_BANNED");
            }
        });
        clientRepository.saveAll(users);
        System.out.println("Successfully banned users: " + users.stream().map(Client::getUsername).collect(Collectors.toList()));
    }
    public List<WeeklyDistribution> getWeeklyDistribution() {
        List<WeeklyDistribution> distributionList = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            String dayName = dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("ru"));
            String formattedDate = date.format(formatter);

            long projectCount = projectRepository.countByCreatedAtBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            long userCount = clientRepository.countByCreatedAtBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay());

            distributionList.add(new WeeklyDistribution(dayName, formattedDate, projectCount, userCount));
        }

        return distributionList;
    }
    @Transactional
    public void approveUnbanRequests(List<Long> requestIds) {
        List<UnbanRequest> requests = unbanRequestRepository.findAllById(requestIds);
        if (requests.size() != requestIds.size()) {
            throw new IllegalArgumentException("Некоторые запросы не найдены.");
        }

        for (UnbanRequest request : requests) {
            Client user = request.getClient();
            user.setRole("ROLE_USER");
            clientRepository.save(user);
            unbanRequestRepository.delete(request);
            System.out.println("Одобрено разблокировка пользователя: " + user.getUsername());
        }
    }
    @Transactional
    public void declineUnbanRequests(List<Long> requestIds) {
        List<UnbanRequest> requests = unbanRequestRepository.findAllById(requestIds);
        if (requests.size() != requestIds.size()) {
            throw new IllegalArgumentException("Некоторые запросы не найдены.");
        }

        for (UnbanRequest request : requests) {
            unbanRequestRepository.delete(request);
            System.out.println("Отклонено разблокировка пользователя: " + request.getClient().getUsername());
        }
    }
    @Data
    public static class WeeklyDistribution {
        private String dayName;
        private String date;
        private long projectCount;
        private long userCount;

        public WeeklyDistribution(String dayName, String date, long projectCount, long userCount) {
            this.dayName = dayName;
            this.date = date;
            this.projectCount = projectCount;
            this.userCount = userCount;
        }

    }

}
