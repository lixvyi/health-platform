package com.csu.health.portal.module.symptom.service;

import com.csu.health.portal.module.symptom.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import lombok.AllArgsConstructor;
import java.util.stream.Collectors;

/**
 * 症状知识库服务
 * 负责加载JSON知识库并提供查询功能
 */
@Slf4j
@Service
public class SymptomKnowledgeService {

    @Value("classpath:symptoms/symptoms.json")
    private Resource symptomsResource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 内存中的知识库数据
    private List<BodyPartDto> bodyParts;
    private Map<Integer, BodyPartDto> bodyPartMap;
    private List<SymptomDto> symptoms;
    private Map<Integer, SymptomDto> symptomMap;
    private Map<Integer, List<Integer>> partSymptomMap; // part_id -> symptom_ids
    private Map<Integer, List<DepartmentEntry>> symptomDepartmentMap; // symptom_id -> department entries with priority
    private List<EmergencyRule> emergencyRules;
    private List<CompoundRule> compoundRules;

    /**
     * 科室条目内部类（包含名称和优先级）
     */
    @Data
    @AllArgsConstructor
    public static class DepartmentEntry {
        private String name;
        private Integer priority;
    }

    /**
     * 急诊规则内部类
     */
    @Data
    public static class EmergencyRule {
        private Integer id;
        private Integer level;
        private String description;
        private RuleConditions conditions;
        private String adviceText;
        /**
         * 推荐科室列表（用于计分模型）
         */
        private List<String> recommendedDepts;
    }

    @Data
    public static class RuleConditions {
        private List<Integer> symptomsAll;
        private List<Integer> symptomsAny;
    }

    /**
     * 选科规则内部类（用于纠正挂错科）
     */
    @Data
    public static class CompoundRule {
        private Integer id;
        private String description;
        private RuleConditions conditions;
        private String correctDept;
        private String reason;
    }

    /**
     * 初始化加载知识库
     */
    @PostConstruct
    public void init() {
        try {
            loadKnowledgeBase();
            log.info("症状知识库加载成功: {}个部位, {}个症状, {}条规则",
                    bodyParts.size(), symptoms.size(), emergencyRules.size());
        } catch (IOException e) {
            log.error("症状知识库加载失败", e);
            throw new RuntimeException("症状知识库加载失败", e);
        }
    }

