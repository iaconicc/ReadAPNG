import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import com.jcraft.jzlib.ZInputStream;

public class PNG_READER {

    private static final Logger LOGGER = Logger.getLogger(PNG_READER.class.getName());
    private static final byte[] HeaderSignature = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private DataInputStream stream;
    private int width;
    private int height;
    private byte bitDepth;
    private byte colourType;
    private byte interlace;
    private DataInputStream pixelStream;

    public PNG_READER(String file) throws IOException {
        try {
            stream = new DataInputStream(new FileInputStream(file));

            //check if png signature is correct
            byte[] header = new byte[8];
            stream.readFully(header);
            if (!Arrays.equals(HeaderSignature, header)) {
                throw new Exception("File is not a png or signature corrupted");
            }

            //read in metadata chunk which should follow the header
            PngChunkHeader IHDR_Chunk_Start = new PngChunkHeader();

            //check if the chunk is type IHDR i.e. metadata chunk
            if (!(IHDR_Chunk_Start.getType().equals("IHDR") && IHDR_Chunk_Start.getLength() == 13)) {
                throw new Exception("PNG may be corrupted as the metadata could not be found");
            }

            //start reading chunk metadata (C/C++ data structures are better for this shame java couldn't be like c++)
            parseIHDRData();

            /*it's not compliant, but I skip every chunk till the IDAT assume PLTE is not needed including the critical chunks
            as far as I know koikatsu is the only app that uses some of the flexibility of PNG specification
            We'll then just start adding all the IDAT data into an array finally allocate a joined buffer and copy the data into it for decompression
            */
            List<byte[]> dataBytes = new ArrayList<>();

            boolean reachedEND = false;
            while (!reachedEND) {
                PngChunkHeader chunk = new PngChunkHeader();
                if (chunk.getType().equals("IEND")) {
                    reachedEND = true;
                } else if (chunk.getType().equals("IDAT")) {
                    byte[] IDATdata = new byte[chunk.getLength()];
                    stream.readFully(IDATdata);
                    dataBytes.add(IDATdata);
                    stream.skipBytes(4);
                } else {
                    stream.skipBytes(chunk.getLength() + 4);
                }
            }

            //data counting time
            int byteCount = 0;
            for (byte[] data : dataBytes) {
                byteCount += data.length;
            }

            //start the copying proccess
            byte[] ConjointedBytes = new byte[byteCount];
            int offset = 0;
            for (byte[] data : dataBytes) {
                System.arraycopy(data, 0, ConjointedBytes, offset, data.length);
                offset += data.length;
            }

            System.out.printf("Copied out %d IDAT chunks total %dkb.\n", dataBytes.size(), byteCount / 1000);

            // decompress data all at once
            try {
                ByteArrayInputStream in = new ByteArrayInputStream(ConjointedBytes);
                ZInputStream zIn = new ZInputStream(in);
                byte[] uncopmData = zIn.readAllBytes();
                ByteArrayInputStream byteStream = new ByteArrayInputStream(uncopmData);
                pixelStream = new DataInputStream(byteStream);
            } catch (Exception e) {
                LOGGER.severe("Error when decompressing data: " + e.getLocalizedMessage());
                for (StackTraceElement element : e.getStackTrace()) {
                    System.out.println("at " + element.toString());
                }
            }

            stream.close();
        } catch (Exception e) {
            LOGGER.severe("Error when initialising image file: " + e.getLocalizedMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                System.out.println("at " + element.toString());
            }
            return;
        } finally {
            stream.close();
        }

    }

    public void closeStream() throws IOException {
        pixelStream.close();
    }

    private static int paethPredictor(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);

