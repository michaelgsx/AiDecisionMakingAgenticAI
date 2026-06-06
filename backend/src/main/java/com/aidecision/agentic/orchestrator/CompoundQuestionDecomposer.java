package com.aidecision.agentic.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based detection of compound analytics questions and split into standalone sub-questions.
 * No domain-specific hardcoding — uses conjunctions, punctuation, and intent-leading phrases.
 */
final class CompoundQuestionDecomposer {

    private static final Pattern NUMBERED_ITEM =
            Pattern.compile("(?m)\\s*\\d+[.)]\\s+");

    private static final Pattern NEW_INTENT_LEAD = Pattern.compile(
            "(?i)(how many|how much|what is|what are|what were|list|show|compare|break\\s*down|"
                    + "group\\s*by|count|total|summarize|give me|tell me|find|get)\\b");

    private static final Pattern PRONOUN = Pattern.compile("(?i)\\b(them|those|these|it)\\b");

    private static final Pattern COUNT_ENTITY = Pattern.compile(
            "(?i)how many\\s+(distinct\\s+)?(.+?)\\s+(do we have|are there|exist|in total|in the)");

    private static final Pattern CONJUNCTION_SPLIT = Pattern.compile(
            "(?i)[?.!]\\s+(?:and|also|plus|as well as)\\s+");

    private static final Pattern COMMA_INTENT_SPLIT = Pattern.compile(
            "(?i),\\s*(?:and\\s+)?(?=how many|how much|list|show|compare|what|break|group|count|total|summarize)");

    private CompoundQuestionDecomposer() {}

    static boolean isCompound(String question) {
        return decompose(question).size() >= 2;
    }

    static List<String> decompose(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String normalized = question.trim().replaceAll("\\s+", " ");

        List<String> parts = trySplit(normalized, ";");
        if (parts.size() < 2) {
            parts = tryNumberedSplit(normalized);
        }
        if (parts.size() < 2) {
            parts = tryConjunctionSplit(normalized);
        }
        if (parts.size() < 2) {
            parts = tryCommaIntentSplit(normalized);
        }
        if (parts.size() < 2) {
            return List.of();
        }

        return finalizeParts(parts);
    }

    private static List<String> trySplit(String text, String delimiter) {
        if (!text.contains(delimiter)) {
            return List.of();
        }
        return filterBlank(text.split(Pattern.quote(delimiter)));
    }

    private static List<String> tryNumberedSplit(String text) {
        if (!NUMBERED_ITEM.matcher(text).find()) {
            return List.of();
        }
        String[] raw = NUMBERED_ITEM.split(text);
        List<String> parts = new ArrayList<>();
        for (String part : raw) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return parts.size() >= 2 ? parts : List.of();
    }

    private static List<String> tryConjunctionSplit(String text) {
        Matcher m = CONJUNCTION_SPLIT.matcher(text);
        if (!m.find()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        int last = 0;
        m.reset();
        while (m.find()) {
            parts.add(text.substring(last, m.start()).trim());
            last = m.end();
        }
        parts.add(text.substring(last).trim());
        return filterBlankParts(parts);
    }

    private static List<String> tryCommaIntentSplit(String text) {
        if (!text.contains(",")) {
            return List.of();
        }
        String[] raw = COMMA_INTENT_SPLIT.split(text);
        List<String> parts = filterBlank(raw);
        if (parts.size() < 2) {
            return List.of();
        }
        boolean multipleIntents = parts.stream()
                .filter(p -> !p.isBlank())
                .filter(CompoundQuestionDecomposer::startsWithIntent)
                .count() >= 2;
        return multipleIntents ? parts : List.of();
    }

    private static boolean startsWithIntent(String part) {
        String trimmed = part.stripLeading();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("and ")) {
            trimmed = trimmed.substring(4).stripLeading();
        }
        return NEW_INTENT_LEAD.matcher(trimmed).find();
    }

    private static List<String> finalizeParts(List<String> rawParts) {
        List<String> out = new ArrayList<>();
        String prior = null;
        for (String raw : rawParts) {
            String part = raw.trim();
            if (part.isBlank()) {
                continue;
            }
            if (part.toLowerCase(Locale.ROOT).startsWith("and ")) {
                part = part.substring(4).trim();
            }
            if (prior != null && PRONOUN.matcher(part).find()) {
                part = rewritePronounClause(part, prior);
            }
            part = ensureQuestion(part);
            out.add(part);
            prior = part;
        }
        return out.size() >= 2 ? out : List.of();
    }

    private static String rewritePronounClause(String clause, String priorClause) {
        if (Pattern.compile("(?i)^(list|show)\\b").matcher(clause).find()) {
            String entity = extractCountableEntity(priorClause);
            if (entity != null) {
                return "List " + entity + ".";
            }
        }
        return clause;
    }

    private static String extractCountableEntity(String priorClause) {
        Matcher m = COUNT_ENTITY.matcher(priorClause);
        if (m.find()) {
            String distinct = m.group(1) != null ? "distinct " : "";
            return distinct + m.group(2).trim();
        }
        return null;
    }

    private static String ensureQuestion(String part) {
        if (part.endsWith("?") || part.endsWith(".")) {
            return part;
        }
        return part + "?";
    }

    private static List<String> filterBlank(String[] raw) {
        List<String> parts = new ArrayList<>();
        for (String part : raw) {
            if (part != null && !part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    private static List<String> filterBlankParts(List<String> raw) {
        List<String> parts = new ArrayList<>();
        for (String part : raw) {
            if (part != null && !part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }
}
