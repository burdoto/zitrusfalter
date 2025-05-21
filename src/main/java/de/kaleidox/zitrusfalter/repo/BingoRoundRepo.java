package de.kaleidox.zitrusfalter.repo;

import de.kaleidox.zitrusfalter.entity.BingoRound;
import org.comroid.api.func.util.Streams;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

public interface BingoRoundRepo extends CrudRepository<BingoRound, @NotNull Long> {
    default Optional<BingoRound> current() {
        return Streams.of(findAll()).max(Comparator.comparingLong(BingoRound::getNumber)).filter(Predicate.not(BingoRound::isEnded));
    }

    default long nextNumber() {
        return Streams.of(findAll()).mapToLong(BingoRound::getNumber).max().orElse(0) + 1;
    }
}
