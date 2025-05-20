package de.kaleidox.zitrusfalter.entity;

import de.kaleidox.zitrusfalter.ZitrusfalterApplication;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class BingoCard {
    public static final Vector.N2                       START     = new Vector.N2(380, 800);
    public static final int                             INCREMENT = 320;
    @Id @ManyToOne      BingoRound                      round;
    @ManyToOne          Player                          player;
    @ManyToMany         Map<@NotNull Integer, FoodItem> entries;
    @ManyToMany         Set<FoodItem>                   calls;
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

    private int id(int x, int y) {
        return x + (y * size);
    }

    public InputStream createImage() {
        BufferedImage img;

        // load background
        var bgRes = ZitrusfalterApplication.class.getResource("background.png");
        try {
            img = ImageIO.read(Objects.requireNonNull(bgRes, "Ressource 'background.png' existiert nicht"));
        } catch (Exception e) {
            throw new Command.Error("Hintergrund kann nicht geladen werden", e);
        }

        // create image
        var g2 = img.createGraphics();
        g2.setColor(Color.BLACK);
        try {
            for (var row = 0; row < size; row++) {
                var rowI = START.addi(Vector.UnitY.muli(row * INCREMENT));
                for (var col = 0; col < size; col++) {
                    var cell = rowI.addi(Vector.UnitX.muli(col * INCREMENT));
                    var item = entries.get(id(row, col));
                    g2.drawString(item.toString(), (int) cell.getX(), (int) cell.getY());
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
}
