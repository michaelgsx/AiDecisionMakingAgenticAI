package com.aidecision.agentic.orchestrator;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.entity.OrchestratorTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Post-processes planner output: when a compound question was under-split into one NL2SQL step,
 * rebuilds the DAG with one natural_language_to_sql step per sub-question (parallel) + llm_answer.
 */
@Component
public class CompoundWorkflowExpander {

    private static final Logger log = LoggerFactory.getLogger(CompoundWorkflowExpander.class);

    private static final String NL2SQL = "natural_language_to_sql";
    private static final String LLM_ANSWER = "llm_answer";

    private final OrchestratorProperties props;

    public CompoundWorkflowExpander(OrchestratorProperties props) {
        this.props = props;
    }

    public WorkflowDag expandIfNeeded(String question, WorkflowDag dag, Map<String, OrchestratorTool> tools) {
        if (dag == null || dag.steps() == null || dag.steps().isEmpty()) {
            return dag;
        }
        if (!tools.containsKey(NL2SQL) || !tools.containsKey(LLM_ANSWER)) {
            return dag;
        }

        List<String> subQuestions = CompoundQuestionDecomposer.decompose(question);
        if (subQuestions.size() < 2) {
            return dag;
        }

        int maxDataSteps = Math.max(1, props.getMaxStepsPerWorkflow() - 1);
        if (subQuestions.size() > maxDataSteps) {
            subQuestions = subQuestions.subList(0, maxDataSteps);
        }

        long nl2sqlSteps = dag.steps().stream().filter(s -> NL2SQL.equals(s.tool())).count();
        if (nl2sqlSteps >= subQuestions.size() && !singleStepUsesFullQuestion(dag, question)) {
            return dag;
        }

        if (!isNl2SqlAnswerWorkflow(dag)) {
            log.debug("Compound question detected but workflow mixes other tools; skipping forced expand");
            return dag;
        }

        WorkflowDag expanded = buildParallelNl2SqlDag(subQuestions, dag);
        log.info("Expanded compound question into {} parallel NL2SQL step(s) + llm_answer (was {} NL2SQL)",
                subQuestions.size(), nl2sqlSteps);
        return expanded;
    }

    private static boolean isNl2SqlAnswerWorkflow(WorkflowDag dag) {
        return dag.steps().stream()
                .allMatch(s -> NL2SQL.equals(s.tool()) || LLM_ANSWER.equals(s.tool()));
    }

    private static boolean singleStepUsesFullQuestion(WorkflowDag dag, String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String norm = normalize(question);
        return dag.steps().stream()
                .filter(s -> NL2SQL.equals(s.tool()))
                .anyMatch(s -> {
                    Object q = s.params() == null ? null : s.params().get("question");
                    return q != null && normalize(q.toString()).equals(norm);
                });
    }

    private WorkflowDag buildParallelNl2SqlDag(List<String> subQuestions, WorkflowDag template) {
        WorkflowDag.WorkflowStepDef nl2sqlTemplate = template.steps().stream()
                .filter(s -> NL2SQL.equals(s.tool()))
                .findFirst()
                .orElse(null);
        WorkflowDag.WorkflowStepDef answerTemplate = template.steps().stream()
                .filter(s -> LLM_ANSWER.equals(s.tool()))
                .findFirst()
                .orElse(null);

        int maxTime = pickTime(nl2sqlTemplate, answerTemplate, true);
        int timeout = pickTime(nl2sqlTemplate, answerTemplate, false);
        Object maxRows = nl2sqlTemplate != null && nl2sqlTemplate.params() != null
                ? nl2sqlTemplate.params().get("maxRows")
                : null;

        List<WorkflowDag.WorkflowStepDef> steps = new ArrayList<>();
        List<String> dataStepIds = new ArrayList<>();

        for (int i = 0; i < subQuestions.size(); i++) {
            String id = "s" + (i + 1);
            dataStepIds.add(id);
            Map<String, Object> params = new HashMap<>();
            params.put("question", subQuestions.get(i));
            if (maxRows instanceof Number n) {
                params.put("maxRows", n.intValue());
            }
            steps.add(WorkflowDag.WorkflowStepDef.tool(
                    id, NL2SQL, List.of(), params, maxTime, timeout));
        }

        String answerId = "s" + (subQuestions.size() + 1);
        steps.add(WorkflowDag.WorkflowStepDef.tool(
                answerId, LLM_ANSWER, List.copyOf(dataStepIds), Map.of(), maxTime, timeout));

        return new WorkflowDag(steps);
    }

    private int pickTime(
            WorkflowDag.WorkflowStepDef nl2sql,
            WorkflowDag.WorkflowStepDef answer,
            boolean maxTime) {
        Integer fromNl2sql = maxTime
                ? nl2sql == null ? null : nl2sql.maxTimeMs()
                : nl2sql == null ? null : nl2sql.timeoutMs();
        if (fromNl2sql != null) {
            return fromNl2sql;
        }
        Integer fromAnswer = maxTime
                ? answer == null ? null : answer.maxTimeMs()
                : answer == null ? null : answer.timeoutMs();
        if (fromAnswer != null) {
            return fromAnswer;
        }
        return maxTime
                ? (int) props.getDefaultStepMaxTimeMs()
                : (int) props.getDefaultStepTimeoutMs();
    }

    private static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