    /**
     * 从JSON文件加载知识库
     */
    private void loadKnowledgeBase() throws IOException {
        JsonNode root = objectMapper.readTree(symptomsResource.getInputStream());

        // 加载身体部位
        bodyParts = new ArrayList<>();
        bodyPartMap = new HashMap<>();
        JsonNode bodyPartsNode = root.get("body_parts");
        for (JsonNode node : bodyPartsNode) {
            BodyPartDto part = new BodyPartDto();
            part.setId(node.get("id").asInt());
            part.setName(node.get("name").asText());
            part.setParentId(node.has("parent_id") && !node.get("parent_id").isNull()
                    ? node.get("parent_id").asInt() : null);
            part.setChildren(new ArrayList<>());
            bodyParts.add(part);
            bodyPartMap.put(part.getId(), part);
        }

        // 构建部位树
        buildBodyPartTree();

        // 加载症状
        symptoms = new ArrayList<>();
        symptomMap = new HashMap<>();
        JsonNode symptomsNode = root.get("symptoms");
        for (JsonNode node : symptomsNode) {
            SymptomDto symptom = new SymptomDto();
            symptom.setId(node.get("id").asInt());
            symptom.setName(node.get("name").asText());
            symptom.setAliases(node.has("aliases") && !node.get("aliases").isNull()
                    ? node.get("aliases").asText() : null);
            symptom.setDescription(node.get("description").asText());
            symptoms.add(symptom);
            symptomMap.put(symptom.getId(), symptom);
        }

        // 加载部位-症状关联
        partSymptomMap = new HashMap<>();
        JsonNode partSymptomNode = root.get("part_symptom");
        for (JsonNode node : partSymptomNode) {
            int partId = node.get("part_id").asInt();
            int symptomId = node.get("symptom_id").asInt();
            partSymptomMap.computeIfAbsent(partId, k -> new ArrayList<>()).add(symptomId);
        }

        // 加载症状-科室映射（支持优先级）
        symptomDepartmentMap = new HashMap<>();
        JsonNode symptomDeptNode = root.get("symptom_department");
        for (JsonNode node : symptomDeptNode) {
            int symptomId = node.get("symptom_id").asInt();
            String deptName = node.get("department_name").asText();
            int priority = node.has("priority") ? node.get("priority").asInt() : 1;
            symptomDepartmentMap.computeIfAbsent(symptomId, k -> new ArrayList<>())
                    .add(new DepartmentEntry(deptName, priority));
        }

        // 加载急诊规则
        emergencyRules = new ArrayList<>();
        JsonNode rulesNode = root.get("emergency_rules");
        for (JsonNode node : rulesNode) {
            EmergencyRule rule = new EmergencyRule();
            rule.setId(node.get("id").asInt());
            rule.setLevel(node.get("level").asInt());
            rule.setDescription(node.get("description").asText());
            rule.setAdviceText(node.get("advice_text").asText());

            // 加载推荐科室列表
            if (node.has("recommended_depts")) {
                List<String> depts = new ArrayList<>();
                for (JsonNode deptNode : node.get("recommended_depts")) {
                    depts.add(deptNode.asText());
                }
                rule.setRecommendedDepts(depts);
            }

            JsonNode conditionsNode = node.get("conditions");
            RuleConditions conditions = new RuleConditions();
            if (conditionsNode.has("symptoms_all")) {
                conditions.setSymptomsAll(new ArrayList<>());
                for (JsonNode idNode : conditionsNode.get("symptoms_all")) {
                    conditions.getSymptomsAll().add(idNode.asInt());
                }
            }
            if (conditionsNode.has("symptoms_any")) {
                conditions.setSymptomsAny(new ArrayList<>());
                for (JsonNode idNode : conditionsNode.get("symptoms_any")) {
                    conditions.getSymptomsAny().add(idNode.asInt());
                }
            }
            rule.setConditions(conditions);
            emergencyRules.add(rule);
        }

        // 加载选科规则
        compoundRules = new ArrayList<>();
        JsonNode compoundNode = root.get("compound_rules");
        if (compoundNode != null) {
            for (JsonNode node : compoundNode) {
                CompoundRule rule = new CompoundRule();
                rule.setId(node.get("id").asInt());
                rule.setDescription(node.get("description").asText());
                rule.setCorrectDept(node.get("correct_dept").asText());
                rule.setReason(node.get("reason").asText());

                JsonNode condNode = node.get("conditions");
                RuleConditions conditions = new RuleConditions();
                if (condNode.has("symptoms_all")) {
                    conditions.setSymptomsAll(new ArrayList<>());
                    for (JsonNode idNode : condNode.get("symptoms_all")) {
                        conditions.getSymptomsAll().add(idNode.asInt());
                    }
                }
                if (condNode.has("symptoms_any")) {
                    conditions.setSymptomsAny(new ArrayList<>());
                    for (JsonNode idNode : condNode.get("symptoms_any")) {
                        conditions.getSymptomsAny().add(idNode.asInt());
                    }
                }
                rule.setConditions(conditions);
                compoundRules.add(rule);
            }
            log.info("加载 {} 条选科规则", compoundRules.size());
        }
    }

    /**
     * 构建身体部位树
     */
    private void buildBodyPartTree() {
        for (BodyPartDto part : bodyParts) {
            if (part.getParentId() != null) {
                BodyPartDto parent = bodyPartMap.get(part.getParentId());
                if (parent != null) {
                    parent.getChildren().add(part);
                }
            }
        }
    }

