package de.kaleidox.zitrusfalter.repo;

import de.kaleidox.zitrusfalter.entity.Player;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;

public interface PlayerRepo extends CrudRepository<Player, @NotNull Long> {
    default Player get(User user) {
        var id = user.getIdLong();
        if (existsById(id)) return findById(id).orElseThrow();
        return save(new Player(id, 0, 0));
    }
}
