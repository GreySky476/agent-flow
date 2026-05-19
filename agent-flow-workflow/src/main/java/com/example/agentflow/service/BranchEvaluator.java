package com.example.agentflow.service;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import com.example.agentflow.entity.WorkflowNode;
import com.example.agentflow.model.WorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BranchEvaluator {

    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String evaluate(WorkflowNode node, WorkflowState state) {
        Map<String, Object> config = parseConfig(node.getConfigJson());
        String conditionType = (String) config.getOrDefault("conditionType", "EXPRESSION");
        String leftField = (String) config.get("leftField");
        Object leftValue = config.get("leftValue");
        String rightField = (String) config.get("rightField");
        Object rightValue = config.get("rightValue");
        String expression = (String) config.get("expression");

        Object left = resolveValue(leftField, leftValue, state);
        Object right = resolveValue(rightField, rightValue, state);

        boolean result = evaluateCondition(conditionType, left, right, expression, state);
        state.put("_branch_" + node.getNodeId(), result);

        return result ? "true" : "false";
    }

    private Object resolveValue(String field, Object literalValue, WorkflowState state) {
        if (field != null && !field.isBlank()) {
            return state.get(field);
        }
        return literalValue;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateCondition(String type, Object left, Object right,
                                       String expression, WorkflowState state) {
        return switch (type.toUpperCase()) {
            case "CONTAINS" -> contains(left, right);
            case "NOT_CONTAINS", "NOTCONTAINS" -> !contains(left, right);
            case "REGEX" -> matchesRegex(left, right);
            case "EQ", "EQUALS", "EQUAL" -> compareEquals(left, right);
            case "NEQ", "NOT_EQUALS", "NOTEQUALS" -> !compareEquals(left, right);
            case "GT", "GREATER_THAN", "GREATERTHAN" -> compare(left, right) > 0;
            case "LT", "LESS_THAN", "LESSTHAN" -> compare(left, right) < 0;
            case "GTE", "GREATER_EQUAL", "GREATEREQUAL" -> compare(left, right) >= 0;
            case "LTE", "LESS_EQUAL", "LESSEQUAL" -> compare(left, right) <= 0;
            case "EXPRESSION" -> evaluateSpel(expression, state);
            default -> {
                log.warn("Unknown condition type: {}", type);
                yield false;
            }
        };
    }

    private boolean contains(Object left, Object right) {
        if (left == null || right == null) return false;
        return left.toString().contains(right.toString());
    }

    private boolean matchesRegex(Object left, Object right) {
        if (left == null || right == null) return false;
        try {
            return Pattern.compile(right.toString()).matcher(left.toString()).find();
        } catch (Exception e) {
            log.warn("Invalid regex pattern: {}", right);
            return false;
        }
    }

    private boolean compareEquals(Object left, Object right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        try {
            if (left instanceof Number && right instanceof Number) {
                return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue()) == 0;
            }
        } catch (Exception ignored) {}
        return left.toString().equals(right.toString());
    }

    private int compare(Object left, Object right) {
        if (left == null || right == null) throw new IllegalArgumentException("Cannot compare null values");
        double l = Double.parseDouble(left.toString());
        double r = Double.parseDouble(right.toString());
        return Double.compare(l, r);
    }

    private boolean evaluateSpel(String expression, WorkflowState state) {
        if (expression == null || expression.isBlank()) return false;
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            state.getData().forEach(context::setVariable);
            Boolean result = spelParser.parseExpression(expression).getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.error("SpEL evaluation failed: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse branch config: {}", e.getMessage());
            return Map.of();
        }
    }
}