    /**
     * 获取所有身体部位（树形结构）
     */
    public List<BodyPartDto> getBodyPartTree() {
        return bodyParts.stream()
                .filter(p -> p.getParentId() == null)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定部位下的症状列表（仅返回该部位的直接关联症状）
     */
    public List<SymptomDto> getSymptomsByPartId(Integer partId) {
        log.info("请求获取部位 {} 的症状", partId);
        
        // 只获取当前部位的直接关联症状
        List<Integer> symptomIds = partSymptomMap.getOrDefault(partId, Collections.emptyList());
        
        List<SymptomDto> result = symptomIds.stream()
                .map(symptomMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        log.info("部位 {} 共返回 {} 个症状", partId, result.size());
        return result;
    }

    /**
     * 执行症状自查（简化计分模型）
     */
    public SymptomCheckResponse checkSymptoms(List<Integer> symptomIds) {
        Set<Integer> inputSet = new HashSet<>(symptomIds);

        // ========== 步骤一：计算科室基础得分 ==========
        // scoreMap: 科室名 -> 得分
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        for (Integer symptomId : symptomIds) {
            List<DepartmentEntry> entries = symptomDepartmentMap.getOrDefault(symptomId, Collections.emptyList());
            for (DepartmentEntry entry : entries) {
                double baseScore = 1.0 / entry.getPriority();
                scoreMap.merge(entry.getName(), baseScore, Double::sum);
            }
        }

        // ========== 评估规则并收集匹配的规则 ==========
        List<AlertDto> alerts = new ArrayList<>();
        List<String> reminders = new ArrayList<>();
        List<RuleSuggestionDto> suggestions = new ArrayList<>();
        List<EmergencyRule> matchedRules = new ArrayList<>();

        for (EmergencyRule rule : emergencyRules) {
            List<Integer> reqAll = rule.getConditions().getSymptomsAll();
            List<Integer> reqAny = rule.getConditions().getSymptomsAny();

            if (reqAll == null) reqAll = Collections.emptyList();
            if (reqAny == null) reqAny = Collections.emptyList();

            boolean allMatch = reqAll.isEmpty() || reqAll.stream().allMatch(inputSet::contains);
            boolean anyMatch = reqAny.isEmpty() || reqAny.stream().anyMatch(inputSet::contains);

            if (allMatch && anyMatch) {
                // 仅对 level 2 生成警报
                if (rule.getLevel() == 2) {
                    alerts.add(buildAlert(rule));
                } else if (rule.getLevel() == 3 || rule.getLevel() == 4) {
                    // level 3/4 生成建议文本
                    suggestions.add(RuleSuggestionDto.builder()
                            .id(rule.getId())
                            .level(rule.getLevel())
                            .description(rule.getDescription())
                            .advice(rule.getAdviceText())
                            .build());
                }
                matchedRules.add(rule);
            } else if (!reqAll.isEmpty() && reqAll.stream().anyMatch(inputSet::contains) && !allMatch) {
                // 部分匹配 - 生成提醒
                List<Integer> missing = reqAll.stream()
                        .filter(id -> !inputSet.contains(id))
                        .collect(Collectors.toList());
                String missingNames = missing.stream()
                        .map(id -> symptomMap.containsKey(id) ? symptomMap.get(id).getName() : "未知症状")
                        .collect(Collectors.joining("、"));
                reminders.add(String.format("如同时伴有 %s，请警惕：%s", missingNames, rule.getDescription()));
            }
        }

        // 按级别排序警报
        alerts.sort(Comparator.comparing(AlertDto::getLevel));

        // ========== 步骤二：紧急规则加权 ==========
        // 如果触发了紧急规则（alerts 非空），则对规则中建议的科室进行额外加权 +1
        Set<String> emergencyDepts = new LinkedHashSet<>();
        if (!alerts.isEmpty()) {
            for (EmergencyRule rule : matchedRules) {
                if (rule.getLevel() == 2) {
                    List<String> depts = rule.getRecommendedDepts();
                    if (depts != null && !depts.isEmpty()) {
                        emergencyDepts.addAll(depts);
                    }
                }
            }
            // 若规则未指定科室，默认加权"急诊科"
            if (emergencyDepts.isEmpty()) {
                emergencyDepts.add("急诊科");
            }
            // 对紧急科室统一加1分
            double emergencyBonus = 3.0;
            for (String dept : emergencyDepts) {
                scoreMap.merge(dept, emergencyBonus, Double::sum);
            }
        }

        // ========== 步骤三：选科规则匹配 ==========
        // 匹配 compound_rules，强制指定科室为最高分，并将 reason 加入 suggestions
        // 如果有选科规则匹配，则覆盖之前 level 3/4 的所有建议
        boolean compoundMatched = false;
        for (CompoundRule rule : compoundRules) {
            List<Integer> reqAll = rule.getConditions().getSymptomsAll();
            List<Integer> reqAny = rule.getConditions().getSymptomsAny();

            if (reqAll == null) reqAll = Collections.emptyList();
            if (reqAny == null) reqAny = Collections.emptyList();

            boolean allMatch = reqAll.isEmpty() || reqAll.stream().allMatch(inputSet::contains);
            boolean anyMatch = reqAny.isEmpty() || reqAny.stream().anyMatch(inputSet::contains);

            if (allMatch && anyMatch) {
                // 将 correct_dept 指定的科目设为最高分
                // 如果有多个科室（以 / 分隔），分别处理
                String[] depts = rule.getCorrectDept().split("/");
                for (String dept : depts) {
                    String trimmed = dept.trim();
                    if (!trimmed.isEmpty()) {
                        // 给指定科室加一个极大的分数，确保成为最高分
                        scoreMap.merge(trimmed, 10000.0, Double::sum);
                    }
                }

                compoundMatched = true;
                log.info("选科规则 {} 触发: {}", rule.getId(), rule.getDescription());
            }
        }

        // 如果选科规则有匹配，clear 之前的 level 3/4 suggestions，只添加选科规则的建议
        if (compoundMatched) {
            suggestions.clear();
            for (CompoundRule rule : compoundRules) {
                List<Integer> reqAll = rule.getConditions().getSymptomsAll();
                List<Integer> reqAny = rule.getConditions().getSymptomsAny();

                if (reqAll == null) reqAll = Collections.emptyList();
                if (reqAny == null) reqAny = Collections.emptyList();

                boolean allMatch = reqAll.isEmpty() || reqAll.stream().allMatch(inputSet::contains);
                boolean anyMatch = reqAny.isEmpty() || reqAny.stream().anyMatch(inputSet::contains);

                if (allMatch && anyMatch) {
                    suggestions.add(RuleSuggestionDto.builder()
                            .id(rule.getId())
                            .level(0)
                            .description(rule.getDescription())
                            .advice(rule.getReason())
                            .build());
                }
            }
        }

        // ========== 步骤四：排序输出 ==========
        // 按得分降序排列；若得分相同，则按科室名称拼音排序
        List<DepartmentDto> departments = scoreMap.entrySet().stream()
                .map(e -> DepartmentDto.builder()
                        .name(e.getKey())
                        .priority(null)
                        .recommended(false) // 先全部设为 false
                        .priorityTag(null)
                        .score(e.getValue())
                        .build())
                .sorted((a, b) -> {
                    // 得分降序
                    int cmp = Double.compare(b.getScore(), a.getScore());
                    if (cmp != 0) return cmp;
                    // 同分时，紧急推荐科室优先
                    boolean aIsEmergency = emergencyDepts.contains(a.getName());
                    boolean bIsEmergency = emergencyDepts.contains(b.getName());
                    if (aIsEmergency && !bIsEmergency) return -1;
                    if (!aIsEmergency && bIsEmergency) return 1;
                    // 再按拼音升序
                    return a.getName().compareTo(b.getName());
                })
                .collect(Collectors.toList());

        // 标记得分最高的科室为 recommended
        if (!departments.isEmpty()) {
            double maxScore = departments.get(0).getScore();
            for (DepartmentDto dept : departments) {
                if (Math.abs(dept.getScore() - maxScore) < 0.0001) { // 使用容差比较浮点数
                    dept.setRecommended(true);
                } else {
                    break; // 因为已排序，后续科室分数一定更低
                }
            }
        }

        return SymptomCheckResponse.builder()
                .departments(departments)
                .alerts(alerts)
                .reminders(reminders)
                .suggestions(suggestions)
                .disclaimer("以上信息仅供参考，不能替代专业诊断，请前往正规医院就诊。")
                .build();
    }

    /**
     * 构建警报DTO（仅用于 level 2）
     */
    private AlertDto buildAlert(EmergencyRule rule) {
        return AlertDto.builder()
                .id(rule.getId())
                .level(rule.getLevel())
                .color("orange")
                .description(rule.getDescription())
                .advice(rule.getAdviceText())
                .responseTime(null) // 不再显示响应时限
                .build();
    }

    /**
     * 热重载知识库
     */
    public void reload() throws IOException {
        loadKnowledgeBase();
        log.info("症状知识库重新加载成功");
    }
}
