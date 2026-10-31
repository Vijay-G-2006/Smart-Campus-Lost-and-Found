package com.findit.api;

import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import com.findit.domain.Role;
import com.findit.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Item reports: creation, validation, ownership and search (PRD FR-03, FR-04).
 */
class ItemApiTest extends AbstractIntegrationTest {

    private String payload(String type, long categoryId, long locationId) {
        return """
                {"type":"%s","title":"Black folding umbrella","categoryId":%d,"locationId":%d,
                 "description":"A black folding umbrella with a wooden handle left on a bench.",
                 "eventDate":"2026-05-01","privateVerificationDetail":"The handle has a small scratch."}
                """.formatted(type, categoryId, locationId);
    }

    @Test
    @DisplayName("a signed in user creates a lost report")
    void createLostReport() throws Exception {
        var user = createUser("owner@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");

        mockMvc.perform(post("/api/items")
                        .with(asUser(user))
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("LOST", category.getId(), location.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LOST"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.owner").value(true))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.createdByDisplayName").value("owner"))
                // the private verification detail must never be part of the public response
                .andExpect(jsonPath("$.privateVerificationDetail").doesNotExist());
    }

    @Test
    @DisplayName("the report payload is validated")
    void createValidatesPayload() throws Exception {
        var user = createUser("validator@findit.local", Role.USER);

        mockMvc.perform(post("/api/items")
                        .with(asUser(user))
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"LOST","title":"ab","categoryId":null,"locationId":null,
                                 "description":"too short","eventDate":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists())
                .andExpect(jsonPath("$.fieldErrors.locationId").exists())
                .andExpect(jsonPath("$.fieldErrors.eventDate").exists());
    }

    @Test
    @DisplayName("an unknown category is rejected")
    void createRejectsUnknownCategory() throws Exception {
        var user = createUser("unknown@findit.local", Role.USER);
        var location = createLocation("Main Library");

        mockMvc.perform(post("/api/items")
                        .with(asUser(user))
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("LOST", 99999L, location.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a user can edit their own open report")
    void ownerCanEditOwnReport() throws Exception {
        var user = createUser("editor@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.LOST, user, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(put("/api/items/" + item.getId())
                        .with(asUser(user))
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Black folding umbrella (updated)","categoryId":%d,"locationId":%d,
                                 "description":"A black folding umbrella with a wooden handle on a bench.",
                                 "eventDate":"2026-05-02"}
                                """.formatted(category.getId(), location.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Black folding umbrella (updated)"));
    }

    @Test
    @DisplayName("a user cannot edit the report of somebody else")
    void userCannotEditForeignReport() throws Exception {
        var owner = createUser("owner2@findit.local", Role.USER);
        var intruder = createUser("intruder@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.LOST, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(put("/api/items/" + item.getId())
                        .with(asUser(intruder))
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked","categoryId":%d,"locationId":%d,
                                 "description":"Trying to change a report that belongs to somebody else.",
                                 "eventDate":"2026-05-02"}
                                """.formatted(category.getId(), location.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a closed report can no longer be edited")
    void closedReportCannotBeEdited() throws Exception {
        var user = createUser("closer@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.LOST, user, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");
        item.setStatus(ItemStatus.CLOSED);
        itemRepository.save(item);

        mockMvc.perform(put("/api/items/" + item.getId())
                        .with(asUser(user))
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Should not work","categoryId":%d,"locationId":%d,
                                 "description":"A black folding umbrella with a wooden handle on a bench.",
                                 "eventDate":"2026-05-02"}
                                """.formatted(category.getId(), location.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only the owner and administrators may read the private details")
    void privateDetailsAreProtected() throws Exception {
        var owner = createUser("privateowner@findit.local", Role.USER);
        var intruder = createUser("privateintruder@findit.local", Role.USER);
        var admin = createUser("privateadmin@findit.local", Role.ADMIN);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(get("/api/items/" + item.getId() + "/private-details").with(asUser(intruder)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/items/" + item.getId() + "/private-details").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privateVerificationDetail").value(
                        "A private detail that must not leak: Black folding umbrella"))
                .andExpect(jsonPath("$.contactChannel").exists());

        mockMvc.perform(get("/api/items/" + item.getId() + "/private-details").with(asUser(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the search filters by type, category and keyword")
    void searchFiltersResults() throws Exception {
        var user = createUser("searcher@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var otherCategory = createCategory("Keys");
        var location = createLocation("Main Library");
        var unique = "searchfilter" + System.nanoTime();
        createItem(ItemType.LOST, user, category, location, "Umbrella " + unique,
                "A black folding umbrella " + unique + " with a wooden handle left on a bench.");
        createItem(ItemType.FOUND, user, otherCategory, location, "Car keys " + unique,
                "A set of car keys " + unique + " with a red plastic fob.");

        mockMvc.perform(get("/api/items").with(asUser(user)).param("type", "LOST").param("q", unique))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("LOST"));

        mockMvc.perform(get("/api/items").with(asUser(user)).param("q", unique)
                        .param("categoryId", otherCategory.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].categoryName").value("Keys"));
    }

    @Test
    @DisplayName("the search is paginated")
    void searchIsPaginated() throws Exception {
        var user = createUser("pager@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var unique = "pagination" + System.nanoTime();
        for (int index = 0; index < 5; index++) {
            createItem(ItemType.LOST, user, category, location, "Umbrella " + unique + " number " + index,
                    "A black folding umbrella " + unique + " number " + index + " left on a bench.");
        }

        mockMvc.perform(get("/api/items").with(asUser(user)).param("q", unique)
                        .param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    @DisplayName("the owner closes their own report")
    void ownerCanCloseReport() throws Exception {
        var user = createUser("closer2@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.LOST, user, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(post("/api/items/" + item.getId() + "/close").with(asUser(user)).with(csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("the match suggestions of a report are returned with a score breakdown")
    void matchesAreScored() throws Exception {
        var owner = createUser("matchowner@findit.local", Role.USER);
        var finder = createUser("matchfinder@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var unique = "match" + System.nanoTime();
        var lost = createItem(ItemType.LOST, owner, category, location, "Black folding umbrella " + unique,
                "A black folding umbrella " + unique + " with a wooden handle left on a bench.");
        var found = createItem(ItemType.FOUND, finder, category, location, "Black folding umbrella " + unique,
                "A black folding umbrella " + unique + " with a wooden handle left on a bench.");

        String body = mockMvc.perform(get("/api/items/" + lost.getId() + "/matches").with(asUser(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String needle = "\"itemId\":" + found.getId();
        assertThat(body).contains(needle);
        assertThat(body).contains("\"score\":100");
        assertThat(body).contains("\"band\":\"HIGH\"");
        assertThat(body).contains("\"categoryPoints\":30");
        assertThat(body).contains("not proof of ownership");
    }

    @Test
    @DisplayName("a user does not get their own reports suggested")
    void ownReportsAreNotSuggested() throws Exception {
        var owner = createUser("selfmatch@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var unique = "selfmatch" + System.nanoTime();
        var lost = createItem(ItemType.LOST, owner, category, location, "Black folding umbrella " + unique,
                "A black folding umbrella " + unique + " with a wooden handle left on a bench.");
        var ownFound = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella " + unique,
                "A black folding umbrella " + unique + " with a wooden handle left on a bench.");

        String body = mockMvc.perform(get("/api/items/" + lost.getId() + "/matches").with(asUser(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("\"itemId\":" + ownFound.getId());
    }
}
