package com.example.dtfgangsheet.repository.jpa;

import com.example.dtfgangsheet.entity.GangSheetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GangSheetJpaRepository extends JpaRepository<GangSheetEntity, String> {

    List<GangSheetEntity> findAllByIsDeletedFalseOrderByUpdatedAtDesc();

    Optional<GangSheetEntity> findByIdAndIsDeletedFalse(String id);
}
