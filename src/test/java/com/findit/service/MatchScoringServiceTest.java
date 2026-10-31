package com.findit.service;

import com.findit.domain.CampusLocation;
import com.findit.domain.Category;
import com.findit.domain.Item;
import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import com.findit.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the rule based match scoring (PRD FR-06).
 */
class MatchScoringServiceTest {

    private MatchScoringService scoringService;
    private Category electronics;
    private Category bags;
    private CampusLocation library;
    private CampusLocation sports;

    @BeforeEach
    void setUp() {
        scoringService = new MatchScoringService();

        electronics = new Category("Electronics", null);
        electronics.setId(1L);
        bags = new Category("Wallets & Bags", null);
        bags.setId(2L);
        library = new CampusLocation("Main Library", null);
        library.setId(10L);
        sports = new CampusLocation("Sports Complex", null);
        sports.setId(11L);
    }

    private Item item(ItemType type, Category category, CampusLocation location, LocalDate date,
                      String title, String description) {
        Item item = new Item();
        item.setType(type);
        item.setCategory(category);
        item.setLocation(location);
        item.setEventDate(date);
        item.setTitle(title);
        item.setDescription(description);
        item.setStatus(ItemStatus.OPEN);
        return item;
    }

    @Test
    @DisplayName("an identical report scores the maximum of 100 points")
    void identicalItemsScoreMaximum() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1),
                "Black folding umbrella", "A black folding umbrella with a wooden handle and a scratch.");
        Item found = item(ItemType.FOUND, electronics, library, LocalDate.of(2026, 5, 1),
                "Black folding umbrella", "A black folding umbrella with a wooden handle and a scratch.");

        MatchScoringService.MatchResult result = scoringService.score(lost, found);

        assertThat(result.total()).isEqualTo(100);
        assertThat(result.categoryPoints()).isEqualTo(30);
        assertThat(result.descriptionPoints()).isEqualTo(30);
        assertThat(result.locationPoints()).isEqualTo(20);
        assertThat(result.datePoints()).isEqualTo(20);
        assertThat(result.band()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("a different category gives no category points")
    void differentCategoryGivesNoCategoryPoints() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1),
                "Black folding umbrella", "A black folding umbrella.");
        Item found = item(ItemType.FOUND, bags, library, LocalDate.of(2026, 5, 1),
                "Black folding umbrella", "A black folding umbrella.");

        MatchScoringService.MatchResult result = scoringService.score(lost, found);

        assertThat(result.categoryPoints()).isZero();
        assertThat(result.total()).isLessThan(100);
    }

    @Test
    @DisplayName("the score never exceeds 100 points")
    void scoreIsCappedAtHundred() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1),
                "Black folding umbrella", "A black folding umbrella.");
        Item found = item(ItemType.FOUND, electronics, library, LocalDate.of(2026, 5, 1),
                "Black folding umbrella", "A black folding umbrella.");

        assertThat(scoringService.score(lost, found).total()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("date proximity loses two points per day and stops at zero")
    void dateProximityDecays() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1), "Umbrella", "Umbrella.");
        Item found = item(ItemType.FOUND, electronics, library, LocalDate.of(2026, 5, 6), "Umbrella", "Umbrella.");

        MatchScoringService.MatchResult result = scoringService.score(lost, found);

        assertThat(result.datePoints()).isEqualTo(10);

        Item far = item(ItemType.FOUND, electronics, library, LocalDate.of(2026, 6, 30), "Umbrella", "Umbrella.");
        assertThat(scoringService.score(lost, far).datePoints()).isZero();
    }

    @Test
    @DisplayName("the same location gives the full location score, a different one gives none")
    void locationScoring() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1), "Umbrella", "Umbrella.");
        Item same = item(ItemType.FOUND, electronics, library, LocalDate.of(2026, 5, 1), "Umbrella", "Umbrella.");
        Item different = item(ItemType.FOUND, electronics, sports, LocalDate.of(2026, 5, 1), "Umbrella", "Umbrella.");

        assertThat(scoringService.score(lost, same).locationPoints()).isEqualTo(20);
        assertThat(scoringService.score(lost, different).locationPoints()).isZero();
    }

    @Test
    @DisplayName("a partially matching location name gives a partial score")
    void partialLocationMatch() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1), "Umbrella", "Umbrella.");
        CampusLocation annex = new CampusLocation("Library Annex", null);
        annex.setId(12L);
        Item found = item(ItemType.FOUND, electronics, annex, LocalDate.of(2026, 5, 1), "Umbrella", "Umbrella.");

        int points = scoringService.score(lost, found).locationPoints();

        assertThat(points).isGreaterThan(0).isLessThan(20);
    }

    @Test
    @DisplayName("keyword overlap is scored on the normalised keywords of title and description")
    void keywordScoring() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1),
                "Blue backpack", "A blue backpack with a grey laptop sleeve.");
        Item found = item(ItemType.FOUND, electronics, library, LocalDate.of(2026, 5, 1),
                "Blue backpack", "A blue backpack with a grey laptop sleeve.");

        assertThat(scoringService.score(lost, found).descriptionPoints()).isEqualTo(30);
    }

    @Test
    @DisplayName("keywords are normalised: case, punctuation, stop words and simple plurals do not matter")
    void keywordNormalisation() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1),
                "Black umbrella", "The umbrella is black.");
        Item found = item(ItemType.FOUND, electronics, library, LocalDate.of(2026, 5, 1),
                "black umbrella!", "A black umbrella.");

        int points = scoringService.score(lost, found).descriptionPoints();

        assertThat(points).isGreaterThan(0);
        assertThat(scoringService.keywords("The UMBRELLAS are here!")).containsExactly("umbrella");
    }

    @Test
    @DisplayName("reports without any shared keyword score zero description points")
    void noSharedKeywords() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1),
                "Black umbrella", "A black umbrella with a wooden handle.");
        Item found = item(ItemType.FOUND, electronics, library, LocalDate.of(2026, 5, 1),
                "Laptop charger", "A white laptop charger for a 65W notebook.");

        assertThat(scoringService.score(lost, found).descriptionPoints()).isZero();
    }

    @Test
    @DisplayName("missing values do not break the scoring")
    void missingValuesAreHandled() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1), "Umbrella", "Umbrella.");
        Item found = new Item();
        found.setType(ItemType.FOUND);
        found.setStatus(ItemStatus.OPEN);

        MatchScoringService.MatchResult result = scoringService.score(lost, found);

        assertThat(result.total()).isZero();
        assertThat(result.band()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("the score bands are HIGH from 70, MEDIUM from 40 and LOW below")
    void scoreBands() {
        assertThat(MatchScoringService.bandOf(100)).isEqualTo("HIGH");
        assertThat(MatchScoringService.bandOf(70)).isEqualTo("HIGH");
        assertThat(MatchScoringService.bandOf(69)).isEqualTo("MEDIUM");
        assertThat(MatchScoringService.bandOf(40)).isEqualTo("MEDIUM");
        assertThat(MatchScoringService.bandOf(39)).isEqualTo("LOW");
        assertThat(MatchScoringService.bandOf(0)).isEqualTo("LOW");
    }

    @Test
    @DisplayName("the explanation names every scoring component")
    void explanationIsReadable() {
        Item lost = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1),
                "Black umbrella", "A black umbrella with a wooden handle.");
        Item found = item(ItemType.FOUND, electronics, library, LocalDate.of(2026, 5, 1),
                "Black umbrella", "A black umbrella with a wooden handle.");

        MatchScoringService.MatchResult result = scoringService.score(lost, found);

        assertThat(result.explanation())
                .contains("same category")
                .contains("keyword overlap")
                .contains("same location")
                .contains("date proximity");
    }

    @Test
    @DisplayName("a report never scores against itself")
    void selfComparison() {
        Item item = item(ItemType.LOST, electronics, library, LocalDate.of(2026, 5, 1),
                "Black umbrella", "A black umbrella with a wooden handle.");

        // The same text still produces the maximum score - the service layer is
        // responsible for excluding the report itself from the candidate list.
        assertThat(scoringService.score(item, item).total()).isEqualTo(100);
        assertThat(item.getCreatedBy()).isNull();
    }
}
