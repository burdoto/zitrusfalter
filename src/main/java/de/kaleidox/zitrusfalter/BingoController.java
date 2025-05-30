package de.kaleidox.zitrusfalter;

import de.kaleidox.zitrusfalter.entity.BingoCard;
import de.kaleidox.zitrusfalter.entity.BingoRound;
import de.kaleidox.zitrusfalter.entity.FoodItem;
import de.kaleidox.zitrusfalter.entity.Player;
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
import java.util.stream.Collectors;

import static de.kaleidox.zitrusfalter.util.ApplicationContextProvider.*;
import static org.comroid.api.func.util.Streams.*;
import static org.comroid.api.text.Translation.*;

@Slf4j
@Component
@Command("bingo")
public class BingoController {
    @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
    @Description("bingo.command.desc.start")
    public static String start() {
        var rounds = ApplicationContextProvider.bean(BingoRoundRepo.class);
        if (rounds.current().isPresent()) throw new Command.Error("bingo.round.error.ongoing");
        long number = rounds.nextNumber();
        var  round  = new BingoRound(number, new HashSet<>(), new HashSet<>(), new HashSet<>(), false, 5);
        rounds.save(round);
        return str("bingo.round.started").formatted(number);
    }

    @Command(permission = "8589934592")
    @Description("bingo.command.desc.call")
    public static String call(
            @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("bingo.command.arg.food.name") String name
    ) {
        var food  = bean(FoodItemRepo.class).findByName(name).orElseThrow(() -> new Command.Error(str("bingo.error.food.notfound").formatted(name)));
        var round = bean(BingoRoundRepo.class).current().orElseThrow(() -> new Command.Error("bingo.error.round.notfound"));

        if (!round.getCalls().add(food)) throw new RuntimeException(str("bingo.error.card.invalid_add_call"));
        bean(BingoRoundRepo.class).save(round);

        return str("bingo.call").formatted(name);
    }

    @Command(privacy = Command.PrivacyLevel.PUBLIC)
    @Description("bingo.command.desc.players")
    public static String players(User user) {
        return bean(BingoRoundRepo.class).current()
                .orElseThrow(() -> new Command.Error("bingo.error.round.notfound"))
                .getCards()
                .stream()
                .map(BingoCard::getPlayer)
                .map(Player::getUser)
                .map(User::getEffectiveName)
                .collect(atLeastOneOrElseGet(() -> str("bingo.error.players.notfound")))
                .collect(Collectors.joining(":\n- ", str("bingo.round.players") + "\n- ", ""));
    }

    @Command(privacy = Command.PrivacyLevel.PUBLIC)
    @Description("bingo.command.desc.called")
    public static String called(User user) {
        return bean(BingoRoundRepo.class).current()
                .orElseThrow(() -> new Command.Error("bingo.error.round.notfound"))
                .getCalls()
                .stream()
                .map(FoodItem::getName)
                .collect(atLeastOneOrElseGet(() -> str("bingo.error.call.food.notfound")))
                .collect(Collectors.joining(":\n- ", str("bingo.round.called") + "\n- ", ""));
    }

    @Command(privacy = Command.PrivacyLevel.PUBLIC)
    @Description("bingo.command.desc.join")
    public static CompletableFuture<Object> join(User user) {
        return CompletableFuture.supplyAsync(() -> bean(BingoRoundRepo.class).current()
                .map(round -> {
                    var card = round.createCard(user);
                    card = bean(BingoCardRepo.class).save(card);
                    bean(BingoRoundRepo.class).save(round);
                    return (BingoCard) card;
                })
                .<Object>map(card -> new MessageCreateBuilder().addContent(str("bingo.card.yours"))
                        .setFiles(FileUpload.fromData(card.generateImage(), "card.png"))
                        .build())
                .orElseGet(() -> str("bingo.error.round.notfound")));
    }

    @Command(privacy = Command.PrivacyLevel.PUBLIC)
    @Description("bingo.command.desc.card")
    public static CompletableFuture<Object> card(User user) {
        return CompletableFuture.supplyAsync(() -> bean(BingoRoundRepo.class).current()
                .orElseThrow(() -> new Command.Error("bingo.error.round.notfound"))
                .getCard(user)
                .<Object>map(card -> new MessageCreateBuilder().addContent(str("bingo.card.yours"))
                        .setFiles(FileUpload.fromData(card.generateImage(), "card.png"))
                        .build())
                .orElseGet(() -> str("bingo.error.card.notfound")));
    }

