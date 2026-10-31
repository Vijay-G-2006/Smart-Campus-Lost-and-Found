package com.findit.service;

import com.findit.domain.Item;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, explainable match scoring (PRD FR-06).
 *
 * <p>The score is a <em>suggestion</em> only. It never proves ownership and it
 * never approves a claim.</p>
 *
 * <p>Scoring rules:</p>
 * <ul>
 *   <li>Category similarity: 30 points for the same category, otherwise 0.</li>
 *   <li>Description / keyword similarity: up to 30 points -
 *       30 &times; Jaccard similarity of the normalised keyword sets of
 *       "title + description".</li>
 *   <li>Location similarity: up to 20 points - 20 for the identical location,
 *       otherwise up to 10 points for a partial name overlap
 *       (10 &times; Jaccard similarity of the location name keywords).</li>
 *   <li>Date proximity: up to 20 points - 20 for the same day, minus 2 points
 *       per day of distance, never below 0.</li>
 *   <li>The total is capped at 100.</li>
 * </ul>
 *
 * <p>Bands: {@code HIGH} from 70, {@code MEDIUM} from 40, otherwise {@code LOW}.</p>
 */
@Service
public class MatchScoringService {

    public static final int MAX_CATEGORY_POINTS = 30;
    public static final int MAX_DESCRIPTION_POINTS = 30;
    public static final int MAX_LOCATION_POINTS = 20;
    public static final int MAX_DATE_POINTS = 20;
    public static final int MAX_TOTAL = 100;

    private static final int DATE_POINTS_PER_DAY = 2;
    private static final int PARTIAL_LOCATION_POINTS = 10;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "of", "at", "by", "for", "with", "about",
            "into", "to", "from", "in", "on", "is", "are", "was", "were", "be", "been", "being",
            "it", "its", "this", "that", "these", "those", "my", "me", "i", "we", "us", "our",
            "your", "you", "they", "them", "their", "he", "she", "his", "her", "has", "have", "had",
            "do", "does", "did", "no", "not", "so", "as", "than", "then", "there", "here", "when",
            "where", "which", "who", "whom", "what", "how", "some", "any", "all", "one", "two",
            "up", "out", "very", "just", "also", "can", "will", "would", "should", "could", "may",
            "might", "must", "shall", "am", "left", "right", "near", "next", "onto", "over");

    /**
     * Scores two opposite reports (one lost, one found).
     */
    public MatchResult score(Item lost, Item found) {
        int categoryPoints = scoreCategory(lost, found);
        int descriptionPoints = scoreDescription(lost, found);
        int locationPoints = scoreLocation(lost, found);
        int datePoints = scoreDate(lost, found);

        int total = Math.min(MAX_TOTAL, categoryPoints + descriptionPoints + locationPoints + datePoints);
        return new MatchResult(categoryPoints, descriptionPoints, locationPoints, datePoints, total,
                bandOf(total), explain(categoryPoints, descriptionPoints, locationPoints, datePoints));
    }

    private int scoreCategory(Item lost, Item found) {
        if (lost.getCategory() == null || found.getCategory() == null) {
            return 0;
        }
        return lost.getCategory().getId().equals(found.getCategory().getId()) ? MAX_CATEGORY_POINTS : 0;
    }

    private int scoreDescription(Item lost, Item found) {
        Set<String> left = keywordsOf(lost);
        Set<String> right = keywordsOf(found);
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        double similarity = jaccard(left, right);
        return (int) Math.round(MAX_DESCRIPTION_POINTS * similarity);
    }

    private int scoreLocation(Item lost, Item found) {
        if (lost.getLocation() == null || found.getLocation() == null) {
            return 0;
        }
        if (lost.getLocation().getId().equals(found.getLocation().getId())) {
            return MAX_LOCATION_POINTS;
        }
        Set<String> left = keywords(lost.getLocation().getName());
        Set<String> right = keywords(found.getLocation().getName());
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        double similarity = jaccard(left, right);
        return Math.min(PARTIAL_LOCATION_POINTS, (int) Math.round(PARTIAL_LOCATION_POINTS * similarity));
    }

    private int scoreDate(Item lost, Item found) {
        LocalDate left = lost.getEventDate();
        LocalDate right = found.getEventDate();
        if (left == null || right == null) {
            return 0;
        }
        long days = Math.abs(ChronoUnit.DAYS.between(left, right));
        return (int) Math.max(0, MAX_DATE_POINTS - (days * DATE_POINTS_PER_DAY));
    }

    /**
     * Normalised keyword set of "title + description".
     */
    public Set<String> keywordsOf(Item item) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.addAll(keywords(item.getTitle()));
        keywords.addAll(keywords(item.getDescription()));
        return keywords;
    }

    /**
     * Lower cases, removes punctuation and stop words and reduces simple
     * plurals. The result is stable and independent of the input order.
     */
    public Set<String> keywords(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String normalised = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        for (String token : normalised.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            String reduced = reducePlural(token);
            if (reduced.length() < 3 || STOP_WORDS.contains(reduced)) {
                continue;
            }
            result.add(reduced);
        }
        return result;
    }

    private String reducePlural(String token) {
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss") && !token.endsWith("us")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        if (union.isEmpty()) {
            return 0d;
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return (double) intersection.size() / union.size();
    }

    public static String bandOf(int total) {
        if (total >= 70) {
            return "HIGH";
        }
        if (total >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String explain(int categoryPoints, int descriptionPoints, int locationPoints, int datePoints) {
        List<String> parts = new java.util.ArrayList<>();
        parts.add(categoryPoints > 0
                ? "same category (+" + categoryPoints + ")"
                : "different category (+0)");
        parts.add("keyword overlap (+" + descriptionPoints + ")");
        parts.add(locationPoints >= MAX_LOCATION_POINTS
                ? "same location (+" + locationPoints + ")"
                : "location overlap (+" + locationPoints + ")");
        parts.add("date proximity (+" + datePoints + ")");
        return String.join(", ", parts);
    }

    /**
     * Result of a single scoring run.
     */
    public record MatchResult(
            int categoryPoints,
            int descriptionPoints,
            int locationPoints,
            int datePoints,
            int total,
            String band,
            String explanation
    ) {
    }
}
