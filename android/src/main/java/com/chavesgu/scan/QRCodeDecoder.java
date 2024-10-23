package com.chavesgu.scan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.huawei.hms.hmsscankit.ScanUtil;
import com.huawei.hms.ml.scan.HmsScan;
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class QRCodeDecoder {
    private static byte[] yuvs;
    public static int MAX_PICTURE_PIXEL = 1024;  // Tăng kích thước ảnh lên 1024 pixel để giữ chi tiết mã QR
    public static final List<BarcodeFormat> allFormats = new ArrayList<BarcodeFormat>() {{
        add(BarcodeFormat.AZTEC);
        add(BarcodeFormat.CODABAR);
        add(BarcodeFormat.CODE_39);
        add(BarcodeFormat.CODE_93);
        add(BarcodeFormat.CODE_128);
        add(BarcodeFormat.DATA_MATRIX);
        add(BarcodeFormat.EAN_8);
        add(BarcodeFormat.EAN_13);
        add(BarcodeFormat.ITF);
        add(BarcodeFormat.MAXICODE);
        add(BarcodeFormat.PDF_417);
        add(BarcodeFormat.QR_CODE);
        add(BarcodeFormat.RSS_14);
        add(BarcodeFormat.RSS_EXPANDED);
        add(BarcodeFormat.UPC_A);
        add(BarcodeFormat.UPC_E);
        add(BarcodeFormat.UPC_EAN_EXTENSION);
    }};

    public static final Map<DecodeHintType, Object> HINTS = new EnumMap(DecodeHintType.class) {{
        put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        put(DecodeHintType.POSSIBLE_FORMATS, allFormats);
        put(DecodeHintType.CHARACTER_SET, "utf-8");
    }};

    public static void config() {
        // Place for any global configurations (if necessary)
    }

    // Decode QR code from image path
    public static String syncDecodeQRCode(String path) {
        config();
        Bitmap bitmap = pathToBitMap(path, MAX_PICTURE_PIXEL, MAX_PICTURE_PIXEL);
        return decodeQRCodeFromBitmap(bitmap);
    }

    // Decode QR code from a Bitmap object
    public static String syncDecodeQRCode(Bitmap bitmap) {
        config();
        return decodeQRCodeFromBitmap(bitmap);
    }

    // Decode image using ZXing and fallback to HybridBinarizer if necessary
    private static String decodeQRCodeFromBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        byte[] mData = getYUV420sp(width, height, bitmap);

        // Ensure bitmap is recycled to free memory
        bitmap.recycle();

        Result result = decodeImage(mData, width, height);
        if (result != null) return result.getText();
        return null;
    }

    // Decode byte array image using ZXing
    private static Result decodeImage(byte[] data, int width, int height) {
        Result result = null;
        try {
            PlanarYUVLuminanceSource source =
                    new PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
            QRCodeReader reader = new QRCodeReader();
            result = reader.decode(binaryBitmap, HINTS);
        } catch (FormatException | ChecksumException ignored) {
            // You can log these exceptions if needed
        } catch (NotFoundException e) {
            // Fallback to HybridBinarizer for more robust scanning
            PlanarYUVLuminanceSource source =
                    new PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            QRCodeReader reader = new QRCodeReader();
            try {
                result = reader.decode(binaryBitmap, HINTS);
            } catch (NotFoundException | ChecksumException | FormatException ignored) {
                // Log these exceptions if needed
            }
        }
        return result;
    }

    // Convert image path to Bitmap
    private static Bitmap pathToBitMap(String imgPath, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imgPath, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(imgPath, options);
    }

    // Calculate optimal sample size
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    // Convert Bitmap to YUV420sp byte array
    private static byte[] getYUV420sp(int width, int height, Bitmap scaled) {
        int[] argb = new int[width * height];
        scaled.getPixels(argb, 0, width, 0, 0, width, height);

        int byteLength = width * height * 3 / 2;
        if (yuvs == null || yuvs.length < byteLength) {
            yuvs = new byte[byteLength];
        } else {
            Arrays.fill(yuvs, (byte) 0);
        }

        encodeYUV420SP(yuvs, argb, width, height);
        return yuvs;
    }

    // Convert ARGB to YUV420SP format
    private static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;
        int yIndex = 0;
        int uvIndex = frameSize;
        int R, G, B, Y, U, V;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                R = (argb[yIndex] & 0xff0000) >> 16;
                G = (argb[yIndex] & 0xff00) >> 8;
                B = argb[yIndex] & 0xff;

                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                yuv420sp[yIndex++] = (byte) Math.max(0, Math.min(Y, 255));
                if ((j % 2 == 0) && (i % 2 == 0)) {
                    yuv420sp[uvIndex++] = (byte) Math.max(0, Math.min(V, 255));
                    yuv420sp[uvIndex++] = (byte) Math.max(0, Math.min(U, 255));
                }
            }
        }
    }

    // Decode QR code using Huawei ScanKit or fallback to ZXing
    public static String decodeQRCode(Context context, String path) {
        Bitmap bitmap = pathToBitMap(path, MAX_PICTURE_PIXEL, MAX_PICTURE_PIXEL);
        return decodeQRCodeFromBitmap(context, bitmap, path);
    }

    // Decode QR code from bitmap using Huawei ScanKit first, fallback to ZXing
    public static String decodeQRCode(Context context, Bitmap bitmap) {
        return decodeQRCodeFromBitmap(context, bitmap, null);
    }

    // Unified method for handling ScanKit and ZXing
    private static String decodeQRCodeFromBitmap(Context context, Bitmap bitmap, String fallbackPath) {
        if (bitmap == null) return null;

        // Try Huawei ScanKit first
        HmsScanAnalyzerOptions options = new HmsScanAnalyzerOptions.Creator().setPhotoMode(true).create();
        HmsScan[] hmsScans = ScanUtil.decodeWithBitmap(context, bitmap, options);

        if (hmsScans != null && hmsScans.length > 0) {
            return hmsScans[0].getOriginalValue();
        }

        // Fallback to ZXing if ScanKit fails
        return fallbackPath != null ? syncDecodeQRCode(fallbackPath) : syncDecodeQRCode(bitmap);
    }
}

