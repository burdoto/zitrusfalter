package de.kaleidox.zitrusfalter.repo;

import de.kaleidox.zitrusfalter.entity.FoodItem;
import de.kaleidox.zitrusfalter.entity.Player;
import net.dv8tion.jda.api.entities.User;
import org.springframework.data.repository.CrudRepository;

import java.util.stream.Stream;

public interface FoodItemRepo extends CrudRepository<FoodItem, String> {
    Stream<FoodItem> findByEmoji(String emoji);
}
