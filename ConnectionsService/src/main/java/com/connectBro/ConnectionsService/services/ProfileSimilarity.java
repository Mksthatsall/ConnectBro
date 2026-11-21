package com.connectBro.ConnectionsService.services;

import java.util.*;
import java.util.regex.Pattern;

/** A small content-based TF-IDF cosine model over the current candidate set. */
final class ProfileSimilarity {
    private static final Pattern WORDS = Pattern.compile("[\\p{Alnum}+#.]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "at", "be", "for", "from", "in", "into", "is", "of", "on", "or", "the", "to", "with",
            "looking", "seeking", "want", "wants", "work", "working", "professional", "experience", "team", "teams", "role", "roles");
    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("ml", List.of("machine", "learning")),
            Map.entry("ai", List.of("artificial", "intelligence")),
            Map.entry("ux", List.of("user", "experience")),
            Map.entry("ui", List.of("user", "interface")),
            Map.entry("llm", List.of("large", "language", "model")),
            Map.entry("genai", List.of("generative", "artificial", "intelligence")),
            Map.entry("js", List.of("javascript")),
            Map.entry("ts", List.of("typescript")),
            Map.entry("mentorship", List.of("mentor")),
            Map.entry("mentoring", List.of("mentor")),
            Map.entry("collaboration", List.of("collaborate")),
            Map.entry("collaborating", List.of("collaborate")));

    private ProfileSimilarity() { }

    static double cosine(String left, String right, Collection<String> corpus) {
        List<Set<String>> documents = corpus.stream().map(ProfileSimilarity::tokens).toList();
        Set<String> leftTerms = tokens(left);
        Set<String> rightTerms = tokens(right);
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) return 0;
        Set<String> vocabulary = new HashSet<>(leftTerms);
        vocabulary.addAll(rightTerms);
        Map<String, Double> leftVector = vector(leftTerms, documents, vocabulary);
        Map<String, Double> rightVector = vector(rightTerms, documents, vocabulary);
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (String term : vocabulary) {
            double a = leftVector.getOrDefault(term, 0.0);
            double b = rightVector.getOrDefault(term, 0.0);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return Math.max(0, Math.min(1, dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm))));
    }

    private static Map<String, Double> vector(Set<String> terms, List<Set<String>> documents, Set<String> vocabulary) {
        Map<String, Double> vector = new HashMap<>();
        int documentCount = Math.max(1, documents.size());
        for (String term : vocabulary) {
            if (!terms.contains(term)) continue;
            long frequency = documents.stream().filter(document -> document.contains(term)).count();
            double inverseDocumentFrequency = Math.log((documentCount + 1.0) / (frequency + 1.0)) + 1.0;
            vector.put(term, inverseDocumentFrequency);
        }
        return vector;
    }

    private static Set<String> tokens(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> result = new HashSet<>();
        var matcher = WORDS.matcher(lower);
        while (matcher.find()) {
            String word = matcher.group();
            if (STOP_WORDS.contains(word)) continue;
            List<String> expanded = ALIASES.getOrDefault(word, List.of(word));
            for (String term : expanded) if (!STOP_WORDS.contains(term)) result.add(term);
        }
        return result;
    }
}
