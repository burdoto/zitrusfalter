package de.kaleidox.zitrusfalter.entity;

import de.kaleidox.zitrusfalter.ZitrusfalterApplication;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.data.Vector;
import org.comroid.api.func.util.Command;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Slf4j
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class BingoCard {
    public static final Vector.N2 START     = new Vector.N2(380, 490);
    public static final int       INCREMENT = 320;
    public static final String    CROSS     = "❌";

    @Id                                  UUID                            id      = UUID.randomUUID();
    @ManyToOne                           Player                          player;
    @ManyToMany(fetch = FetchType.EAGER) Map<@NotNull Integer, FoodItem> entries = new ConcurrentHashMap<>();
    @ManyToMany(fetch = FetchType.EAGER) Set<FoodItem>                   calls   = new HashSet<>();
    int size = 5;

    public boolean scanWin() {
        // check columns
        nextRow:
        for (var x = 0; x < size; x++) {
            for (var y = 0; y < size; y++)
                if (!calls.contains(entries.getOrDefault(id(x, y), null))) continue nextRow;
            return true;
        }

        // check rows
        nextCol:
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++)
                if (!calls.contains(entries.getOrDefault(id(x, y), null))) continue nextCol;
            return true;
        }

        // check diagonals
        for (var i = 0; i < size; i++) {
            if (i == Math.ceil(size / 2f)) continue;
            if (!calls.contains(entries.getOrDefault(id(i, i), null))) continue;
            if (!calls.contains(entries.getOrDefault(id(i, size - i - 1), null))) continue;
            return true;
        }

        return false;
    }

    public InputStream generateImage() {
        BufferedImage img;

        // load background
        try (var resource = ZitrusfalterApplication.class.getResourceAsStream("/assets/background.png")) {
            if (resource == null) throw new IllegalStateException("Background resource not found");
            img = ImageIO.read(resource);
        } catch (Exception e) {
            throw new Command.Error("Hintergrund kann nicht geladen werden", e);
        }

        // create image
        var g2        = img.createGraphics();
        var fontText  = new Font(g2.getFont().getName(), Font.PLAIN, 38);
        var fontCross = new Font(g2.getFont().getName(), Font.PLAIN, 180);

        try {
            for (var y = 0; y < size; y++) {
                var left = START.addi(Vector.UnitY.muli(y * INCREMENT));
                for (var x = 0; x < size; x++) {
                    var cell = left.addi(Vector.UnitX.muli(x * INCREMENT));
                    var item = entries.get(id(x, y));
                    if (item == null) continue;

                    // draw entry names
                    var str = item.getName();
                    g2.setFont(fontText);
                    var metrics = g2.getFontMetrics();
                    var offset  = metrics.stringWidth(str) / 2;
                    g2.setColor(Color.BLACK);
                    g2.drawString(str, (int) cell.getX() - offset, (int) cell.getY());

                    // cross out called entries
                    if (!calls.contains(item)) continue;
                    g2.setFont(fontCross);
                    metrics = g2.getFontMetrics();
                    offset  = metrics.stringWidth(CROSS) / 2;
                    g2.setColor(Color.RED);
                    g2.drawString(CROSS, (int) cell.getX() - offset, (int) cell.getY() + 55);
                }
            }
        } finally {
            g2.dispose();
        }

        // export image
        byte[] buf;
        try (var baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            buf = baos.toByteArray();
        } catch (IOException e) {
            throw new Command.Error(e);
        }

        return new ByteArrayInputStream(buf);
    }

    private int id(int x, int y) {
        return x + (y * size);
    }
}
