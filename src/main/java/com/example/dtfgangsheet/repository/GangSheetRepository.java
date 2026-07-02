package com.example.dtfgangsheet.repository;

import com.example.dtfgangsheet.model.SavedGangSheet;
import com.example.dtfgangsheet.persistence.PersistenceMapper;
import com.example.dtfgangsheet.repository.jpa.GangSheetJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class GangSheetRepository {

    private final GangSheetJpaRepository jpaRepository;

    public GangSheetRepository(GangSheetJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public List<SavedGangSheet> findAll() {
        return jpaRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .map(PersistenceMapper::toGangSheet)
                .toList();
    }

    public Optional<SavedGangSheet> findById(String id) {
        return jpaRepository.findByIdAndIsDeletedFalse(id).map(PersistenceMapper::toGangSheet);
    }

    @Transactional
    public void save(SavedGangSheet gangSheet) {
        jpaRepository.save(PersistenceMapper.toGangSheetEntity(gangSheet));
    }

    /** Soft-delete: {@code is_deleted=true}, {@code deleted_at=now}; row kept for audit. */
    @Transactional
    public void softDeleteById(String id) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.markDeleted(Instant.now());
            jpaRepository.save(entity);
        });
    }
}
