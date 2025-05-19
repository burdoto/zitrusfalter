package de.kaleidox.zitrusfalter.repo;

import de.kaleidox.zitrusfalter.entity.Player;
import net.dv8tion.jda.api.entities.User;
import org.springframework.data.repository.CrudRepository;

public interface PlayerRepo extends CrudRepository<Player, User> {
    default Player get(User user) {
        if (existsById(user)) return findById(user).orElseThrow();
        // do not save() because we dont want zero-data in database
        return new Player(user.getIdLong(), 0, 0);
    }
}
