package org.jcodec.api;

import org.jcodec.Utils;
import org.jcodec.codecs.h264.io.model.Frame;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.SeekableDemuxerTrack;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.IOUtils;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture8Bit;
import org.jcodec.common.model.Rect;
import org.jcodec.scale.AWTUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.jcodec.Utils.picturesRoughlyEqual;

public class FrameGrabTest {
    private static final String SEQ_1_MP4 = "src/test/resources/video/seq_h264_1.mp4";
    private static final String SEQ_2_MP4 = "src/test/resources/video/seq_h264_2.mp4";
    private static final String SEQ_3_MP4 = "src/test/resources/video/seq_h264_3.mp4";

    private static final String SEQ_1_YUV = "src/test/resources/video/seq_1.yuv";
    private static final String SEQ_2_YUV = "src/test/resources/video/seq_2.yuv";
    private static final String SEQ_3_YUV = "src/test/resources/video/seq_3.yuv";

    private boolean saveImages = false;

    @Test
    public void testFrameGrab() throws IOException, JCodecException {
        compareOneSequence(SEQ_1_MP4, SEQ_1_YUV);
        compareOneSequence(SEQ_2_MP4, SEQ_2_YUV);
        compareOneSequence(SEQ_3_MP4, SEQ_3_YUV);
    }

    private void compareOneSequence(String compressed, String uncompressed) throws FileNotFoundException, IOException,
            JCodecException {
        FileChannelWrapper ch1 = null, ch2 = null;
        try {
            ch1 = NIOUtils.readableChannel(new File(compressed));
            ch2 = NIOUtils.readableChannel(new File(uncompressed));
            FrameGrab8Bit frameGrab1 = FrameGrab8Bit.createFrameGrab8Bit(ch1);

            PictureWithMetadata8Bit fr1;
            List<PictureWithMetadata8Bit> decoded = new ArrayList<PictureWithMetadata8Bit>();
            do {
                fr1 = frameGrab1.getNativeFrameWithMetadata();
                if (fr1 == null)
                    break;
                fr1 = new PictureWithMetadata8Bit(fr1.getPicture().cloneCropped(), fr1.getTimestamp(),
                        fr1.getDuration());
                decoded.add(fr1);
            } while (fr1 != null);

            Collections.sort(decoded, new Comparator<PictureWithMetadata8Bit>() {
                @Override
                public int compare(PictureWithMetadata8Bit o1, PictureWithMetadata8Bit o2) {
                    return o1.getTimestamp() < o2.getTimestamp() ? -1
                            : (o1.getTimestamp() == o2.getTimestamp() ? 0 : 1);
                }
            });

            for (PictureWithMetadata8Bit pic : decoded) {
                Frame frame = (Frame) pic.getPicture();
                Picture8Bit fr2 = Utils.readYuvFrame(ch2, frame.getWidth(), frame.getHeight());

                if (saveImages && Utils.maxDiff(frame, fr2) > 0) {
                    System.out.println(String.format("POC: %d, pts: %f", frame.getPOC(), pic.getTimestamp()));
                    Utils.saveImage(fr2, "png", String.format("/tmp/orig_%s_%f.%s", new File(compressed).getName(),
                            pic.getTimestamp(), "png"));
                    Utils.saveImage(frame, "png", String.format("/tmp/dec_%s_%f.%s", new File(compressed).getName(),
                            pic.getTimestamp(), "png"));

                    Utils.saveImage(Utils.diff(frame, fr2, 10), "png", String.format("/tmp/diff_%s_%f.%s", new File(
                            compressed).getName(), pic.getTimestamp(), "png"));
                }

                Assert.assertTrue(
                        String.format("Seq %s, poc %d, pts %f", compressed, frame.getPOC(), pic.getTimestamp()),
                        picturesRoughlyEqual(frame, fr2, 50));
            }
        } finally {
            IOUtils.closeQuietly(ch1);
            IOUtils.closeQuietly(ch2);
        }
    }

    @Test
    public void testFrameGrabAndEncode() throws Exception {
        FileChannelWrapper channel = NIOUtils.readableFileChannel("/Users/zach/Desktop/liushen.mp4");

        FrameGrab8Bit grab8Bit = FrameGrab8Bit.createFrameGrab8Bit(channel);

        SeekableDemuxerTrack videoTrack = grab8Bit.getVideoTrack();

        DemuxerTrackMeta meta = videoTrack.getMeta();
        double totalDuration = meta.getTotalDuration();
        int totalFrames = meta.getTotalFrames();
        System.out.println(totalFrames + "-" + totalDuration + "-" + meta.getType());

        PictureWithMetadata8Bit metaWithPic = grab8Bit.getNativeFrameWithMetadata();
        System.out.println(metaWithPic.getDuration() + ":" + metaWithPic.getTimestamp());
        SequenceEncoder8Bit encoder = SequenceEncoder8Bit.createSequenceEncoder8Bit(new File("/Users/zach/Desktop/liushen2.mp4"), 20);

        Picture8Bit pic = metaWithPic.getPicture();

        int frameCount = 30;
        int frameOff = totalFrames / frameCount;

        int currentFrame = 0;
        long start = System.currentTimeMillis();

        while (pic != null && currentFrame <= totalFrames) {
//            encoder.encodeNativeFrame(Picture8Bit.createCropped(200, 200, ColorSpace.RGB, new Rect(640, 360, 200, 200)));
            if (currentFrame % frameOff == 0) {
                pic.setCrop(new Rect(0, 0, 640, 640));
                encoder.encodeNativeFrame(AWTUtil.fromBufferedImageRGB8Bit(AWTUtil.toBufferedImage8Bit(pic)));
                System.out.println(System.currentTimeMillis() + " currentFrame:" + currentFrame);
            }
//            grab8Bit.seekToFrameSloppy(currentFrame);
            pic = grab8Bit.getNativeFrame();
            currentFrame++;
        }

        System.out.println(((double) (System.currentTimeMillis() - start)) / 1000);
        encoder.finish();
    }

    @Test
    public void testFrameGrab2() throws Exception {
        FileChannelWrapper channel = NIOUtils.readableFileChannel("/Users/zach/Desktop/liushen2.mp4");

        FrameGrab8Bit grab8Bit = FrameGrab8Bit.createFrameGrab8Bit(channel);

        SeekableDemuxerTrack videoTrack = grab8Bit.getVideoTrack();

        DemuxerTrackMeta meta = videoTrack.getMeta();
        double totalDuration = meta.getTotalDuration();
        int totalFrames = meta.getTotalFrames();

        System.out.println(totalFrames + "-" + totalDuration + "-" + meta.getType());
    }
}