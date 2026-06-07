package com.aidecision.agentic.orchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates gate expressions against completed step outputs.
 * Supported forms:
 * <ul>
 *   <li>{@code steps.{stepId}.output.{field}} — truthy check</li>
 *   <li>{@code steps.{stepId}.output.{field} > 5}</li>
 *   <li>{@code steps.{stepId}.output.{field} == 'frozen'}</li>
 *   <li>{@code steps.{stepId}.output.{field} == true}</li>
 * </ul>
 */
@Component
public class GateConditionEvaluator {

    private static final Pattern TRUTHY =
            Pattern.compile("^steps\\.([A-Za-z0-9_-]+)\\.output\\.([A-Za-z0-9_.-]+)$");
    private static final Pattern COMPARE = Pattern.compile(
            "^steps\\.([A-Za-z0-9_-]+)\\.output\\.([A-Za-z0-9_.-]+)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");

    private final ObjectMapper mapper;

    public GateConditionEvaluator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public boolean evaluate(String expression, Map<String, String> outputJsonByStepKey) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Gate expression is required");
        }
        String expr = expression.trim();

        Matcher cmp = COMPARE.matcher(expr);
        if (cmp.matches()) {
            Object left = resolveValue(cmp.group(1), cmp.group(2), outputJsonByStepKey);
            String op = cmp.group(3);
            Object right = parseLiteral(cmp.group(4).trim());
            return compare(left, op, right);
        }

        Matcher truthy = TRUTHY.matcher(expr);
        if (truthy.matches()) {
            Object value = resolveValue(truthy.group(1), truthy.group(2), outputJsonByStepKey);
            return isTruthy(value);
        }

        throw new IllegalArgumentException("Unsupported gate expression: " + expression);
    }

    private Object resolveValue(String stepKey, String fieldPath, Map<String, String> outputs) {
        String json = outputs.get(stepKey);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return navigate(map, fieldPath);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read output for step " + stepKey + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Object navigate(Map<String, Object> root, String fieldPath) {
        Object current = root;
        for (String segment : fieldPath.split("\\.")) {
            if (current == null) {
                return null;
            }
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static Object parseLiteral(String raw) {
        if (raw.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (raw.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (raw.equalsIgnoreCase("null")) {
            return null;
        }
        if ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith("\"") && raw.endsWith("\""))) {
            return raw.substring(1, raw.length() - 1);
        }
        try {
            if (raw.contains(".")) {
                return Double.parseDouble(raw);
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    private static boolean compare(Object left, String op, Object right) {
        if ("==".equals(op)) {
            return valuesEqual(left, right);
        }
        if ("!=".equals(op)) {
            return !valuesEqual(left, right);
        }
        Double l = toDouble(left);
        Double r = toDouble(right);
        if (l == null || r == null) {
            throw new IllegalArgumentException("Numeric comparison requires numeric operands");
        }
        return switch (op) {
            case ">" -> l > r;
            case "<" -> l < r;
            case ">=" -> l >= r;
            case "<=" -> l <= r;
            default -> throw new IllegalArgumentException("Unknown operator: " + op);
        };
    }

    private static boolean valuesEqual(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof Number && right instanceof Number) {
            return Double.compare(toDouble(left), toDouble(right)) == 0;
        }
        if (left instanceof Boolean || right instanceof Boolean) {
            return Boolean.valueOf(String.valueOf(left)).equals(Boolean.valueOf(String.valueOf(right)));
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (value instanceof String s) {
            return !s.isBlank() && !"false".equalsIgnoreCase(s) && !"0".equals(s);
        }
        return true;
    }
}
