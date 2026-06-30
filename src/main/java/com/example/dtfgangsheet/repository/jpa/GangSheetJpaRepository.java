package com.example.dtfgangsheet.repository.jpa;

import com.example.dtfgangsheet.entity.GangSheetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GangSheetJpaRepository extends JpaRepository<GangSheetEntity, String> {

    List<GangSheetEntity> findAllByOrderByUpdatedAtDesc();
}
