package de.kaleidox.zitrusfalter.repo;

import de.kaleidox.zitrusfalter.entity.BingoCard;
import de.kaleidox.zitrusfalter.entity.BingoRound;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface BingoCardRepo extends CrudRepository<BingoCard, UUID> {}
