package de.kaleidox.zitrusfalter;

import de.kaleidox.zitrusfalter.entity.BingoRound;
import de.kaleidox.zitrusfalter.entity.FoodItem;
import de.kaleidox.zitrusfalter.entity.Player;
import de.kaleidox.zitrusfalter.repo.BingoRoundRepo;
import de.kaleidox.zitrusfalter.repo.FoodItemRepo;
import de.kaleidox.zitrusfalter.repo.PlayerRepo;
import de.kaleidox.zitrusfalter.util.ApplicationContextProvider;
import de.kaleidox.zitrusfalter.util.AutoFillProvider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.GatewayIntent;
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
import java.util.stream.Collectors;

import static org.comroid.api.func.util.Streams.*;

@SpringBootApplication
@ComponentScan(basePackageClasses = ApplicationContextProvider.class)
public class ZitrusfalterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZitrusfalterApplication.class, args);
    }

    @Autowired PlayerRepo     players;
    @Autowired BingoRoundRepo rounds;
    @Autowired FoodItemRepo   foods;

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
        @Command
        public String call(ZitrusfalterApplication app, @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.FoodByName.class) String name) {
            return app.foods.findById(name)
                    .flatMap(food -> of(app.rounds.findAll()).max(Comparator.comparingLong(BingoRound::getNumber))
                            .filter(round -> round.getEntries().add(food)))
                    .stream()
                    .flatMap(BingoRound::scanWinners)
                    .map(Player::getUser)
                    .map(User::getEffectiveName)
                    .collect(atLeastOneOrElseGet(() -> "%s wurde aufgerufen!".formatted(name)))
                    .collect(Collectors.joining("\n- ",
                            "# Wir haben Gewinner!\nAlle Gewinner müssen selbst Bingo aufrufen, bevor der nächste call erfolgt\n- ",
                            ""));
        }
    }

    @Command
    public static class food {
        @Command(privacy = Command.PrivacyLevel.PUBLIC)
        public String list(ZitrusfalterApplication app) {
            return "Alle Einträge:" + of(app.foods.findAll()).map(FoodItem::getName)
                    .collect(atLeastOneOrElseGet(() -> "Es gibt keine Einträge"))
                    .collect(Collectors.joining("\n- ", "- ", ""));
        }

        @Command(privacy = Command.PrivacyLevel.PUBLIC)
        public String add(ZitrusfalterApplication app, @Command.Arg("name") String name, @Command.Arg(value = "emoji", required = false) String emoji) {
            if (app.foods.existsById(name)) throw new Command.Error("Eintrag `%s` existiert bereits".formatted(name));
            if (emoji.isBlank()) emoji = null;
            var item = new FoodItem(name, emoji);
            app.foods.save(item);
            return "Eintrag erstellt:\n- %s".formatted(item);
        }

        @Command(privacy = Command.PrivacyLevel.PUBLIC)
        public String remove(ZitrusfalterApplication app, @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.FoodByName.class) String name) {
            if (!app.foods.existsById(name)) throw new Command.Error("Eintrag `%s` existiert nicht".formatted(name));
            app.foods.deleteById(name);
            return "Eintrag gelöscht: `%s`".formatted(name);
        }
    }
}
