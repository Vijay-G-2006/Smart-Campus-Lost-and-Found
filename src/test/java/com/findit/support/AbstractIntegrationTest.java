package com.findit.support;

import com.findit.domain.CampusLocation;
import com.findit.domain.Category;
import com.findit.domain.Item;
import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import com.findit.domain.Role;
import com.findit.domain.User;
import com.findit.repository.CampusLocationRepository;
import com.findit.repository.CategoryRepository;
import com.findit.repository.ItemRepository;
import com.findit.repository.UserRepository;
import com.findit.security.AppUserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Shared setup for the integration tests: an in-memory database, no seed data
 * and a local image directory inside the build folder.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:findit-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.seed.enabled=false",
        "app.storage.type=local",
        "app.storage.local-dir=build/test-images",
        "app.security.max-login-attempts=100"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected CampusLocationRepository locationRepository;

    @Autowired
    protected ItemRepository itemRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected User createUser(String email, Role role) {
        User user = new User(email, email.substring(0, email.indexOf('@')), passwordEncoder.encode("Str0ngPass123"), role);
        user.setActive(true);
        user.setContactChannel(email.replace("@", " at ") + " (demo contact channel)");
        return userRepository.save(user);
    }

    protected AppUserPrincipal principalOf(User user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getDisplayName(), "hash",
                user.getRole(), true);
    }

    protected Category createCategory(String name) {
        return categoryRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> categoryRepository.save(new Category(name, null)));
    }

    protected CampusLocation createLocation(String name) {
        return locationRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> locationRepository.save(new CampusLocation(name, null)));
    }

    protected Item createItem(ItemType type, User owner, Category category, CampusLocation location,
                              String title, String description) {
        Item item = new Item();
        item.setType(type);
        item.setTitle(title);
        item.setDescription(description);
        item.setCategory(category);
        item.setLocation(location);
        item.setEventDate(LocalDate.now().minusDays(1));
        item.setStatus(ItemStatus.OPEN);
        item.setCreatedBy(owner);
        item.setPrivateVerificationDetail("A private detail that must not leak: " + title);
        return itemRepository.save(item);
    }

    protected RequestPostProcessor asUser(User user) {
        return SecurityMockMvcRequestPostProcessors.user(principalOf(user));
    }

    protected RequestPostProcessor asAdmin(User admin) {
        return SecurityMockMvcRequestPostProcessors.user(principalOf(admin));
    }

    protected SecurityMockMvcRequestPostProcessors.CsrfRequestPostProcessor csrfToken() {
        return csrf();
    }

    protected void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(context);
    }
}
