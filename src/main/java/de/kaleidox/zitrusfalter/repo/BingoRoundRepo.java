package de.kaleidox.zitrusfalter.repo;

import de.kaleidox.zitrusfalter.entity.BingoRound;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;

public interface BingoRoundRepo extends CrudRepository<BingoRound, @NotNull Long> {
}
