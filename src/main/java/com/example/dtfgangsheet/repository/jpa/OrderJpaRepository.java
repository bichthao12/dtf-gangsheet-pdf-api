package com.example.dtfgangsheet.repository.jpa;

import com.example.dtfgangsheet.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, String> {

    @Query("SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.lines ORDER BY o.submittedAt DESC")
    List<OrderEntity> findAllWithLines();

    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.lines WHERE o.id = :id")
    Optional<OrderEntity> findByIdWithLines(@Param("id") String id);
}
