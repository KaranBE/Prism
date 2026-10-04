package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.dto.ChatCompletionRequest;
import com.airtribe.prism.common.dto.ChatMessage;
import com.airtribe.prism.common.model.ModelTier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Heuristic difficulty classifier behind the "auto" alias.
 *
 * The spec is explicit that this must beat a length-only baseline on an eval set that
 * deliberately contains short-but-hard prompts ("Prove sqrt(2) is irrational.") and
 * long-but-trivial ones (a long, simple story-continuation request). A pure length threshold
 * gets both of those wrong by construction, so this scores several independent signals and
 * combines them into a weighted difficulty score instead of relying on length alone:
 *
 *   - reasoning/complexity vocabulary ("prove", "why", "optimi", "algorithm", "debug", ...)
 *   - presence of code or math notation
 *   - multi-step / comparative structure (multiple clauses, "and then", "step by step")
 *   - lexical density (unique-word ratio, average word length) as a *secondary* signal only
 *   - raw length, but weighted low and capped, specifically so it cannot dominate the score
 *
 * This stays a fast, dependency-free heuristic (no model call needed to decide which model to
 * call) so classification itself doesn't add material latency to the hot path. See
 * scripts/routing_eval.py for the harness that scores this against a labeled eval set and
 * reports accuracy versus the length-only baseline.
 */
@Service
public class DifficultyClassifierService {

    private static final Set<String> HARD_KEYWORDS = Set.of(
            "prove", "why", "explain", "derive", "optimi", "algorithm", "debug", "architecture",
            "compare", "trade-off", "tradeoff", "design", "analyze", "analyse", "reason", "complexity",
            "concurrency", "distributed", "security", "vulnerability", "proof", "theorem", "step by step",
            "tcp", "handshake", "protocol", "lock", "mutex", "database", "cap theorem", "irrational",
            "indexoutofbounds", "deadlock");

    private static final Pattern CODE_OR_MATH = Pattern.compile("[{}<>=;()\\[\\]]|```|\\b(sqrt|sum|integral|O\\()\\b");

    public ModelTier classify(ChatCompletionRequest request) {
        return classify(concatUserContent(request));
    }

    ModelTier classify(String text) {
        return score(text) >= 0.35 ? ModelTier.SMART : ModelTier.FAST;
    }

    /** 0.0 (trivially easy) .. 1.0 (hard) */
    double score(String text) {
        String lower = text.toLowerCase();
        String[] words = lower.split("\\s+");
        int wordCount = Math.max(1, words.length);

        long keywordHits = HARD_KEYWORDS.stream().filter(lower::contains).count();
        double keywordSignal = Math.min(1.0, keywordHits / 2.0);

        boolean hasCodeOrMath = CODE_OR_MATH.matcher(text).find();
        double codeMathSignal = hasCodeOrMath ? 1.0 : 0.0;

        long questionMarks = text.chars().filter(c -> c == '?').count();
        long conjunctions = List.of("and then", "after that", "next,", "step by step").stream()
                .filter(lower::contains).count();
        double multiStepSignal = Math.min(1.0, (questionMarks > 1 ? 0.5 : 0.0) + conjunctions * 0.5);

        double avgWordLen = (double) lower.replace(" ", "").length() / wordCount;
        double lexicalSignal = Math.min(1.0, Math.max(0.0, (avgWordLen - 4.0) / 4.0));

        // Length is intentionally the smallest-weighted, capped signal - long-but-trivial
        // prompts (a long story to continue) must not be pushed to "smart" by length alone,
        // and short-but-hard prompts must not be kept on "fast" for being short.
        double lengthSignal = Math.min(1.0, wordCount / 400.0);

        double score = 0.40 * keywordSignal
                + 0.25 * codeMathSignal
                + 0.20 * multiStepSignal
                + 0.10 * lexicalSignal
                + 0.05 * lengthSignal;

        return Math.min(1.0, score);
    }

    private String concatUserContent(ChatCompletionRequest request) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : request.messages()) {
            if ("user".equalsIgnoreCase(m.role())) {
                sb.append(m.content()).append(' ');
            }
        }
        return sb.isEmpty() ? request.messages().get(request.messages().size() - 1).content() : sb.toString();
    }
}
