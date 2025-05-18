package de.kaleidox.zitrusfalter.util;

import de.kaleidox.zitrusfalter.entity.FoodItem;
import de.kaleidox.zitrusfalter.repo.FoodItemRepo;
import lombok.Value;
import org.comroid.api.func.util.Command;
import org.comroid.api.func.util.Streams;

import java.util.stream.Stream;

public interface AutoFillProvider {
    @Value
    class FoodByName implements Command.AutoFillProvider {
        @Override
        public Stream<String> autoFill(Command.Usage usage, String argName, String currentValue) {
            return Streams.of(ApplicationContextProvider.bean(FoodItemRepo.class).findAll()).map(FoodItem::getName);
        }
    }
}
