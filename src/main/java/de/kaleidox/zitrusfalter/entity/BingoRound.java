package de.kaleidox.zitrusfalter.entity;

import de.kaleidox.zitrusfalter.repo.BingoCardRepo;
import de.kaleidox.zitrusfalter.repo.FoodItemRepo;
import de.kaleidox.zitrusfalter.repo.PlayerRepo;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.kaleidox.zitrusfalter.util.ApplicationContextProvider.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class BingoRound {
    @Id                                  long           number;
    @OneToMany(fetch = FetchType.EAGER)  Set<BingoCard> cards   = new HashSet<>();
    @ManyToMany(fetch = FetchType.EAGER) Set<FoodItem>  calls   = new HashSet<>();
    @ManyToMany(fetch = FetchType.EAGER) Set<Player>    winners = new HashSet<>();
    boolean ended = false;
    int     size  = 5;

    public BingoCard createCard(User user) {
        var player = bean(PlayerRepo.class).get(user);

        var rng     = new Random();
        var lim     = size * size;
        var mid     = (int) Math.ceil(size / 2f) - 1;
        var entries = new HashMap<Integer, FoodItem>(lim);
        var foods   = Streams.of(bean(FoodItemRepo.class).findAll()).collect(Collectors.toList());

        for (var i = 0; i < lim; i++) {
            if ((i / size) == mid && i % size == mid) continue;

            var value = foods.remove(rng.nextInt(foods.size()));
            entries.put(i, value);
        }

        var card = new BingoCard(UUID.randomUUID(), player, entries, new HashSet<>(), size);
        cards.add(card);
        return card;
    }

    public Optional<BingoCard> getCard(User user) {
        return cards.stream().filter(card -> card.player.userId == user.getIdLong()).findAny();
    }

    public Stream<Player> scanWinners() {
        return cards.stream().filter(BingoCard::scanWin).map(BingoCard::getPlayer);
    }
}
