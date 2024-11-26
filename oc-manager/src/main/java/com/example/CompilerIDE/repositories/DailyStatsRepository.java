package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyStatsRepository extends JpaRepository<DailyStats, Long> {

    // Метод для поиска статистики по конкретной дате
    Optional<DailyStats> findByDate(LocalDate date);

    // Метод для поиска статистики в заданном диапазоне дат
    List<DailyStats> findAllByDateBetween(LocalDate startDate, LocalDate endDate);
}
