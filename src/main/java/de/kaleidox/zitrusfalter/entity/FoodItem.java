package de.kaleidox.zitrusfalter.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@Entity
@NoArgsConstructor
public class FoodItem {
    @Id       String name;
    @Nullable String emoji;
    double pointBonus  = 1;
    double pointFactor = 1;

    public FoodItem(String name, @Nullable String emoji) {
        this.name  = name;
        this.emoji = emoji;
    }

    @Override
    public String toString() {
        return emoji + ' ' + name;
    }
}
