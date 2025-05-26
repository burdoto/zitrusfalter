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
        return description == null ? "Keine Beschreibung" : description;
    }

    @Override
    public String toString() {
        return "%s%s%s (%.1f Punkte, %.0f%% Bonus)".formatted(emoji == null ? "" : emoji + " ", name, description == null ? "" : " - " + description, pointBonus, (pointFactor-1)*100);
    }
}
