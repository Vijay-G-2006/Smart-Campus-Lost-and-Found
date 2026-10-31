package com.findit.repository;

import com.findit.domain.Item;
import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {

    List<Item> findByCreatedByIdOrderByCreatedAtDesc(Long createdById);

    long countByTypeAndStatus(ItemType type, ItemStatus status);

    long countByStatus(ItemStatus status);

    long countByType(ItemType type);
}
