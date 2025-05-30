package de.kaleidox.zitrusfalter;

import de.kaleidox.zitrusfalter.entity.BingoCard;
import de.kaleidox.zitrusfalter.entity.BingoRound;
import de.kaleidox.zitrusfalter.entity.FoodItem;
import de.kaleidox.zitrusfalter.repo.BingoCardRepo;
import de.kaleidox.zitrusfalter.repo.BingoRoundRepo;
import de.kaleidox.zitrusfalter.repo.FoodItemRepo;
import de.kaleidox.zitrusfalter.repo.PlayerRepo;
import de.kaleidox.zitrusfalter.util.ApplicationContextProvider;
import de.kaleidox.zitrusfalter.util.AutoFillProvider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.comroid.annotations.Description;
import org.comroid.api.func.comp.StringBasedComparator;
import org.comroid.api.func.util.Command;
import org.comroid.api.text.StringMode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static de.kaleidox.zitrusfalter.util.ApplicationContextProvider.*;
import static org.comroid.api.func.util.Streams.*;

@Slf4j
@Component
public class BingoController {
    @Command
    public static class bingo {
        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
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
            bean(BingoRoundRepo.class).save(round);

            return "%s wurde aufgerufen!".formatted(name);
        }

        @Command(privacy = Command.PrivacyLevel.PUBLIC)
        @Description("Trete der aktuellen Runde bei")
        public static CompletableFuture<Object> join(User user) {
            return CompletableFuture.supplyAsync(() -> bean(BingoRoundRepo.class).current()
                    .map(round -> {
                        var card = round.createCard(user);
                        card = bean(BingoCardRepo.class).save(card);
                        bean(BingoRoundRepo.class).save(round);
                        return card;
                    })
                    .<Object>map(card -> new MessageCreateBuilder().addContent("Deine Karte")
                            .setFiles(FileUpload.fromData(card.generateImage(), "card.png"))
                            .build())
                    .orElse("Es läuft derzeit keine Runde"));
        }

        @Command(privacy = Command.PrivacyLevel.PUBLIC)
        @Description("Zeige deine Karte an")
        public static CompletableFuture<Object> card(User user) {
            return CompletableFuture.supplyAsync(() -> bean(BingoRoundRepo.class).current()
                    .flatMap(round -> round.getCard(user))
                    .<Object>map(card -> new MessageCreateBuilder().addContent("Deine Karte")
                            .setFiles(FileUpload.fromData(card.generateImage(), "card.png"))
                            .build())
                    .orElse("Du hast keine Karte, entweder weil keine Runde läuft oder weil du nicht beigetreten bist"));
        }

        @Command(privacy = Command.PrivacyLevel.PUBLIC)
        @Description("Markiere eine Speise auf deiner Karte")
        public static CompletableFuture<MessageCreateData> mark(
                User user, @Command.Arg(value = "name",
                                        autoFillProvider = AutoFillProvider.CalledFoods.class) @Description(
                        "Name der Speise") String name
        ) {
            var food  = bean(FoodItemRepo.class).findByName(name).orElseThrow(() -> new Command.Error("Die Speise '%s' existiert nicht".formatted(name)));
            var round = bean(BingoRoundRepo.class).current().orElseThrow(() -> new Command.Error("Derzeit gibt es keine aktive Runde"));
            var card  = round.getCard(user).orElseThrow(() -> new Command.Error("Du hast keine Karten"));

            if (!round.getCalls().contains(food)) throw new Command.Error("Diese Speise ist nicht aufgerufen worden");
            if (!card.getEntries().containsValue(food)) throw new Command.Error("Das steht nicht auf deiner Karte");
            if (!card.getCalls().add(food)) throw new RuntimeException("Could not add call to card");
            if (card.scanWin()) log.info("{} hat eine Gewinnerkarte!", user.getEffectiveName());
            bean(BingoCardRepo.class).save(card);

            return CompletableFuture.supplyAsync(() -> new MessageCreateBuilder().setContent("%s wurde markiert".formatted(food))
                    .setFiles(FileUpload.fromData(card.generateImage(), "card.png"))
                    .build());
        }

        @Command(privacy = Command.PrivacyLevel.PUBLIC)
        @Description("Rufe 'Bingo!' wenn du soweit bist")
        public static CompletableFuture<MessageCreateData> shout(User user) {
            var current = bean(BingoRoundRepo.class).current();
            var card = current.stream()
                    .flatMap(round -> round.getCards().stream())
                    .filter(it -> it.getPlayer().getUser().equals(user))
                    .findAny()
                    .filter(BingoCard::scanWin)
                    .orElseThrow(() -> new Command.Error("Du hast noch kein Bingo"));

            var round = current.orElseThrow();
            round.setEnded(true);
            round.getWinners().add(card.getPlayer());
            bean(BingoRoundRepo.class).save(round);

            return CompletableFuture.supplyAsync(() -> new MessageCreateBuilder().setContent("# Bingo!")
                    .setFiles(FileUpload.fromData(card.generateImage(), "card.png"))
                    .build());
        }

