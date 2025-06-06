package de.kaleidox.zitrusfalter.trigger;

import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.intellij.lang.annotations.Language;

import java.util.Optional;

import static org.comroid.api.text.Translation.*;

public class GameSuggestionTriggers extends ListenerAdapter {
    public static final @Language("RegExp") String ANY_TEXT_WITH_URL = "(.|\\n)*\\[?(https?://\\S+\\n?)+(]\\([^\\n]+\\))?(.|\\n)*";
    public static final                     Emoji  THUMBS_UP         = Emoji.fromUnicode("\uD83D\uDC4D");
    public static final                     Emoji  THUMBS_DOWN       = Emoji.fromUnicode("\uD83D\uDC4E");
    public static final                     long   CHANNEL_ID        = 1379940786174689320L;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        var author = event.getAuthor();
        if (event.getChannel().getIdLong() != CHANNEL_ID || event.isFromThread() || author.isBot()) return;

        var message = event.getMessage();
        if (message.getContentDisplay().matches(ANY_TEXT_WITH_URL)) message.addReaction(THUMBS_UP).flatMap($ -> message.addReaction(THUMBS_DOWN)).queue();
        else event.getChannel()
                .asThreadContainer()
                .createThreadChannel(message.getContentStripped(),
                        Optional.ofNullable(message.getMessageReference())
                                .stream()
                                .mapToLong(MessageReference::getMessageIdLong)
                                .findAny()
                                .orElseGet(event::getMessageIdLong))
                .flatMap(thread -> thread.sendMessage(str("trigger.suggestion.threadstart").formatted(author.getAsMention())))
                //.flatMap(msg -> msg.addReaction(Emoji.fromUnicode("⏲️")))
                .queue();
    }
}
