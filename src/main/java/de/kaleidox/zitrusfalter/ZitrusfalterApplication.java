package de.kaleidox.zitrusfalter;

import de.kaleidox.zitrusfalter.entity.BingoCard;
import de.kaleidox.zitrusfalter.entity.BingoRound;
import de.kaleidox.zitrusfalter.entity.FoodItem;
import de.kaleidox.zitrusfalter.entity.Player;
import de.kaleidox.zitrusfalter.repo.BingoCardRepo;
import de.kaleidox.zitrusfalter.repo.BingoRoundRepo;
import de.kaleidox.zitrusfalter.repo.FoodItemRepo;
import de.kaleidox.zitrusfalter.util.ApplicationContextProvider;
import de.kaleidox.zitrusfalter.util.AutoFillProvider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static de.kaleidox.zitrusfalter.util.ApplicationContextProvider.*;
import static org.comroid.api.func.util.Streams.*;

@Slf4j
@SpringBootApplication
@ComponentScan(basePackageClasses = ApplicationContextProvider.class)
public class ZitrusfalterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZitrusfalterApplication.class, args);
    }

    @Command(permission = "8")
    public static CompletableFuture<MessageCreateData> test(User user) {
        return CompletableFuture.supplyAsync(() -> new MessageCreateBuilder().setFiles(FileUpload.fromData(new BingoRound(-1,
                new HashSet<>(),
                new HashSet<>(),
                new HashSet<>(),
                false,
                5).createCard(user).createImage(), "card.png")).build());
    }

    @Command(permission = "8")
    public static String shutdown(User user) {
        System.exit(0);
        return "Goodbye";
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
            var adp = cmdr.new Adapter$JDA(jda.awaitReady());
            adp.setPurgeCommands(true);
            return adp;
        } finally {
            cmdr.initialize();
        }
    }

    @Command
    public static class bingo {
        @Command(permission = "8589934592")
        @Description("Starte eine neue Runde")
        public static String start() {
            var rounds = ApplicationContextProvider.bean(BingoRoundRepo.class);
            if (rounds.current().isPresent()) throw new Command.Error("Es ist bereits eine Runde im Gange");
            long number = rounds.nextNumber();
            var  round  = new BingoRound(number, new HashSet<>(), new HashSet<>(), new HashSet<>(), false, 5);
            rounds.save(round);
            return "Runde **%d** wurde gestartet".formatted(number);
        }

        @Command(permission = "8589934592")
        @Description("Füge eine Speise dem aktuellen Bingo-Pool hinzu")
        public static String call(
                @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("Name der Speise") String name
        ) {
            var food  = bean(FoodItemRepo.class).findByName(name).orElseThrow(() -> new Command.Error("Die Speise '%s' existiert nicht".formatted(name)));
            var round = bean(BingoRoundRepo.class).current().orElseThrow(() -> new Command.Error("Derzeit gibt es keine aktive Runde"));

            if (!round.getCalls().add(food)) throw new RuntimeException("Could not add call to card");

            return round.scanWinners()
                    .map(Player::getUser)
                    .map(User::getEffectiveName)
                    .collect(atLeastOneOrElseGet(() -> "%s wurde aufgerufen!".formatted(name)))
                    .collect(Collectors.joining("\n- ",
                            "# Wir haben Gewinner!\nAlle Gewinner müssen selbst Bingo aufrufen, bevor der nächste call erfolgt\n- ",
                            ""));
        }

        @Command
        @Description("Trete der aktuellen Runde bei")
        public static Object join(User user) {
            return bean(BingoRoundRepo.class).current()
                    .map(round -> bean(BingoCardRepo.class).save(round.createCard(user)))
                    .<Object>map(card -> new MessageCreateBuilder().addContent("Deine Karte")
                            .setFiles(FileUpload.fromData(card.createImage(), "card.png"))
                            .build())
                    .orElse("Es läuft derzeit keine Runde");
        }

        @Command
        @Description("Markiere eine Speise auf deiner Karte")
        public static String mark(
                User user, @Command.Arg(value = "name",
                                        autoFillProvider = AutoFillProvider.CalledFoods.class) @Description("Name der Speise") String name
        ) {
            var food  = bean(FoodItemRepo.class).findByName(name).orElseThrow(() -> new Command.Error("Die Speise '%s' existiert nicht".formatted(name)));
            var round = bean(BingoRoundRepo.class).current().orElseThrow(() -> new Command.Error("Derzeit gibt es keine aktive Runde"));
            var card  = round.getCard(user).orElseThrow(() -> new Command.Error("Du hast keine Karten"));

            if (!round.getCalls().contains(food)) throw new Command.Error("Diese Speise ist nicht aufgerufen worden");
            if (!card.getCalls().add(food)) throw new RuntimeException("Could not add call to card");
            if (card.scanWin()) log.info("{} hat eine Gewinnerkarte!", user.getEffectiveName());

            return "%s wurde markiert".formatted(food);
        }

        @Command(privacy = Command.PrivacyLevel.PUBLIC)
        @Description("Rufe 'Bingo!' wenn du soweit bist")
        public static String shout(User user) {
            return bean(BingoRoundRepo.class).current()
                    .stream()
                    .flatMap(round -> round.getCards().stream())
                    .filter(card -> card.getPlayer().getUser().equals(user))
                    .findAny()
                    .filter(BingoCard::scanWin)
                    .map($ -> "Bingo!")
                    .orElseThrow(() -> new Command.Error("Du hast noch kein Bingo"));
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
        @Description({ "Füge eine Speise dem Pool hinzu", "Achtung: Name kann nicht bearbeitet werden" })
        public static String add(
                @Command.Arg("name") @Description("Name der Speise") String name,
                @Command.Arg(value = "emoji", required = false) @Description("Emoji-Gruppe der Speise") String emoji
        ) {
            var foods = bean(FoodItemRepo.class);
            if (foods.existsByName(name)) throw new Command.Error("Eintrag `%s` existiert bereits".formatted(name));
            if (emoji.isBlank() || "food".equals(emoji)) emoji = null;
            var item = new FoodItem(name, emoji);
            foods.save(item);
            return "Eintrag erstellt:\n- %s".formatted(item);
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description("Ändere den Namen einer Speise")
        public static String rename(
                @Command.Arg(value = "old", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("Alter Name der Speise") String oldName,
                @Command.Arg(value = "new") @Description("Neuer Name der Speise") String newName
        ) {
            var foods = bean(FoodItemRepo.class);
            var item  = foods.findByName(oldName).orElseThrow(() -> new Command.Error("Eintrag `%s` existiert nicht".formatted(oldName)));
            item.setName(newName);
            foods.save(item);
            return "Eintrag aktualisiert:\n- %s".formatted(item);
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description("Ändere das Emoji einer Speise")
        public static String emoji(
                @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("Name der Speise") String name,
                @Command.Arg("emoji") @Description("Emoji der Speise") String emoji
        ) {
            var foods = bean(FoodItemRepo.class);
            var item  = foods.findByName(name).orElseThrow(() -> new Command.Error("Eintrag `%s` existiert nicht".formatted(name)));
            item.setEmoji(emoji);
            foods.save(item);
            return "Eintrag aktualisiert:\n- %s".formatted(item);
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description({ "Entferne eine Speise aus dem Pool", "Kann Probleme mit vergangenen Runden verursachen" })
        public static String remove(
                @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("Name der Speise") String name
        ) {
            var foods = bean(FoodItemRepo.class);
            if (!foods.existsByName(name)) throw new Command.Error("Eintrag `%s` existiert nicht".formatted(name));
            foods.deleteByName(name);
            return "Eintrag gelöscht: `%s`".formatted(name);
        }
    }
}
