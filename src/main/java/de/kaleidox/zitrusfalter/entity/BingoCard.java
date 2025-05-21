package de.kaleidox.zitrusfalter.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.Polyfill;
import org.comroid.api.data.Vector;
import org.comroid.api.func.util.Command;
import org.comroid.api.func.util.Stopwatch;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Set;

@Data
@Slf4j
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class BingoCard {
    public static final Vector.N2 START          = new Vector.N2(380, 490);
    public static final int       INCREMENT      = 320;
    public static final URL       BACKGROUND_URL = Polyfill.url("https://github.com/burdoto/zitrusfalter/blob/main/assets/background.png?raw=true");
    public static final File      BACKGROUND_CACHE;

    static {
        var stopwatch = Stopwatch.start(BACKGROUND_URL);
        try {
            BACKGROUND_CACHE = File.createTempFile("background", ".png");
            BACKGROUND_CACHE.deleteOnExit();

            try (var resource = BACKGROUND_URL.openStream(); var fos = new FileOutputStream(BACKGROUND_CACHE)) {
                resource.transferTo(fos);
            } catch (Exception e) {
                throw new Command.Error("Hintergrund kann nicht geladen werden", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create temp file", e);
        }

        log.info("Background image cached in {}ms at {}", stopwatch.stop().toMillis(), BACKGROUND_CACHE.getAbsolutePath());
    }

    @Id @ManyToOne BingoRound                      round;
    @ManyToOne     Player                          player;
    @ManyToMany    Map<@NotNull Integer, FoodItem> entries;
    @ManyToMany    Set<FoodItem>                   calls;
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

    public InputStream createImage() {
        BufferedImage img;

        // load background
        try (var fis = new FileInputStream(BACKGROUND_CACHE)) {
            img = ImageIO.read(fis);
        } catch (Exception e) {
            throw new Command.Error("Hintergrund kann nicht geladen werden", e);
        }

        // create image
        var g2 = img.createGraphics();
        g2.setColor(Color.BLACK);
        g2.setFont(new Font(g2.getFont().getName(), Font.PLAIN, 38));
        var metrics = g2.getFontMetrics();

        try {
            for (var y = 0; y < size; y++) {
                var left = START.addi(Vector.UnitY.muli(y * INCREMENT));
                for (var x = 0; x < size; x++) {
                    var cell = left.addi(Vector.UnitX.muli(x * INCREMENT));
                    var item = entries.get(id(x, y));
                    if (item == null) continue;

                    var str    = item.getName();
                    var offset = metrics.stringWidth(str) / 2;
                    g2.drawString(str, (int) cell.getX() - offset, (int) cell.getY());
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
