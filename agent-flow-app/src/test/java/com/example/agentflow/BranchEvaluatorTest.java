package com.example.agentflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.agentflow.entity.WorkflowNode;
import com.example.agentflow.model.WorkflowState;
import com.example.agentflow.service.BranchEvaluator;

class BranchEvaluatorTest {

    private BranchEvaluator evaluator;
    private WorkflowState state;

    @BeforeEach
    void setUp() {
        evaluator = new BranchEvaluator();
        state = new WorkflowState();
    }

    @Test
    @DisplayName("CONTAINS - should return true when left contains right")
    void testContainsTrue() {
        state.put("text", "hello world");
        WorkflowNode node = createBranchNode("CONTAINS", "text", null, null, "world");
        assertEquals("true", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("CONTAINS - should return false when left does not contain right")
    void testContainsFalse() {
        state.put("text", "hello world");
        WorkflowNode node = createBranchNode("CONTAINS", "text", null, null, "xyz");
        assertEquals("false", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("NOT_CONTAINS - should return true when left does not contain right")
    void testNotContainsTrue() {
        state.put("text", "hello world");
        WorkflowNode node = createBranchNode("NOT_CONTAINS", "text", null, null, "xyz");
        assertEquals("true", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("NOT_CONTAINS - should return false when left contains right")
    void testNotContainsFalse() {
        state.put("text", "hello world");
        WorkflowNode node = createBranchNode("NOT_CONTAINS", "text", null, null, "hello");
        assertEquals("false", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("REGEX - should match pattern")
    void testRegexTrue() {
        state.put("text", "hello123world");
        WorkflowNode node = createBranchNode("REGEX", "text", null, null, "[0-9]+");
        assertEquals("true", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("REGEX - should not match pattern")
    void testRegexFalse() {
        state.put("text", "helloworld");
        WorkflowNode node = createBranchNode("REGEX", "text", null, null, "[0-9]+");
        assertEquals("false", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("EQ - should return true for equal values")
    void testEqTrue() {
        state.put("status", "ok");
        WorkflowNode node = createBranchNode("EQ", "status", null, null, "ok");
        assertEquals("true", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("EQ - should return false for different values")
    void testEqFalse() {
        state.put("status", "error");
        WorkflowNode node = createBranchNode("EQ", "status", null, null, "ok");
        assertEquals("false", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("NEQ - should return true for different values")
    void testNeqTrue() {
        state.put("status", "error");
        WorkflowNode node = createBranchNode("NEQ", "status", null, null, "ok");
        assertEquals("true", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("GT - should return true when left > right")
    void testGtTrue() {
        state.put("score", "85");
        WorkflowNode node = createBranchNode("GT", "score", null, null, "60");
        assertEquals("true", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("GT - should return false when left <= right")
    void testGtFalse() {
        state.put("score", "50");
        WorkflowNode node = createBranchNode("GT", "score", null, null, "60");
        assertEquals("false", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("LT - should return true when left < right")
    void testLtTrue() {
        state.put("age", "18");
        WorkflowNode node = createBranchNode("LT", "age", null, null, "60");
        assertEquals("true", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("GTE - should return true when left >= right (equal case)")
    void testGteTrue() {
        state.put("score", "60");
        WorkflowNode node = createBranchNode("GTE", "score", null, null, "60");
        assertEquals("true", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("LTE - should return true when left <= right")
    void testLteTrue() {
        state.put("score", "20");
        WorkflowNode node = createBranchNode("LTE", "score", null, null, "60");
        assertEquals("true", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("EXPRESSION - SpEL evaluation with regex")
    void testSpelExpression() {
        state.put("text", "hello world");
        WorkflowNode node = new WorkflowNode();
        node.setNodeId("branch_1");
        node.setNodeType("BRANCH");
        node.setConfigJson("{\"conditionType\":\"EXPRESSION\",\"expression\":\"#text.contains('hello')\"}");
        assertEquals("true", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("Null left value should return false")
    void testNullValue() {
        WorkflowNode node = createBranchNode("CONTAINS", "nonexistent", null, null, "anything");
        assertEquals("false", evaluator.evaluate(node, state));
    }

    @Test
    @DisplayName("Branch result stored in state")
    void testResultStoredInState() {
        state.put("text", "hello");
        WorkflowNode node = createBranchNode("CONTAINS", "text", null, null, "hello");
        evaluator.evaluate(node, state);
        assertTrue((Boolean) state.get("_branch_branch_1"));
    }

    private WorkflowNode createBranchNode(String conditionType, String leftField,
                                           Object leftValue, Object rightField, Object rightValue) {
        WorkflowNode node = new WorkflowNode();
        node.setNodeId("branch_1");
        node.setNodeType("BRANCH");
        String config = String.format(
                "{\"conditionType\":\"%s\",\"leftField\":\"%s\",\"leftValue\":%s,\"rightField\":%s,\"rightValue\":%s}",
                conditionType,
                leftField,
                leftValue instanceof String ? "\"" + leftValue + "\"" : leftValue,
                rightField instanceof String ? "\"" + rightField + "\"" : rightField,
                rightValue instanceof String ? "\"" + rightValue + "\"" : rightValue);
        node.setConfigJson("{\"conditionType\":\"" + conditionType + "\"," +
                "\"leftField\":\"" + leftField + "\"," +
                "\"rightValue\":\"" + rightValue + "\"}");
        return node;
    }
}
