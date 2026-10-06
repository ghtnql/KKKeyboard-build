import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;

// Run with the desktop JDK: java tools/EncodeCafePreview.java <frames-dir> <output.gif>
class EncodeCafePreview {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected frames directory and output GIF");
        var frames = new File(args[0]).listFiles((dir, name) -> name.matches("frame-\\d{4}\\.png"));
        if (frames == null || frames.length == 0) throw new IllegalArgumentException("No captured frames");
        Arrays.sort(frames, Comparator.comparing(File::getName));
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (var output = new FileImageOutputStream(new File(args[1]))) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int index = 0; index < frames.length; index++) {
                var frame = ImageIO.read(frames[index]);
                var metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(frame), null);
                var format = metadata.getNativeMetadataFormatName();
                var root = (IIOMetadataNode) metadata.getAsTree(format);
                var control = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("delayTime", "5");
                control.setAttribute("disposalMethod", "none");
                if (index == 0) {
                    var extensions = new IIOMetadataNode("ApplicationExtensions");
                    var loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[] {1, 0, 0});
                    extensions.appendChild(loop);
                    root.appendChild(extensions);
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        System.out.println("Encoded " + frames.length + " rendered frames: " + args[1]);
    }
}
