package de.kaleidox.zitrusfalter;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.comroid.api.data.seri.DataNode;

@Data
@NoArgsConstructor
public class BotConfig implements DataNode {
    String token;
    final Database database = new Database();

    @Data@NoArgsConstructor
    public static class Database {
        String uri;
        String username;
        String password;
    }
}
