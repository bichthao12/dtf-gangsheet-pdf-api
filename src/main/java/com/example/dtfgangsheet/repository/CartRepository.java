package com.example.dtfgangsheet.repository;

import com.example.dtfgangsheet.model.Cart;
import com.example.dtfgangsheet.model.CartLine;
import com.example.dtfgangsheet.persistence.PersistenceMapper;
import com.example.dtfgangsheet.repository.jpa.CartLineJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Repository
public class CartRepository {

    private final CartLineJpaRepository jpaRepository;

    public CartRepository(CartLineJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public Cart load() {
        List<CartLine> lines = jpaRepository.findAllByOrderByAddedAtAsc().stream()
                .map(PersistenceMapper::toCartLine)
                .toList();

        Instant updatedAt = lines.stream()
                .map(CartLine::updatedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        return new Cart(lines, updatedAt);
    }

    @Transactional
    public void save(Cart cart) {
        jpaRepository.deleteAllInBatch();
        for (CartLine line : cart.lines()) {
            jpaRepository.save(PersistenceMapper.toCartLineEntity(line));
        }
    }

    @Transactional
    public void clear() {
        jpaRepository.deleteAllInBatch();
    }
}
