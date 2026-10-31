package com.findit.config;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Local development seed data.
 *
 * <p>Enabled with {@code app.seed.enabled=true} (local profile only).</p>
 *
 * <ul>
 *   <li>The administrator account is only created when
 *       {@code app.seed.admin-password} is provided - there is deliberately no
 *       default password, and no production password is ever hard coded.</li>
 *   <li>The demo students use the synthetic password from
 *       {@code app.seed.demo-user-password} and only exist in the local
 *       development database.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final CampusLocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AppProperties appProperties, UserRepository userRepository, ItemRepository itemRepository,
                       CategoryRepository categoryRepository, CampusLocationRepository locationRepository,
                       PasswordEncoder passwordEncoder) {
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        if (appProperties.getSeed().isDemoData()) {
            seedDemoData();
        }
    }

    private void seedAdmin() {
        String email = appProperties.getSeed().getAdminEmail();
        String password = appProperties.getSeed().getAdminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("No administrator seed configured - set ADMIN_SEED_EMAIL and ADMIN_SEED_PASSWORD to create one.");
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(email.trim())) {
            log.info("Administrator {} already exists - nothing to do", email.trim());
            return;
        }
        User admin = new User(email.trim().toLowerCase(), appProperties.getSeed().getAdminDisplayName(),
                passwordEncoder.encode(password), Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);
        log.info("Created administrator account {}", admin.getEmail());
    }

    private void seedDemoData() {
        String password = appProperties.getSeed().getDemoUserPassword();
        if (password == null || password.isBlank()) {
            log.info("No demo password configured - skipping demo data.");
            return;
        }
        User alice = ensureDemoUser("demo.alice@findit.local", "Alice Demo", password);
        User ben = ensureDemoUser("demo.ben@findit.local", "Ben Demo", password);
        ensureDemoUser("demo.chloe@findit.local", "Chloe Demo", password);

        if (itemRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();

        createItem(ItemType.LOST, "Black folding umbrella", "Electronics", "Main Library",
                "I left a black folding umbrella with a wooden handle near the main library entrance. "
                        + "It was raining in the afternoon and the handle has a small scratch.",
                today.minusDays(2), alice);
        createItem(ItemType.FOUND, "Black folding umbrella", "Electronics", "Main Library",
                "Found a black folding umbrella with a wooden handle next to the library notice board. "
                        + "The handle has a small scratch on one side.",
                today.minusDays(2), ben);
        createItem(ItemType.LOST, "Blue backpack with laptop sleeve", "Wallets & Bags", "Science Block B",
                "Navy blue backpack with a grey laptop sleeve and a small keychain. Left it in the lecture hall "
                        + "after the afternoon lab session.",
                today.minusDays(5), ben);
        createItem(ItemType.FOUND, "Blue backpack", "Wallets & Bags", "Science Block B",
                "Navy blue backpack found in lecture hall 2B, it has a grey laptop sleeve and a keychain with a "
                        + "small planet charm.",
                today.minusDays(4), alice);
    }

    private User ensureDemoUser(String email, String displayName, String password) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    User user = new User(email, displayName, passwordEncoder.encode(password), Role.USER);
                    user.setActive(true);
                    user.setContactChannel(email);
                    User saved = userRepository.save(user);
                    log.info("Created demo account {}", email);
                    return saved;
                });
    }

    private void createItem(ItemType type, String title, String categoryName, String locationName, String description,
                            LocalDate eventDate, User owner) {
        Category category = categoryRepository.findByNameIgnoreCase(categoryName).orElse(null);
        CampusLocation location = locationRepository.findByNameIgnoreCase(locationName).orElse(null);
        if (category == null || location == null) {
            log.warn("Skipping demo item '{}' - category or location is missing", title);
            return;
        }
        Item item = new Item();
        item.setType(type);
        item.setTitle(title);
        item.setDescription(description);
        item.setCategory(category);
        item.setLocation(location);
        item.setEventDate(eventDate);
        item.setStatus(ItemStatus.OPEN);
        item.setCreatedBy(owner);
        itemRepository.save(item);
    }
}
