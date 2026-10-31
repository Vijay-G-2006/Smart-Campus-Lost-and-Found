package com.findit.service;

import com.findit.domain.Item;
import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import com.findit.dto.match.MatchResponse;
import com.findit.exception.NotFoundException;
import com.findit.repository.ItemRepository;
import com.findit.security.AppUserPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calculates match suggestions between open lost reports and open found reports.
 *
 * <p>The result is only a suggestion. It never approves a claim and never
 * reveals private verification details.</p>
 */
@Service
public class MatchService {

    /** Maximum number of suggestions returned for one report. */
    public static final int MAX_SUGGESTIONS = 10;

    private final ItemRepository itemRepository;
    private final MatchScoringService scoringService;
    private final ItemService itemService;

    public MatchService(ItemRepository itemRepository, MatchScoringService scoringService, ItemService itemService) {
        this.itemRepository = itemRepository;
        this.scoringService = scoringService;
        this.itemService = itemService;
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> findMatches(Long itemId, AppUserPrincipal viewer) {
        Item subject = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Report not found"));
        if (!itemService.isVisibleTo(subject, viewer)) {
            throw new NotFoundException("Report not found");
        }

        List<Item> candidates = itemRepository.findAll(candidateSpecification(subject, viewer));

        List<MatchResponse> matches = new ArrayList<>();
        for (Item candidate : candidates) {
            MatchScoringService.MatchResult result =
                    subject.getType() == ItemType.LOST ? scoringService.score(subject, candidate)
                            : scoringService.score(candidate, subject);
            if (result.total() <= 0) {
                continue;
            }
            matches.add(toMatchResponse(candidate, result));
        }

        matches.sort(Comparator
                .comparingInt(MatchResponse::score).reversed()
                .thenComparing(MatchResponse::eventDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return matches.stream().limit(MAX_SUGGESTIONS).toList();
    }

    private Specification<Item> candidateSpecification(Item subject, AppUserPrincipal viewer) {
        ItemType opposite = subject.getType() == ItemType.LOST ? ItemType.FOUND : ItemType.LOST;
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("type"), opposite));
            predicates.add(cb.equal(root.get("status"), ItemStatus.OPEN));
            predicates.add(cb.notEqual(root.get("id"), subject.getId()));
            if (viewer != null) {
                // Own reports are never suggested - a user cannot claim their own item.
                predicates.add(cb.notEqual(root.get("createdBy").get("id"), viewer.getId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private MatchResponse toMatchResponse(Item candidate, MatchScoringService.MatchResult result) {
        return new MatchResponse(
                candidate.getId(),
                candidate.getType(),
                candidate.getTitle(),
                candidate.getCategory().getName(),
                candidate.getLocation().getName(),
                candidate.getEventDate(),
                result.total(),
                result.band(),
                new MatchResponse.MatchBreakdown(
                        result.categoryPoints(),
                        result.descriptionPoints(),
                        result.locationPoints(),
                        result.datePoints(),
                        result.total(),
                        result.explanation()),
                MatchResponse.DISCLAIMER);
    }
}
