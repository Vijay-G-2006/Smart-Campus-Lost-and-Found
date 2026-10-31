package com.findit.api;

import com.findit.domain.ItemType;
import com.findit.domain.Role;
import com.findit.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Image handling (PRD FR-05): only real images of an accepted type are stored
 * and they are only served through the authorised endpoint.
 */
class ImageUploadApiTest extends AbstractIntegrationTest {

    /** Smallest valid 1x1 PNG file. */
    private static final byte[] TINY_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0, 0x00,
            0x00, 0x00, 0x02, 0x00, 0x01, (byte) 0xE2, (byte) 0x21, (byte) 0xBC, 0x33,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @Test
    @DisplayName("the owner can upload a valid image")
    void ownerCanUploadImage() throws Exception {
        var user = createUser("uploader@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.LOST, user, category, location, "Umbrella with photo",
                "A black folding umbrella with a wooden handle left on a bench.");

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, TINY_PNG);

        mockMvc.perform(multipart("/api/items/" + item.getId() + "/image")
                        .file(file)
                        .with(asUser(user))
                        .with(csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasImage").value(true))
                .andExpect(jsonPath("$.imageUrl").value("/api/items/" + item.getId() + "/image"));
    }

    @Test
    @DisplayName("a file that is not an accepted image type is rejected")
    void unsupportedContentTypeIsRejected() throws Exception {
        var user = createUser("uploader2@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.LOST, user, category, location, "Umbrella with bad type",
                "A black folding umbrella with a wooden handle left on a bench.");

        MockMultipartFile file = new MockMultipartFile("file", "payload.svg", MediaType.TEXT_PLAIN_VALUE,
                "<svg onload=\"alert(1)\"></svg>".getBytes());

        mockMvc.perform(multipart("/api/items/" + item.getId() + "/image")
                        .file(file)
                        .with(asUser(user))
                        .with(csrfToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a file that pretends to be an image is rejected")
    void forgedImageIsRejected() throws Exception {
        var user = createUser("uploader3@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.LOST, user, category, location, "Umbrella with forged image",
                "A black folding umbrella with a wooden handle left on a bench.");

        MockMultipartFile file = new MockMultipartFile("file", "payload.png", MediaType.IMAGE_PNG_VALUE,
                "#!/bin/sh\necho not an image".getBytes());

        mockMvc.perform(multipart("/api/items/" + item.getId() + "/image")
                        .file(file)
                        .with(asUser(user))
                        .with(csrfToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a user cannot upload an image to the report of somebody else")
    void onlyTheOwnerCanUploadAnImage() throws Exception {
        var owner = createUser("uploader4@findit.local", Role.USER);
        var intruder = createUser("intruder4@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.LOST, owner, category, location, "Umbrella with photo",
                "A black folding umbrella with a wooden handle left on a bench.");

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, TINY_PNG);

        mockMvc.perform(multipart("/api/items/" + item.getId() + "/image")
                        .file(file)
                        .with(asUser(intruder))
                        .with(csrfToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a stored image is served to signed in users and is not a public static file")
    void imageIsServedThroughTheApi() throws Exception {
        var user = createUser("viewer@findit.local", Role.USER);
        var category = createCategory("Electronics");
        var location = createLocation("Main Library");
        var item = createItem(ItemType.LOST, user, category, location, "Umbrella with photo",
                "A black folding umbrella with a wooden handle left on a bench.");

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, TINY_PNG);
        mockMvc.perform(multipart("/api/items/" + item.getId() + "/image")
                .file(file).with(asUser(user)).with(csrfToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/items/" + item.getId() + "/image").with(asUser(user)))
                .andExpect(status().isOk());

        // without a session the image endpoint is not reachable
        mockMvc.perform(get("/api/items/" + item.getId() + "/image"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the front-end is delivered by the application")
    void frontendIsServed() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/js/api.js"))
                .andExpect(status().isOk());
    }
}
