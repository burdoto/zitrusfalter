package de.kaleidox.zitrusfalter.entity;

import de.kaleidox.zitrusfalter.util.ApplicationContextProvider;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OrderBy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    @Id      long   userId;
    @OrderBy double totalScore;
    int totalWins;

    public User getUser() {
        return ApplicationContextProvider.bean(JDA.class).retrieveUserById(userId).complete();
    }
}
