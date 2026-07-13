package com.csu.health.portal.module.datapool.service;

import com.csu.health.portal.module.datapool.dto.DataPoolDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataGovernanceService {

    private static final int TREND_DAYS = 14;
    private static final Set<String> ISSUE_STATUSES = Set.of("PENDING", "IN_PROGRESS", "RESOLVED", "IGNORED");

    private final JdbcTemplate jdbcTemplate;
    private final DataPoolService dataPoolService;

    public DataPoolDto.GovernanceDashboard dashboard() {
        List<DataPoolDto.GovernanceIssue> issues = listIssues();
        Map<String, List<DataPoolDto.GovernanceIssue>> issuesByDataset = groupIssues(issues);
        List<DataPoolDto.GovernanceSource> sources = listDatasetSources(issuesByDataset);
        appendCollectionSources(sources, issuesByDataset);

        DataPoolDto.GovernanceDashboard dashboard = new DataPoolDto.GovernanceDashboard();
        dashboard.setGeneratedAt(LocalDateTime.now().withNano(0).toString());
        dashboard.setSources(sources);
        dashboard.setIssues(issues);
        dashboard.setSummary(buildSummary(sources, issues));
        dashboard.setTrends(buildTrends(sources));
        dashboard.setFreshnessDistribution(buildFreshnessDistribution(sources));
        return dashboard;
    }

    public DataPoolDto.GovernanceIssue updateIssue(long id, DataPoolDto.GovernanceIssueUpdate request) {
        String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!ISSUE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("不支持的处理状态");
        }
        int updated = jdbcTemplate.update("""
                UPDATE data_governance_issue
                SET status = ?, handler_note = ?, handled_by = ?,
                    handled_at = CASE WHEN ? IN ('RESOLVED', 'IGNORED') THEN NOW() ELSE NULL END
                WHERE id = ?
                """, status, request.getHandlerNote(), request.getHandledBy(), status, id);
        if (updated == 0) {
            throw new IllegalArgumentException("异常记录不存在");
        }
        return jdbcTemplate.queryForObject("""
                SELECT id, dataset_code, source_name, issue_type, description, detected_at,
                       status, handler_note, handled_by, handled_at
                FROM data_governance_issue WHERE id = ?
                """, (rs, rowNum) -> mapIssue(rs), id);
    }

    private List<DataPoolDto.GovernanceSource> listDatasetSources(
            Map<String, List<DataPoolDto.GovernanceIssue>> issuesByDataset) {
        try {
            return jdbcTemplate.query("""
                    SELECT d.dataset_code, d.dataset_name, d.dataset_type, d.source_name,
                           d.source_url, d.source_file, d.update_frequency, d.update_status,
                           d.record_count, d.duplicate_count, d.error_count, d.completeness_rate,
                           COALESCE(d.last_collected_at, d.last_imported_at, d.updated_at) AS last_collected_at,
                           COALESCE(
                               (SELECT r.inserted_count
                                FROM data_resource_import_run r
                                WHERE r.dataset_id = d.id
                                ORDER BY r.started_at DESC LIMIT 1),
                               d.record_count
                           ) AS latest_added_count,
                           COALESCE(
                               (SELECT SUM(r.scanned_count)
                                FROM data_resource_import_run r
                                WHERE r.dataset_id = d.id),
                               d.record_count + d.duplicate_count + d.error_count
                           ) AS processed_count,
                           COALESCE(
                               (SELECT SUM(r.error_count)
                                FROM data_resource_import_run r
                                WHERE r.dataset_id = d.id),
                               d.error_count
                           ) AS processed_error_count
                    FROM data_resource_dataset d
                    ORDER BY d.dataset_name
                    """, (rs, rowNum) -> {
                DataPoolDto.GovernanceSource source = new DataPoolDto.GovernanceSource();
                source.setDatasetCode(rs.getString("dataset_code"));
                source.setDatasetName(rs.getString("dataset_name"));
                source.setSourceType(rs.getString("dataset_type"));
                source.setSourceName(rs.getString("source_name"));
                source.setOriginalUrl(rs.getString("source_url"));
                source.setSourceFile(rs.getString("source_file"));
                source.setTotalRecords(rs.getLong("record_count"));
                source.setLatestAddedRecords(rs.getLong("latest_added_count"));

                long processed = rs.getLong("processed_count");
                long errors = rs.getLong("processed_error_count");
                source.setProcessedRecords(processed);
                source.setErrorRecords(errors);
                source.setSuccessRate(processed > 0
                        ? percent(Math.max(0, processed - errors), processed)
                        : (isSuccess(rs.getString("update_status")) ? 100.0 : 0.0));

                long duplicates = rs.getLong("duplicate_count");
                source.setDuplicateRecords(duplicates);
                long denominator = source.getTotalRecords() + duplicates + rs.getLong("error_count");
                source.setDuplicateRatio(denominator > 0 ? percent(duplicates, denominator) : 0.0);

                BigDecimal completeness = rs.getBigDecimal("completeness_rate");
                source.setMissingFieldRatio(completeness == null
                        ? null
                        : roundOne(Math.max(0, 100.0 - completeness.doubleValue() * 100.0)));

                Timestamp lastCollected = rs.getTimestamp("last_collected_at");
                applyFreshness(source, lastCollected == null ? null : lastCollected.toLocalDateTime(),
                        rs.getString("update_frequency"));
                source.setLicenseName(resolveLicense(source.getSourceName(), source.getOriginalUrl()));
                applyIssueState(source, issuesByDataset.get(source.getDatasetCode()));
                return source;
            });
        } catch (Exception e) {
            log.warn("Unable to build data governance source metrics: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void appendCollectionSources(List<DataPoolDto.GovernanceSource> sources,
                                         Map<String, List<DataPoolDto.GovernanceIssue>> issuesByDataset) {
        DataPoolDto.CollectStatus collectStatus = dataPoolService.collectStatus();
        if (collectStatus == null || collectStatus.getSources() == null) {
            return;
        }
        Map<String, DataPoolDto.GovernanceSource> uniqueSources = new LinkedHashMap<>();
        sources.forEach(source -> uniqueSources.put(source.getDatasetCode(), source));
        LocalDateTime collectedAt = parseDateTime(collectStatus.getFinishedAt());

        for (DataPoolDto.SourceResult result : collectStatus.getSources()) {
            if (result.getSourceId() == null || uniqueSources.containsKey(result.getSourceId())) {
                continue;
            }
            DataPoolDto.GovernanceSource source = new DataPoolDto.GovernanceSource();
            source.setDatasetCode(result.getSourceId());
            source.setDatasetName(result.getSourceName());
            source.setSourceName(result.getSourceName());
            source.setSourceType(result.getSourceId().contains("mirror") ? "OPEN_DATA" : "CRAWL");
            source.setTotalRecords(result.getRecordCount());
            source.setLatestAddedRecords(result.getRecordCount());
            source.setProcessedRecords(result.getRecordCount());
            source.setDuplicateRecords(0);
            source.setErrorRecords(isSuccess(result.getStatus()) ? 0 : result.getRecordCount());
            source.setSuccessRate(isSuccess(result.getStatus()) ? 100.0 : 0.0);
            source.setDuplicateRatio(null);
            source.setMissingFieldRatio(null);
            source.setOriginalUrl(resolveOriginalUrl(result.getSourceId()));
            source.setLicenseName(resolveLicense(source.getSourceName(), source.getOriginalUrl()));
            applyFreshness(source, collectedAt, "ON_DEMAND");
            applyIssueState(source, issuesByDataset.get(source.getDatasetCode()));
            uniqueSources.put(source.getDatasetCode(), source);
        }
        sources.clear();
        sources.addAll(uniqueSources.values());
    }

    private DataPoolDto.GovernanceSummary buildSummary(List<DataPoolDto.GovernanceSource> sources,
                                                       List<DataPoolDto.GovernanceIssue> issues) {
        DataPoolDto.GovernanceSummary summary = new DataPoolDto.GovernanceSummary();
        summary.setSourceCount(sources.size());
        summary.setHealthySourceCount((int) sources.stream()
                .filter(source -> source.getSuccessRate() >= 98.0
                        && !"STALE".equals(source.getFreshnessLevel())
                        && !"PENDING".equals(source.getAnomalyStatus())
                        && !"IN_PROGRESS".equals(source.getAnomalyStatus()))
                .count());
        summary.setTotalRecords(sources.stream().mapToLong(DataPoolDto.GovernanceSource::getTotalRecords).sum());
        long processedRecords = sources.stream()
                .mapToLong(DataPoolDto.GovernanceSource::getProcessedRecords).sum();
        long errorRecords = sources.stream()
                .mapToLong(DataPoolDto.GovernanceSource::getErrorRecords).sum();
        summary.setAverageSuccessRate(processedRecords == 0
                ? 0.0
                : percent(Math.max(0, processedRecords - errorRecords), processedRecords));
        summary.setFreshSourceCount((int) sources.stream()
                .filter(source -> "FRESH".equals(source.getFreshnessLevel())).count());
        summary.setOpenIssueCount((int) issues.stream()
                .filter(issue -> "PENDING".equals(issue.getStatus()) || "IN_PROGRESS".equals(issue.getStatus()))
                .count());

        long duplicateCount = 0;
        long duplicateBase = 0;
        double missingTotal = 0;
        int missingSamples = 0;
        LocalDate latestDate = null;
        for (DataPoolDto.GovernanceSource source : sources) {
            if (source.getDuplicateRatio() != null) {
                duplicateBase += source.getProcessedRecords();
                duplicateCount += source.getDuplicateRecords();
            }
            if (source.getMissingFieldRatio() != null) {
                missingTotal += source.getMissingFieldRatio();
                missingSamples++;
            }
            LocalDate sourceDate = parseDate(source.getLastCollectedAt());
            if (sourceDate != null && (latestDate == null || sourceDate.isAfter(latestDate))) {
                latestDate = sourceDate;
            }
        }
        summary.setDuplicateRatio(duplicateBase == 0 ? null : percent(duplicateCount, duplicateBase));
        summary.setMissingFieldRatio(missingSamples == 0 ? null : roundOne(missingTotal / missingSamples));

        LocalDate finalLatestDate = latestDate;
        summary.setLatestAddedRecords(finalLatestDate == null ? 0 : sources.stream()
                .filter(source -> finalLatestDate.equals(parseDate(source.getLastCollectedAt())))
                .mapToLong(DataPoolDto.GovernanceSource::getLatestAddedRecords).sum());
        return summary;
    }

    private List<DataPoolDto.GovernanceTrendPoint> buildTrends(List<DataPoolDto.GovernanceSource> sources) {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(TREND_DAYS - 1L);
        Map<LocalDate, Long> additions = new HashMap<>();
        for (DataPoolDto.GovernanceSource source : sources) {
            LocalDate date = parseDate(source.getLastCollectedAt());
            if (date != null && !date.isBefore(firstDay) && !date.isAfter(today)) {
                additions.merge(date, source.getLatestAddedRecords(), Long::sum);
            }
        }

        long currentTotal = sources.stream().mapToLong(DataPoolDto.GovernanceSource::getTotalRecords).sum();
        long additionsInWindow = additions.values().stream().mapToLong(Long::longValue).sum();
        long runningTotal = Math.max(0, currentTotal - additionsInWindow);
        List<DataPoolDto.GovernanceTrendPoint> trends = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < TREND_DAYS; dayOffset++) {
            LocalDate date = firstDay.plusDays(dayOffset);
            long added = additions.getOrDefault(date, 0L);
            runningTotal += added;
            DataPoolDto.GovernanceTrendPoint point = new DataPoolDto.GovernanceTrendPoint();
            point.setDate(date.toString());
            point.setAddedRecords(added);
            point.setTotalRecords(runningTotal);
            trends.add(point);
        }
        return trends;
    }

    private Map<String, Integer> buildFreshnessDistribution(List<DataPoolDto.GovernanceSource> sources) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("FRESH", 0);
        distribution.put("WARNING", 0);
        distribution.put("STALE", 0);
        distribution.put("UNKNOWN", 0);
        sources.forEach(source -> distribution.compute(source.getFreshnessLevel(),
                (key, value) -> value == null ? 1 : value + 1));
        return distribution;
    }

    private List<DataPoolDto.GovernanceIssue> listIssues() {
        try {
            return jdbcTemplate.query("""
                    SELECT id, dataset_code, source_name, issue_type, description, detected_at,
                           status, handler_note, handled_by, handled_at
                    FROM data_governance_issue
                    ORDER BY FIELD(status, 'PENDING', 'IN_PROGRESS', 'RESOLVED', 'IGNORED'), detected_at DESC
                    """, (rs, rowNum) -> mapIssue(rs));
        } catch (Exception e) {
            log.debug("data_governance_issue is not available yet: {}", e.getMessage());
            return List.of();
        }
    }

    private DataPoolDto.GovernanceIssue mapIssue(java.sql.ResultSet rs) throws java.sql.SQLException {
        DataPoolDto.GovernanceIssue issue = new DataPoolDto.GovernanceIssue();
        issue.setId(rs.getLong("id"));
        issue.setDatasetCode(rs.getString("dataset_code"));
        issue.setSourceName(rs.getString("source_name"));
        issue.setIssueType(rs.getString("issue_type"));
        issue.setDescription(rs.getString("description"));
        issue.setDetectedAt(toText(rs.getTimestamp("detected_at")));
        issue.setStatus(rs.getString("status"));
        issue.setHandlerNote(rs.getString("handler_note"));
        issue.setHandledBy(rs.getString("handled_by"));
        issue.setHandledAt(toText(rs.getTimestamp("handled_at")));
        return issue;
    }

    private static Map<String, List<DataPoolDto.GovernanceIssue>> groupIssues(
            List<DataPoolDto.GovernanceIssue> issues) {
        Map<String, List<DataPoolDto.GovernanceIssue>> grouped = new HashMap<>();
        for (DataPoolDto.GovernanceIssue issue : issues) {
            grouped.computeIfAbsent(issue.getDatasetCode(), key -> new ArrayList<>()).add(issue);
        }
        return grouped;
    }

    private static void applyIssueState(DataPoolDto.GovernanceSource source,
                                        List<DataPoolDto.GovernanceIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            source.setAnomalyStatus("NORMAL");
            source.setAnomalyCount(0);
            return;
        }
        List<DataPoolDto.GovernanceIssue> openIssues = issues.stream()
                .filter(issue -> "PENDING".equals(issue.getStatus()) || "IN_PROGRESS".equals(issue.getStatus()))
                .toList();
        source.setAnomalyCount(openIssues.size());
        if (openIssues.stream().anyMatch(issue -> "IN_PROGRESS".equals(issue.getStatus()))) {
            source.setAnomalyStatus("IN_PROGRESS");
        } else if (!openIssues.isEmpty()) {
            source.setAnomalyStatus("PENDING");
        } else {
            source.setAnomalyStatus("RESOLVED");
        }
    }

    private static void applyFreshness(DataPoolDto.GovernanceSource source,
                                       LocalDateTime lastCollectedAt, String frequency) {
        if (lastCollectedAt == null) {
            source.setLastCollectedAt(null);
            source.setFreshnessLevel("UNKNOWN");
            source.setFreshnessText("暂无采集时间");
            source.setFreshnessDays(null);
            return;
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(lastCollectedAt.toLocalDate(), LocalDate.now()));
        long freshDays = 30;
        long warningDays = 60;
        String normalizedFrequency = frequency == null ? "" : frequency.toUpperCase(Locale.ROOT);
        switch (normalizedFrequency) {
            case "DAILY" -> { freshDays = 2; warningDays = 4; }
            case "WEEKLY" -> { freshDays = 8; warningDays = 14; }
            case "MONTHLY" -> { freshDays = 35; warningDays = 60; }
            case "QUARTERLY" -> { freshDays = 100; warningDays = 150; }
            case "YEARLY" -> { freshDays = 400; warningDays = 550; }
            case "ON_DEMAND" -> { freshDays = 90; warningDays = 180; }
            default -> { }
        }
        source.setLastCollectedAt(lastCollectedAt.withNano(0).toString());
        source.setFreshnessDays(days);
        if (days <= freshDays) {
            source.setFreshnessLevel("FRESH");
            source.setFreshnessText(days == 0 ? "今天更新" : days + "天前更新");
        } else if (days <= warningDays) {
            source.setFreshnessLevel("WARNING");
            source.setFreshnessText("需要关注");
        } else {
            source.setFreshnessLevel("STALE");
            source.setFreshnessText("已过期");
        }
    }

    private static boolean isSuccess(String status) {
        return status != null && Set.of("SUCCESS", "OK", "IMPORTED", "EXPORTED").contains(status.toUpperCase(Locale.ROOT));
    }

    private static String resolveLicense(String sourceName, String sourceUrl) {
        String source = sourceName == null ? "" : sourceName;
        String url = sourceUrl == null ? "" : sourceUrl;
        if (source.contains("国家统计局")) {
            return "政府开放数据（使用时注明来源）";
        }
        if (source.contains("疾控") || url.contains("gov.cn") || url.contains("chinacdc.cn")) {
            return "政府网站公开信息（使用时注明来源）";
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return "未标注开放许可证";
        }
        return "按原网站使用条款";
    }

    private static String resolveOriginalUrl(String sourceId) {
        return switch (sourceId) {
            case "gov_cn_shuju" -> "https://www.gov.cn/shuju/";
            case "stats_gov_cn_sj", "nbs_open_mirror" -> "https://data.stats.gov.cn/";
            case "shanghai_open_mirror" -> "https://data.sh.gov.cn/";
            default -> null;
        };
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value.replace(' ', 'T'));
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static LocalDate parseDate(String value) {
        LocalDateTime dateTime = parseDateTime(value);
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    private static String toText(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().withNano(0).toString();
    }

    private static double percent(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : roundOne(numerator * 100.0 / denominator);
    }

    private static double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
