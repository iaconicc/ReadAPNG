import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Scanner;

import static java.lang.Thread.sleep;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws Exception {

        JFileChooser chooser = new JFileChooser();

        chooser.setDialogTitle("Choose an image file");

        chooser.setFileFilter(new FileNameExtensionFilter(
                "Image Files (PNG)","png"
        ));

        String file;
        int result = chooser.showOpenDialog(null);
        if(result == JFileChooser.APPROVE_OPTION){
            File fileHandle = chooser.getSelectedFile();
            file = fileHandle.getAbsolutePath();
        }else {
            throw new Exception("Unable to get file");
        }

        PNG_READER reader = new PNG_READER(file);
        Dimension windowDimensions = reader.getDimensions();
        BufferedImage img = reader.decodeToBufferedImage();
        FrameBufferWindow window = new FrameBufferWindow("Window", windowDimensions.width, windowDimensions.height, img);

        //uncomment for animation but uncomment the FrameBufferWindow above and img
        /*FrameBufferWindow window = new FrameBufferWindow("Window", windowDimensions.width, windowDimensions.height, reader.getBitDepth(), reader.getColourType());
        for (int y = 0; y < windowDimensions.height; y++) {
            sleep(5);
            window.setLine(y, reader.getNextScanline());
            window.repaint();
        }*/

        reader.closeStream();
    }
}