package de.kaleidox.zitrusfalter.repo;

import de.kaleidox.zitrusfalter.entity.FoodItem;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface FoodItemRepo extends CrudRepository<FoodItem, UUID> {
    boolean existsByName(String name);

    Optional<FoodItem> findByName(String name);

    void deleteByName(String name);

    Stream<FoodItem> findByEmoji(String emoji);
}
