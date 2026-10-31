package com.findit.repository;

import com.findit.domain.Claim;
import com.findit.domain.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    boolean existsByItemIdAndClaimantIdAndStatusIn(Long itemId, Long claimantId, Collection<ClaimStatus> statuses);

    boolean existsByItemIdAndStatus(Long itemId, ClaimStatus status);

    long countByStatus(ClaimStatus status);

    long countByItemIdAndStatus(Long itemId, ClaimStatus status);
}
