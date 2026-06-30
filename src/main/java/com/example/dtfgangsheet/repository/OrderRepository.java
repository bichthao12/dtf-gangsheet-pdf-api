package com.example.dtfgangsheet.repository;

import com.example.dtfgangsheet.model.Order;
import com.example.dtfgangsheet.persistence.PersistenceMapper;
import com.example.dtfgangsheet.repository.jpa.OrderJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepository(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return jpaRepository.findAllWithLines().stream()
                .map(PersistenceMapper::toOrder)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(String id) {
        return jpaRepository.findByIdWithLines(id).map(PersistenceMapper::toOrder);
    }

    @Transactional
    public void save(Order order) {
        jpaRepository.save(PersistenceMapper.toOrderEntity(order));
    }
}
