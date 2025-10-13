import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class FrameBufferWindow extends JPanel {

    private final JFrame window;
    private final BufferedImage framebuffer;
    private final int width;
    private final int height;

    private final byte bitDepth;
    private final int colourType;

    public FrameBufferWindow(String name, int width, int height, byte bitDepth, int colourType) {
        window = new JFrame(name);
        framebuffer = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        setPreferredSize(new Dimension(width, height));

        this.width = width;
        this.height = height;
        this.bitDepth = bitDepth;
        this.colourType = colourType;

        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.add(this);
        window.pack();
        window.setVisible(true);
    }

    public FrameBufferWindow(String name, int width, int height, BufferedImage buffer, byte bitDepth, int colourType) {
        window = new JFrame(name);
        framebuffer = buffer;
        this.bitDepth = bitDepth;
        this.colourType = colourType;
        setPreferredSize(new Dimension(width, height));

        this.width = width;
        this.height = height;

        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.add(this);
        window.pack();
        window.setVisible(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(framebuffer, 0, 0, null);
    }

    public void setPixel(int x, int y, int colour) {
        framebuffer.setRGB(x, y, colour);
    }

    public void setLine(int line, byte[] row) throws Exception {

        int x = 0;
        if (bitDepth == 8) {
            if (colourType == 2) { // RGB
                for (int i = 0; i < row.length; i += 3) {
                    int r = row[i] & 0xFF;
                    int g = row[i + 1] & 0xFF;
                    int b = row[i + 2] & 0xFF;
                    int argb = (0xFF << 24) | (r << 16) | (g << 8) | b;
                    framebuffer.setRGB(x++, line, argb);
                }
            } else if (colourType == 6) { // RGBA
                for (int i = 0; i < row.length; i += 4) {
                    int r = row[i] & 0xFF;
                    int g = row[i + 1] & 0xFF;
                    int b = row[i + 2] & 0xFF;
                    int a = row[i + 3] & 0xFF;
                    int argb = (a << 24) | (r << 16) | (g << 8) | b;
                    framebuffer.setRGB(x++, line, argb);
                }
            } else if (colourType == 0) { // grayscale
                for (int i = 0; i < row.length; i++) {
                    int v = row[i] & 0xFF;
                    int argb = (0xFF << 24) | (v << 16) | (v << 8) | v;
                    framebuffer.setRGB(x++, line, argb);
                }
            } else {
                throw new UnsupportedOperationException("Colour type " + colourType + " not implemented for decode");
            }
        } else if (bitDepth == 16) {
            // samples are two bytes (MSB first). Convert 16->8 by taking MSB
            if (colourType == 2) {
                for (int i = 0; i < row.length; i += 6) {
                    int r = row[i] & 0xFF;      // MSB
                    int g = row[i + 2] & 0xFF;
                    int b = row[i + 4] & 0xFF;
                    int argb = (0xFF << 24) | (r << 16) | (g << 8) | b;
                    framebuffer.setRGB(x++, line, argb);
                }
            } else if (colourType == 6) {
                for (int i = 0; i < row.length; i += 8) {
                    int r = row[i] & 0xFF;
                    int g = row[i + 2] & 0xFF;
                    int b = row[i + 4] & 0xFF;
                    int a = row[i + 6] & 0xFF;
                    int argb = (a << 24) | (r << 16) | (g << 8) | b;
                    framebuffer.setRGB(x++, line, argb);
                }
            } else {
                throw new UnsupportedOperationException("16-bit + colour type " + colourType + " not implemented");
            }
        } else {
            throw new UnsupportedOperationException("Unsupported bit depth: " + bitDepth);
        }
    }
}
