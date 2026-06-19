package com.codewhale.musicplayer.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

/**
 * Lightweight async image loader for album art.
 * Uses a simple background thread — no heavy library like Glide/Picasso
 * needed for this lightweight player.
 */
public class ImageLoader {

    /**
     * Load album art bytes into an ImageView on a background thread.
     */
    public static void loadAlbumArt(final byte[] artData, final ImageView target,
                                    final int placeholderResId) {
        if (target == null) return;

        if (artData == null || artData.length == 0) {
            if (placeholderResId != 0) {
                target.setImageResource(placeholderResId);
            }
            return;
        }

        target.setTag(artData); // tag to prevent stale loads

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Try full decode first
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(artData, 0, artData.length, opts);

                    // Calculate sample size — target ~256px
                    int sampleSize = 1;
                    int targetSize = 256;
                    if (opts.outWidth > targetSize || opts.outHeight > targetSize) {
                        while ((opts.outWidth / sampleSize) > targetSize * 2
                            || (opts.outHeight / sampleSize) > targetSize * 2) {
                            sampleSize *= 2;
                        }
                    }

                    opts = new BitmapFactory.Options();
                    opts.inSampleSize = sampleSize;
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(artData, 0, artData.length, opts);

                    if (bitmap != null) {
                        target.post(new Runnable() {
                            @Override
                            public void run() {
                                // Only set if the tag still matches (no newer load)
                                if (target.getTag() == artData) {
                                    target.setImageBitmap(bitmap);
                                } else {
                                    bitmap.recycle();
                                }
                            }
                        });
                    } else {
                        setPlaceholder(target, placeholderResId);
                    }
                } catch (Exception e) {
                    setPlaceholder(target, placeholderResId);
                } catch (OutOfMemoryError e) {
                    // Try with higher sample size
                    try {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = 8;
                        final Bitmap bitmap = BitmapFactory.decodeByteArray(artData, 0, artData.length, opts);
                        if (bitmap != null) {
                            target.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (target.getTag() == artData) {
                                        target.setImageBitmap(bitmap);
                                    } else {
                                        bitmap.recycle();
                                    }
                                }
                            });
                        }
                    } catch (Exception ignored) {
                        setPlaceholder(target, placeholderResId);
                    }
                }
            }
        }).start();
    }

    private static void setPlaceholder(final ImageView target, final int resId) {
        if (resId != 0) {
            target.post(new Runnable() {
                @Override
                public void run() {
                    target.setImageResource(resId);
                }
            });
        }
    }
}