        @Command
        public static class food {
            @Command(permission = "8589934592")
            @Description("Listet alle Speisen im gesamten Pool")
            public static CompletableFuture<?> list(MessageChannelUnion channel) {
                return new Command.Manager.Adapter$JDA.PaginatedList<>(channel,
                        () -> of(bean(FoodItemRepo.class).findAll()),
                        new StringBasedComparator<>(FoodItem::getName),
                        item -> new MessageEmbed.Field(item.getName(), item.toString(), false),
                        "Speisen",
                        8) {
                    @Override
                    protected void finalizeEmbed(EmbedBuilder embed) {
                        embed.setColor(ZitrusfalterApplication.THEME);
                    }

                    @Override
                    protected String pageText() {
                        return super.pageText().replace("Page", "Seite");
                    }
                }.resend().submit().thenApply($ -> "Liste erstellt!");
            }

            @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
            @Description({ "Füge eine Speise dem Pool hinzu", "Achtung: Name kann nicht bearbeitet werden" })
            public static String add(
                    @Command.Arg("name") @Description("Name der Speise") String name,
                    @Command.Arg(value = "emoji", required = false) @Description("Emoji-Gruppe der Speise") String emoji,
                    @Command.Arg(value = "description", required = false) @Description("Beschreibung der Speise") String description,
                    @Command.Arg(value = "points", required = false) @Description("Punkte der Speise; standard = 1.0") Double pointBonus,
                    @Command.Arg(value = "factor", required = false) @Description("Bonusfaktor der Speise; standard: 1.0") Double pointFactor
            ) {
                var foods = bean(FoodItemRepo.class);
                if (foods.existsByName(name)) throw new Command.Error("Eintrag `%s` existiert bereits".formatted(name));
                if (emoji.isBlank() || "food".equals(emoji)) emoji = null;
                if (description.isBlank() || "food".equals(description)) emoji = null;
                var item = new FoodItem(name, emoji);
                if (description != null) item.setDescription(description);
                if (pointBonus != null) item.setPointBonus(pointBonus);
                if (pointFactor != null) item.setPointFactor(pointFactor);
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
            @Description("Ändere die Beschreibung einer Speise")
            public static String description(
                    @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("Name der Speise") String name,
                    @Command.Arg(value = "description", stringMode = StringMode.GREEDY) @Description("Beschreibung der Speise") String description
            ) {
                var foods = bean(FoodItemRepo.class);
                var item  = foods.findByName(name).orElseThrow(() -> new Command.Error("Eintrag `%s` existiert nicht".formatted(name)));
                item.setDescription(description);
                foods.save(item);
                return "Eintrag aktualisiert:\n- %s".formatted(item);
            }

            @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
            @Description("Ändere die Punkte für eine Speise")
            public static String points(
                    @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("Name der Speise") String name,
                    @Command.Arg("points") @Description("Punkte für die Speise; standard = 1.0") double points
            ) {
                var foods = bean(FoodItemRepo.class);
                var item  = foods.findByName(name).orElseThrow(() -> new Command.Error("Eintrag `%s` existiert nicht".formatted(name)));
                item.setPointBonus(points);
                foods.save(item);
                return "Eintrag aktualisiert:\n- %s".formatted(item);
            }

            @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
            @Description("Ändere den Bonusfaktor für Speise")
            public static String factor(
                    @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("Name der Speise") String name,
                    @Command.Arg("factor") @Description("Bonusfaktor für die Speise; standard = 1.0") double factor
            ) {
                var foods = bean(FoodItemRepo.class);
                var item  = foods.findByName(name).orElseThrow(() -> new Command.Error("Eintrag `%s` existiert nicht".formatted(name)));
                item.setPointFactor(factor);
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

        @Command
        public static class score {
            @Command(privacy = Command.PrivacyLevel.PUBLIC)
            @Description("Ruft die Scores von einem Spieler auf")
            public static String of(@Command.Arg("player") @Description("Spieler, dessen Scores abgerufen werden sollen") User target) {
                var players = bean(PlayerRepo.class);
                var idLong  = target.getIdLong();
                return "%s hat insgesamt %d Siege und %.2f Punkte".formatted(target.getEffectiveName(),
                        Objects.requireNonNullElse(players.wins(idLong), 0),
                        Objects.requireNonNullElse(players.totalScore(idLong), 0d));
            }
        }
    }
}
