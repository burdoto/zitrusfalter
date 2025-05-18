package de.kaleidox.zitrusfalter.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import lombok.Data;

import java.util.Set;
import java.util.stream.Stream;

@Data
@Entity
public class BingoRound {
    @Id         long           number;
    @OneToMany  Set<BingoCard> cards;
    @ManyToMany Set<FoodItem>  entries;

    public Stream<Player> scanWinners() {
        return cards.stream()
                .filter(card -> false/* todo */)
                .map(BingoCard::getPlayer);
    }
}
