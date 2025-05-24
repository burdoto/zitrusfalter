package de.kaleidox.zitrusfalter.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
public class FoodItem {
    @Id                 UUID   id          = UUID.randomUUID();
    @NotNull            String name;
    @Nullable           String emoji;
    @ColumnDefault("1") double pointBonus  = 1;
    @ColumnDefault("1") double pointFactor = 1;

    public FoodItem(String name, @Nullable String emoji) {
        this.name  = name;
        this.emoji = emoji;
    }

    @Override
    public String toString() {
        return emoji + ' ' + name;
    }
}