    @Command(privacy = Command.PrivacyLevel.PUBLIC)
    @Description("bingo.command.desc.mark")
    public static CompletableFuture<MessageCreateData> mark(
            User user,
            @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.CalledFoods.class) @Description("bingo.command.arg.food.name") String name
    ) {
        var food  = bean(FoodItemRepo.class).findByName(name).orElseThrow(() -> new Command.Error(str("bingo.error.food.notfound").formatted(name)));
        var round = bean(BingoRoundRepo.class).current().orElseThrow(() -> new Command.Error("bingo.error.round.notfound"));
        var card  = round.getCard(user).orElseThrow(() -> new Command.Error("bingo.error.card.notfound"));

        if (!round.getCalls().contains(food)) throw new Command.Error("bingo.error.round.call.notfound");
        if (!card.getEntries().containsValue(food)) throw new Command.Error("bingo.error.card.food.notfound");
        if (!card.getCalls().add(food)) throw new RuntimeException(str("bingo.error.card.invalid_add_call"));
        if (card.scanWin()) log.info("{} has a winning card", user.getEffectiveName());
        bean(BingoCardRepo.class).save(card);

        return CompletableFuture.supplyAsync(() -> new MessageCreateBuilder().setContent(str("bingo.card.call.added").formatted(food))
                .setFiles(FileUpload.fromData(card.generateImage(), "card.png"))
                .build());
    }

    @Command(privacy = Command.PrivacyLevel.PUBLIC)
    @Description("bingo.command.desc.shout")
    public static CompletableFuture<MessageCreateData> shout(User user) {
        var current = bean(BingoRoundRepo.class).current();
        var card = current.stream()
                .flatMap(round -> round.getCards().stream())
                .filter(it -> it.getPlayer().getUser().equals(user))
                .findAny()
                .filter(BingoCard::scanWin)
                .orElseThrow(() -> new Command.Error("bingo.error.shout.invalid"));

        var round = current.orElseThrow(() -> new Command.Error("bingo.error.round.notfound"));
        round.setEnded(true);
        round.getWinners().add(card.getPlayer());
        bean(BingoRoundRepo.class).save(round);

        return CompletableFuture.supplyAsync(() -> new MessageCreateBuilder().setContent(str("bingo.shout"))
                .setFiles(FileUpload.fromData(card.generateImage(), "card.png"))
                .build());
    }

    @Command
    public static class food {
        @Command(permission = "8589934592")
        @Description("bingo.command.desc.food.list")
        public static CompletableFuture<?> list(MessageChannelUnion channel) {
            return new Command.Manager.Adapter$JDA.PaginatedList<>(channel,
                    () -> of(bean(FoodItemRepo.class).findAll()),
                    new StringBasedComparator<>(FoodItem::getName),
                    item -> new MessageEmbed.Field(item.getName(), item.toString(), false),
                    str("bingo.plural.food"),
                    8) {
                @Override
                protected void finalizeEmbed(EmbedBuilder embed) {
                    embed.setColor(ZitrusfalterApplication.THEME);
                }

                @Override
                protected String pageText() {
                    return super.pageText().replace("Page", str("generic.noun.singular.page"));
                }
            }.resend().submit().thenApply($ -> str("generic.phrase.list_created"));
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description("bingo.command.desc.food.add")
        public static String add(
                @Command.Arg("name") @Description("bingo.command.arg.food.name") String name,
                @Command.Arg(value = "emoji", required = false) @Description("bingo.command.arg.food.emoji") String emoji,
                @Command.Arg(value = "description", required = false) @Description("bingo.command.arg.food.description") String description,
                @Command.Arg(value = "points", required = false) @Description("bingo.command.arg.food.bonus") Double pointBonus,
                @Command.Arg(value = "factor", required = false) @Description("bingo.command.arg.food.factor") Double pointFactor
        ) {
            var foods = bean(FoodItemRepo.class);
            if (foods.existsByName(name)) throw new Command.Error(str("bingo.error.food.exists").formatted(name));
            if (emoji.isBlank() || "food".equals(emoji)) emoji = null;
            if (description.isBlank() || "food".equals(description)) emoji = null;
            var item = new FoodItem(name, emoji);
            if (description != null) item.setDescription(description);
            if (pointBonus != null) item.setPointBonus(pointBonus);
            if (pointFactor != null) item.setPointFactor(pointFactor);
            foods.save(item);
            return str("bingo.food.added") + ":\n- %s".formatted(item);
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description("bingo.command.desc.food.rename")
        public static String rename(
                @Command.Arg(value = "old",
                             autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("bingo.command.arg.food.name.old") String oldName,
                @Command.Arg(value = "new") @Description("bingo.command.arg.food.name.new") String newName
        ) {
            var foods = bean(FoodItemRepo.class);
            var item  = foods.findByName(oldName).orElseThrow(() -> new Command.Error(str("bingo.error.food.notfound").formatted(oldName)));
            item.setName(newName);
            foods.save(item);
            return str("bingo.food.changed") + ":\n- %s".formatted(item);
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description("bingo.command.desc.food.emoji")
        public static String emoji(
                @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("bingo.command.arg.food.name") String name,
                @Command.Arg("emoji") @Description("bingo.command.arg.food.emoji") String emoji
        ) {
            var foods = bean(FoodItemRepo.class);
            var item  = foods.findByName(name).orElseThrow(() -> new Command.Error(str("bingo.error.food.notfound").formatted(name)));
            item.setEmoji(emoji);
            foods.save(item);
            return str("bingo.food.changed") + ":\n- %s".formatted(item);
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description("bingo.command.desc.food.description")
        public static String description(
                @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("bingo.command.arg.food.name") String name,
                @Command.Arg(value = "description", stringMode = StringMode.GREEDY) @Description("bingo.command.arg.food.description") String description
        ) {
            var foods = bean(FoodItemRepo.class);
            var item  = foods.findByName(name).orElseThrow(() -> new Command.Error(str("bingo.error.food.notfound").formatted(name)));
            item.setDescription(description);
            foods.save(item);
            return str("bingo.food.changed") + ":\n- %s".formatted(item);
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description("bingo.command.desc.food.points")
        public static String points(
                @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("bingo.command.arg.food.name") String name,
                @Command.Arg("points") @Description("bingo.command.arg.food.bonus") double points
        ) {
            var foods = bean(FoodItemRepo.class);
            var item  = foods.findByName(name).orElseThrow(() -> new Command.Error(str("bingo.error.food.notfound").formatted(name)));
            item.setPointBonus(points);
            foods.save(item);
            return str("bingo.food.changed") + ":\n- %s".formatted(item);
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description("bingo.command.desc.food.factor")
        public static String factor(
                @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("bingo.command.arg.food.name") String name,
                @Command.Arg("factor") @Description("bingo.command.arg.food.factor") double factor
        ) {
            var foods = bean(FoodItemRepo.class);
            var item  = foods.findByName(name).orElseThrow(() -> new Command.Error(str("bingo.error.food.notfound").formatted(name)));
            item.setPointFactor(factor);
            foods.save(item);
            return str("bingo.food.changed") + ":\n- %s".formatted(item);
        }

        @Command(permission = "8589934592", privacy = Command.PrivacyLevel.PUBLIC)
        @Description("bingo.command.desc.food.delete")
        public static String remove(
                @Command.Arg(value = "name", autoFillProvider = AutoFillProvider.AllFoodNames.class) @Description("bingo.command.arg.food.name") String name
        ) {
            var foods = bean(FoodItemRepo.class);
            if (!foods.existsByName(name)) throw new Command.Error(str("bingo.error.food.notfound").formatted(name));
            foods.deleteByName(name);
            return str("bingo.food.deleted") + ": `%s`".formatted(name);
        }
    }

    @Command
    public static class score {
        @Command(privacy = Command.PrivacyLevel.PUBLIC)
        @Description("bingo.command.desc.score.of")
        public static String of(@Command.Arg("player") @Description("bingo.command.arg.score.player") User target) {
            var players = bean(PlayerRepo.class);
            var idLong  = target.getIdLong();
            return str("bingo.score.player.wins.score").formatted(target.getEffectiveName(),
                    Objects.requireNonNullElse(players.wins(idLong), 0),
                    Objects.requireNonNullElse(players.totalScore(idLong), 0d));
        }
    }
}
