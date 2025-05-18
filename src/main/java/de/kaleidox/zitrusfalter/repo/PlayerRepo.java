package de.kaleidox.zitrusfalter.repo;

import de.kaleidox.zitrusfalter.entity.Player;
import net.dv8tion.jda.api.entities.User;
import org.springframework.data.repository.CrudRepository;

public interface PlayerRepo extends CrudRepository<Player, User> {
}