        if (pa <= pb && pa <= pc) return a;
        else if (pb <= pc) return b;
        else return c;
    }

    private static void applyFilter(byte[] currentRow, byte[] prevRow, int bytesPerPixel, byte filterType) {
        final int rowLength = currentRow.length;

        switch (filterType) {
            case 0:
                break;

            //sub
            case 1:
                for (int i = bytesPerPixel; i < rowLength; i++) {
                    currentRow[i] = (byte) ((currentRow[i] + (currentRow[i - bytesPerPixel] & 0xFF)) & 0xFF);
                }
                break;
            case 2: // Up
                if (prevRow != null) {
                    for (int i = 0; i < rowLength; i++) {
                        currentRow[i] = (byte) ((currentRow[i] + (prevRow[i] & 0xFF)) & 0xFF);
                    }
                }
                break;

            case 3: // Average
                for (int i = 0; i < rowLength; i++) {
                    int left = (i >= bytesPerPixel) ? (currentRow[i - bytesPerPixel] & 0xFF) : 0;
                    int up = (prevRow != null) ? (prevRow[i] & 0xFF) : 0;
                    currentRow[i] = (byte) ((currentRow[i] + ((left + up) / 2)) & 0xFF);
                }
                break;

            case 4: // Paeth
                for (int i = 0; i < rowLength; i++) {
                    int left = (i >= bytesPerPixel) ? (currentRow[i - bytesPerPixel] & 0xFF) : 0;
                    int up = (prevRow != null) ? (prevRow[i] & 0xFF) : 0;
                    int upperLeft = (i >= bytesPerPixel && prevRow != null) ? (prevRow[i - bytesPerPixel] & 0xFF) : 0;
                    currentRow[i] = (byte) ((currentRow[i] + paethPredictor(left, up, upperLeft)) & 0xFF);
                }
                break;

            default:
                throw new IllegalArgumentException("Unsupported PNG filter type: " + filterType);
        }

    }

    private int getBytesPerPixel() throws Exception {
        int channels;
        switch (colourType) {
            case 0: // Grayscale
                channels = 1;
                break;
            case 2: // RGB
                channels = 3;
                break;
            case 3: // Indexed color (palette)
                channels = 1; // indices, not true color values
                break;
            case 4: // Grayscale + Alpha
                channels = 2;
                break;
            case 6: // RGBA
                channels = 4;
                break;
            default:
                throw new Exception("Unsupported PNG colour type: " + colourType);
        }

        if (bitDepth != 8 && bitDepth != 16)
            throw new Exception("Unsupported bit depth: " + bitDepth);

        return (bitDepth / 8) * channels;
    }

    private byte[] prevRow = null;

    public BufferedImage decodeToBufferedImage() throws Exception {
        int bpp = getBytesPerPixel(); // channels * (bitDepth/8)
        if (colourType == 3) throw new UnsupportedOperationException("Indexed PNG (PLTE) not supported");
        if (interlace != 0) throw new UnsupportedOperationException("Interlaced PNG not supported");

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            byte[] row = getNextScanline();
            if (row == null) throw new EOFException("Unexpected end of pixel data");

            int x = 0;
            if (bitDepth == 8) {
                if (colourType == 2) { // RGB
                    for (int i = 0; i < row.length; i += 3) {
                        int r = row[i] & 0xFF;
                        int g = row[i+1] & 0xFF;
                        int b = row[i+2] & 0xFF;
                        int argb = (0xFF << 24) | (r << 16) | (g << 8) | b;
                        img.setRGB(x++, y, argb);
                    }
                } else if (colourType == 6) { // RGBA
                    for (int i = 0; i < row.length; i += 4) {
                        int r = row[i] & 0xFF;
                        int g = row[i+1] & 0xFF;
                        int b = row[i+2] & 0xFF;
                        int a = row[i+3] & 0xFF;
                        int argb = (a << 24) | (r << 16) | (g << 8) | b;
                        img.setRGB(x++, y, argb);
                    }
                } else if (colourType == 0) { // grayscale
                    for (int i = 0; i < row.length; i++) {
                        int v = row[i] & 0xFF;
                        int argb = (0xFF << 24) | (v << 16) | (v << 8) | v;
                        img.setRGB(x++, y, argb);
                    }
                } else {
                    throw new UnsupportedOperationException("Colour type " + colourType + " not implemented for decode");
                }
            } else if (bitDepth == 16) {
                // samples are two bytes (MSB first). Convert 16->8 by taking MSB
                if (colourType == 2) {
                    for (int i = 0; i < row.length; i += 6) {
                        int r = row[i] & 0xFF;      // MSB
                        int g = row[i+2] & 0xFF;
                        int b = row[i+4] & 0xFF;
                        int argb = (0xFF << 24) | (r << 16) | (g << 8) | b;
                        img.setRGB(x++, y, argb);
                    }
                } else if (colourType == 6) {
                    for (int i = 0; i < row.length; i += 8) {
                        int r = row[i] & 0xFF;
                        int g = row[i+2] & 0xFF;
                        int b = row[i+4] & 0xFF;
                        int a = row[i+6] & 0xFF;
                        int argb = (a << 24) | (r << 16) | (g << 8) | b;
                        img.setRGB(x++, y, argb);
                    }
                } else {
                    throw new UnsupportedOperationException("16-bit + colour type " + colourType + " not implemented");
                }
            } else {
                throw new UnsupportedOperationException("Unsupported bit depth: " + bitDepth);
            }
        }

        return img;
    }

    public byte[] getNextScanline() throws Exception {
        if (pixelStream == null) throw new IOException("Pixel stream is not initialised (decompression failed)");
        if (pixelStream.available() <= 0) return null;

        int bpp = getBytesPerPixel();
        byte filterType = pixelStream.readByte();
        byte[] scanline = new byte[width * bpp];
        pixelStream.readFully(scanline);

        applyFilter(scanline, prevRow, bpp, filterType);
        prevRow = scanline;
        return scanline;
    }

    public byte getBitDepth() {
        return bitDepth;
    }

    public byte getColourType() {
        return colourType;
    }

    /*private class PngChunk {
        private PngChunkHeader chunkHeader = new PngChunkHeader();

        private byte[] data;

        private PngChunk() throws IOException {
            data = new byte[chunkHeader.getLength()];
            stream.readFully(data);
            if(chunkHeader.getType().equals("IEND")) {
                return;
            }
            stream.skip(4);
        }

        public PngChunkHeader getHeader() {
            return chunkHeader;
        }
    }*/

    private class PngChunkHeader {

        private int length;
        private String type;

        public PngChunkHeader() throws IOException {
            length = stream.readInt();
            byte[] typeBytes = new byte[4];
            stream.readFully(typeBytes);
            type = ReadString(typeBytes, 0, 4);
        }

        public int getLength() {
            return length;
        }

        public String getType() {
            return type;
        }
    }

    public Dimension getDimensions() {
        return new Dimension(width, height);
    }

    private void parseIHDRData() throws IOException {
        width = stream.readInt();
        height = stream.readInt();
        bitDepth = stream.readByte();
        colourType = stream.readByte();
        byte compressionMethod = stream.readByte(); // should be 0
        byte filterMethod = stream.readByte();      // should be 0 (per spec)
        interlace = stream.readByte();              // 0 = none, 1 = Adam7

        if (compressionMethod != 0 || filterMethod != 0) {
            throw new IOException("Unsupported compression/filter method in IHDR");
        }
        if (interlace != 0) {
            throw new IOException("Interlaced PNGs (Adam7) are not supported");
        }

        stream.skipBytes(4);
    }

    private static String ReadString(byte[] bytearray, int offset, int length) {
        try {
            return new String(bytearray, offset, length, "UTF-8");
        } catch (Exception e) {
            LOGGER.severe("Error when reading string: " + e.getLocalizedMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                System.out.println("at " + element.toString());
            }
            return null;
        }

    }

}