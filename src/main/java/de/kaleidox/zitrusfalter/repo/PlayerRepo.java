package de.kaleidox.zitrusfalter.repo;

import de.kaleidox.zitrusfalter.entity.Player;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface PlayerRepo extends CrudRepository<Player, @NotNull Long> {
    default Player get(User user) {
        var id = user.getIdLong();
        if (existsById(id)) return findById(id).orElseThrow();
        return save(new Player(id, 0, 0));
    }

    @Query("""
            select (SUM(e.pointBonus) * (SUM(e.pointFactor) - COUNT(e) + 1))
             from BingoCard c inner join c.calls e
             where c.player.userId = :#{#userId}""")
    @Nullable Double totalScore(@Param("userId") long userId);

    @Query("select COUNT(r) from BingoRound r inner join r.winners w where w.userId = :#{#userId}")
    @Nullable Integer wins(@Param("userId") long userId);
}
