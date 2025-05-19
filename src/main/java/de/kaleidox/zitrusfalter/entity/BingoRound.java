package de.kaleidox.zitrusfalter.entity;

import de.kaleidox.zitrusfalter.repo.FoodItemRepo;
import de.kaleidox.zitrusfalter.repo.PlayerRepo;
import de.kaleidox.zitrusfalter.util.ApplicationContextProvider;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import org.comroid.api.func.util.Streams;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class BingoRound {
    @Id         long           number;
    @OneToMany  Set<BingoCard> cards;
    @ManyToMany Set<FoodItem>  calls;
    @ManyToMany Set<BingoCard> winners = new HashSet<>();
    boolean ended = false;
    int     size  = 5;

    public BingoCard createCard(User user) {
        var player = ApplicationContextProvider.bean(PlayerRepo.class).get(user);

        var rng     = new Random();
        var lim     = size * size;
        var entries = new HashMap<Integer, FoodItem>(lim);
        var foods   = Streams.of(ApplicationContextProvider.bean(FoodItemRepo.class).findAll()).collect(Collectors.toList());

        for (var i = 0; i < lim; i++) {
            if (i == Math.ceil(size / 2f)) continue;

            var value = foods.remove(rng.nextInt(foods.size()));
            entries.put(i, value);
        }

        return new BingoCard(this, player, entries, new HashSet<>(), size);
    }

    public Stream<Player> scanWinners() {
        return cards.stream().filter(BingoCard::scanWin).map(BingoCard::getPlayer);
    }
}
