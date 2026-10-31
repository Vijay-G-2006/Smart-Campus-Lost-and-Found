package com.findit.api;

import com.findit.domain.ClaimStatus;
import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import com.findit.domain.Role;
import com.findit.repository.ClaimRepository;
import com.findit.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The claim workflow: submission, privacy, review and handover (PRD FR-07, FR-08).
 */
class ClaimApiTest extends AbstractIntegrationTest {

    @Autowired
    private ClaimRepository claimRepository;

    private static final String CLAIM_BODY = """
            {"explanation":"This is my umbrella, I lost it in the library and the handle has a scratch.",
             "verificationAnswers":"Blue fabric, wooden handle with a scratch."}
            """;

    @Test
    @DisplayName("a user claims a found item")
    void userCanClaimFoundItem() throws Exception {
        var owner = createUser("finder@findit.local", Role.USER);
        var claimant = createUser("claimant@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(claimant))
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLAIM_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.itemId").value(item.getId()))
                .andExpect(jsonPath("$.explanation").exists())
                .andExpect(jsonPath("$.verificationAnswers").exists());
    }

    @Test
    @DisplayName("a lost item cannot be claimed")
    void lostItemCannotBeClaimed() throws Exception {
        var owner = createUser("finder2@findit.local", Role.USER);
        var claimant = createUser("claimant2@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.LOST, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(claimant))
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLAIM_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("the owner of a found item cannot claim it")
    void ownerCannotClaimOwnItem() throws Exception {
        var owner = createUser("finder3@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(owner))
                        .with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLAIM_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a duplicate active claim is rejected")
    void duplicateClaimIsRejected() throws Exception {
        var owner = createUser("finder4@findit.local", Role.USER);
        var claimant = createUser("claimant4@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(claimant)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(claimant)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("the claim payload is validated")
    void claimIsValidated() throws Exception {
        var owner = createUser("finder5@findit.local", Role.USER);
        var claimant = createUser("claimant5@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(claimant)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"explanation":"too short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.explanation").exists());
    }

    @Test
    @DisplayName("a claim can only be read by the claimant and administrators")
    void claimPrivacyIsEnforced() throws Exception {
        var owner = createUser("finder6@findit.local", Role.USER);
        var claimant = createUser("claimant6@findit.local", Role.USER);
        var intruder = createUser("intruder6@findit.local", Role.USER);
        var admin = createUser("admin6@findit.local", Role.ADMIN);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        String body = mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(claimant)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long claimId = readId(body);

        mockMvc.perform(get("/api/claims/" + claimId).with(asUser(intruder)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/claims/" + claimId).with(asUser(claimant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").exists());

        mockMvc.perform(get("/api/claims/" + claimId).with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationAnswers").exists());
    }

    @Test
    @DisplayName("a normal user cannot reach the administrator endpoints")
    void normalUserIsDeniedAdminEndpoints() throws Exception {
        var user = createUser("normal@findit.local", Role.USER);

        mockMvc.perform(get("/api/admin/dashboard").with(asUser(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/claims").with(asUser(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit-logs").with(asUser(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/items/1/mark-returned").with(asUser(user)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an administrator approves a claim and the item becomes claimed")
    void adminApprovesClaim() throws Exception {
        var owner = createUser("finder7@findit.local", Role.USER);
        var claimant = createUser("claimant7@findit.local", Role.USER);
        var admin = createUser("admin7@findit.local", Role.ADMIN);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        String body = mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(claimant)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long claimId = readId(body);

        mockMvc.perform(post("/api/admin/claims/" + claimId + "/approve")
                        .with(asUser(admin)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Ownership verified at the lost property office."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedByDisplayName").value("admin7"));

        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.CLAIMED);
        assertThat(claimRepository.findById(claimId).orElseThrow().getStatus()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    @DisplayName("rejecting a claim requires a reason")
    void rejectRequiresReason() throws Exception {
        var owner = createUser("finder8@findit.local", Role.USER);
        var claimant = createUser("claimant8@findit.local", Role.USER);
        var admin = createUser("admin8@findit.local", Role.ADMIN);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        String body = mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(claimant)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andReturn().getResponse().getContentAsString();
        long claimId = readId(body);

        mockMvc.perform(post("/api/admin/claims/" + claimId + "/reject")
                        .with(asUser(admin)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"   "}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/claims/" + claimId + "/reject")
                        .with(asUser(admin)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"The description does not match."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("a returned item does not accept new claims")
    void returnedItemAcceptsNoClaims() throws Exception {
        var owner = createUser("finder9@findit.local", Role.USER);
        var claimant = createUser("claimant9@findit.local", Role.USER);
        var admin = createUser("admin9@findit.local", Role.ADMIN);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(post("/api/admin/items/" + item.getId() + "/mark-returned")
                        .with(asUser(admin)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Handed over without a claim after three weeks in the office."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));

        mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(claimant)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("marking an item as returned without an approved claim needs a documented exception")
    void markReturnedNeedsReasonWithoutApprovedClaim() throws Exception {
        var owner = createUser("finder10@findit.local", Role.USER);
        var admin = createUser("admin10@findit.local", Role.ADMIN);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        mockMvc.perform(post("/api/admin/items/" + item.getId() + "/mark-returned")
                        .with(asUser(admin)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/items/" + item.getId() + "/mark-returned")
                        .with(asUser(admin)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"The owner collected the item in person."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));
    }

    @Test
    @DisplayName("a claimant can withdraw a pending claim")
    void claimantCanWithdrawClaim() throws Exception {
        var owner = createUser("finder11@findit.local", Role.USER);
        var claimant = createUser("claimant11@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.FOUND, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");

        String body = mockMvc.perform(post("/api/items/" + item.getId() + "/claims")
                        .with(asUser(claimant)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(CLAIM_BODY))
                .andReturn().getResponse().getContentAsString();
        long claimId = readId(body);

        mockMvc.perform(post("/api/claims/" + claimId + "/withdraw")
                        .with(asUser(claimant)).with(csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    @DisplayName("the administrator dashboard shows the counters")
    void adminDashboardShowsCounts() throws Exception {
        var admin = createUser("admin12@findit.local", Role.ADMIN);
        var owner = createUser("finder12@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        createItem(ItemType.LOST, owner, category, location, "Black folding umbrella",
                "A black folding umbrella with a wooden handle left on a bench.");
        createItem(ItemType.FOUND, owner, category, location, "Blue backpack",
                "A blue backpack with a laptop sleeve left on a bench.");

        mockMvc.perform(get("/api/admin/dashboard").with(asUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.openLostReports").exists())
                .andExpect(jsonPath("$.counts.openFoundReports").exists())
                .andExpect(jsonPath("$.counts.pendingClaims").exists())
                .andExpect(jsonPath("$.counts.approvedClaims").exists())
                .andExpect(jsonPath("$.counts.returnedItems").exists())
                .andExpect(jsonPath("$.counts.totalUsers").exists())
                .andExpect(jsonPath("$.recentlyUpdatedReports").isArray())
                .andExpect(jsonPath("$.pendingClaims").isArray());
    }

    private long readId(String json) {
        String marker = "\"id\":";
        int index = json.indexOf(marker);
        if (index < 0) {
            throw new IllegalStateException("No id found in " + json);
        }
        int start = index + marker.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
