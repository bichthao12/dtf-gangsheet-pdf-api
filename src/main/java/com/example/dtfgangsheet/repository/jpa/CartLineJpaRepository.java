package com.example.dtfgangsheet.repository.jpa;

import com.example.dtfgangsheet.entity.CartLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartLineJpaRepository extends JpaRepository<CartLineEntity, String> {

    List<CartLineEntity> findAllByOrderByAddedAtAsc();

    Optional<CartLineEntity> findByDesignId(String designId);

    void deleteByDesignId(String designId);
}
