package de.kaleidox.zitrusfalter.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static org.comroid.api.text.Translation.*;

@Data
@Entity
@NoArgsConstructor
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = { "name" }) })
public class FoodItem {
    @Id                 UUID   id          = UUID.randomUUID();
    @NotNull            String name;
    @Nullable           String emoji;
    @Nullable           String description;
    @ColumnDefault("1") double pointBonus  = 1;
    @ColumnDefault("1") double pointFactor = 1;

    public FoodItem(String name, @Nullable String emoji) {
        this.name  = name;
        this.emoji = emoji;
    }

    public @Nullable String getDescription() {
        return description == null ? str("generic.desc.notfound") : description;
    }

    @Override
    public String toString() {
        return str("bingo.food.string.emoji.name.desc.points.factor").formatted(emoji == null ? "" : emoji + " ",
                name,
                description == null ? "" : " - " + description,
                pointBonus,
                (pointFactor - 1) * 100);
    }
}
