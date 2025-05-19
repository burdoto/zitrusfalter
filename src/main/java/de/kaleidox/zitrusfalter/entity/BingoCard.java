package de.kaleidox.zitrusfalter.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class BingoCard {
    @Id @ManyToOne BingoRound                      round;
    @ManyToOne     Player                          player;
    @ManyToMany    Map<@NotNull Integer, FoodItem> entries;
    @ManyToMany    Set<FoodItem>                   calls;
    int size = 5;

    public boolean scanWin() {
        // check columns
        nextRow:
        for (var x = 0; x < size; x++) {
            for (var y = 0; y < size; y++)
                if (!calls.contains(entries.getOrDefault(id(x, y), null))) continue nextRow;
            return true;
        }

        // check rows
        nextCol:
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++)
                if (!calls.contains(entries.getOrDefault(id(x, y), null))) continue nextCol;
            return true;
        }

        // check diagonals
        for (var i = 0; i < size; i++) {
            if (i == Math.ceil(size / 2f)) continue;
            if (!calls.contains(entries.getOrDefault(id(i, i), null))) continue;
            if (!calls.contains(entries.getOrDefault(id(i, size - i - 1), null))) continue;
            return true;
        }

        return false;
    }

    private int id(int x, int y) {
        return x + (y * size);
    }
}
