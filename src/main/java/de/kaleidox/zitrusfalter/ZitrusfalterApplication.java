package de.kaleidox.zitrusfalter;

import de.kaleidox.zitrusfalter.entity.BingoRound;
import de.kaleidox.zitrusfalter.entity.FoodItem;
import de.kaleidox.zitrusfalter.entity.Player;
import de.kaleidox.zitrusfalter.repo.BingoRoundRepo;
import de.kaleidox.zitrusfalter.repo.FoodItemRepo;
import de.kaleidox.zitrusfalter.util.ApplicationContextProvider;
import de.kaleidox.zitrusfalter.util.AutoFillProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.comroid.annotations.Description;
import org.comroid.api.config.ConfigurationManager;
import org.comroid.api.func.ext.Context;
import org.comroid.api.func.util.Command;
import org.comroid.api.io.FileHandle;
import org.mariadb.jdbc.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

import static de.kaleidox.zitrusfalter.util.ApplicationContextProvider.*;
import static org.comroid.api.func.util.Streams.*;

@SpringBootApplication
@ComponentScan(basePackageClasses = ApplicationContextProvider.class)
public class ZitrusfalterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZitrusfalterApplication.class, args);
    }

    @Bean
    public FileHandle configDir() {
        return new FileHandle("/srv/discord/zitrus", true);
    }

    @Bean
    public FileHandle configFile(@Autowired FileHandle configDir) {
        return configDir.createSubFile("config.json");
    }

    @Bean
    public ConfigurationManager<BotConfig> configManager(@Autowired Context context, @Autowired FileHandle configFile) {
        return new ConfigurationManager<>(context, BotConfig.class, configFile.getAbsolutePath());
    }

    @Bean
    @Order
    public BotConfig config(@Autowired ConfigurationManager<BotConfig> configManager) {
        configManager.initialize();
        return configManager.getConfig();
    }

    @Bean
    public JDA jda(@Autowired BotConfig config) {
        return JDABuilder.create(config.token, GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS)).build();
    }

    @Bean
    public DataSource database(@Autowired BotConfig config) {
        return DataSourceBuilder.create()
                .driverClassName(Driver.class.getCanonicalName())
                .url(config.database.uri)
                .username(config.database.username)
                .password(config.database.password)
                .build();
    }

    @Bean
    public Command.Manager cmdr() {
        var cmdr = new Command.Manager();
        cmdr.addChild(this);
        cmdr.register(this);
        return cmdr;
    }

    @Bean
    public Command.Manager.Adapter$JDA cmdrJdaAdapter(@Autowired Command.Manager cmdr, @Autowired JDA jda) throws InterruptedException {
        try {
            return cmdr.new Adapter$JDA(jda.awaitReady());
        } finally {
            cmdr.initialize();
        }
    }

    @Command
    public static class bingo {
        @Command(permission = "8589934592")
        @Description("Füge eine Speise dem aktuellen Bingo-Pool hinzu")
        public static String add(
                @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.FoodByName.class) @Description("Name der Speise") String name
        ) {
            return bean(FoodItemRepo.class).findById(name)
                    .flatMap(food -> of(bean(BingoRoundRepo.class).findAll()).max(Comparator.comparingLong(BingoRound::getNumber))
                            .filter(round -> round.getCalls().add(food)))
                    .stream()
                    .flatMap(BingoRound::scanWinners)
                    .map(Player::getUser)
                    .map(User::getEffectiveName)
                    .collect(atLeastOneOrElseGet(() -> "%s wurde aufgerufen!".formatted(name)))
                    .collect(Collectors.joining("\n- ",
                            "# Wir haben Gewinner!\nAlle Gewinner müssen selbst Bingo aufrufen, bevor der nächste call erfolgt\n- ",
                            ""));
        }

        @Command(privacy = Command.PrivacyLevel.PUBLIC)
        public static String shout() {
        }
    }

    @Command
    public static class food {
        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description("Listet alle Speisen im gesamten Pool")
        public static String list() {
            return "Alle Einträge:\n" + of(bean(FoodItemRepo.class).findAll()).map(Objects::toString)
                    .collect(atLeastOneOrElseGet(() -> "Es gibt keine Einträge"))
                    .collect(Collectors.joining("\n- ", "- ", ""));
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description({ "Füge eine Speise dem Pool hinzu", "Achtung: Name kann nicht bearbeitet werden, nachdem die Speise in einem Pool genutzt wurde" })
        public static String add(
                @Command.Arg("name") @Description("Name der Speise") String name,
                @Command.Arg(value = "emoji", required = false) @Description("Emoji-Gruppe der Speise") String emoji
        ) {
            var foods = bean(FoodItemRepo.class);
            if (foods.existsById(name)) throw new Command.Error("Eintrag `%s` existiert bereits".formatted(name));
            if (emoji.isBlank() || "food".equals(emoji)) emoji = null;
            var item = new FoodItem(name, emoji);
            foods.save(item);
            return "Eintrag erstellt:\n- %s".formatted(item);
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description({ "Entferne eine Speise aus dem Pool", "Kann Probleme mit vergangenen Runden verursachen" })
        public static String remove(
                @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.FoodByName.class) @Description("Name der Speise") String name
        ) {
            var foods = bean(FoodItemRepo.class);
            if (!foods.existsById(name)) throw new Command.Error("Eintrag `%s` existiert nicht".formatted(name));
            foods.deleteById(name);
            return "Eintrag gelöscht: `%s`".formatted(name);
        }
    }
}
