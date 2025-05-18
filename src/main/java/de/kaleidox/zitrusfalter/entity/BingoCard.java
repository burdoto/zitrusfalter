package de.kaleidox.zitrusfalter.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@Data
@Entity
public class BingoCard {
    @Id @ManyToOne BingoRound                      round;
    @ManyToOne     Player                          player;
    @ManyToMany    Map<@NotNull Integer, FoodItem> entries;
}
