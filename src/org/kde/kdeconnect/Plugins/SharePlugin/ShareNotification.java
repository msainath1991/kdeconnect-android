package org.kde.kdeconnect.Plugins.SharePlugin;

/*
 * Copyright 2017 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect_tp.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

//TODO: Starting API 24 notification title and text are both displayed on 1 line above the progress bar. Because title can be long, the text is often not displayed
class ShareNotification {
    private final NotificationManager notificationManager;
    private final int notificationId;
    private NotificationCompat.Builder builder;
    private final Device device;

    //https://documentation.onesignal.com/docs/android-customizations#section-big-picture
    private static final int bigImageWidth = 1440;
    private static final int bigImageHeight = 720;

    public ShareNotification(Device device) {
        this.device = device;

        notificationId = (int) System.currentTimeMillis();
        notificationManager = (NotificationManager) device.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        builder = new NotificationCompat.Builder(device.getContext(), NotificationHelper.Channels.FILETRANSFER)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setAutoCancel(true)
                .setOngoing(true)
                .setProgress(100, 0, true);
    }

    public void show() {
        NotificationHelper.notifyCompat(notificationManager, notificationId, builder.build());
    }

    public void cancel() {
        notificationManager.cancel(notificationId);
    }

    public int getId() {
        return notificationId;
    }

    public void setTitle(String title) {
        builder.setContentTitle(title);
        builder.setTicker(title);
    }

    public void setProgress(int progress, String progressMessage) {
        builder.setProgress( 100, progress, false);
        builder.setContentText(progressMessage);
    }

    public void setFinished(String message) {
        builder = new NotificationCompat.Builder(device.getContext(), NotificationHelper.Channels.DEFAULT);
        builder.setContentTitle(message)
                .setTicker(message)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setOngoing(false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(device.getContext());
        if (prefs.getBoolean("share_notification_preference", true)) {
            builder.setDefaults(Notification.DEFAULT_ALL);
        }
    }

    public void setURI(Uri destinationUri, String mimeType, String filename) {
        /*
         * We only support file URIs (because sending a content uri to another app does not work for security reasons).
         * In effect, that means only the default download folder currently works.
         *
         * TODO: implement our own content provider (instead of support-v4's FileProvider). It should:
         *  - Proxy to real files (in case of the default download folder)
         *  - Proxy to the underlying content uri (in case of a custom download folder)
         */

        //If it's an image, try to show it in the notification
        if (mimeType.startsWith("image/")) {
            //https://developer.android.com/topic/performance/graphics/load-bitmap
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            try (InputStream decodeBoundsInputStream = device.getContext().getContentResolver().openInputStream(destinationUri);
                 InputStream decodeInputStream = device.getContext().getContentResolver().openInputStream(destinationUri)) {
                BitmapFactory.decodeStream(decodeBoundsInputStream, null, options);

                options.inJustDecodeBounds = false;
                options.inSampleSize = calculateInSampleSize(options, bigImageWidth, bigImageHeight);

                Bitmap image = BitmapFactory.decodeStream(decodeInputStream, null, options);
                if (image != null) {
                    builder.setLargeIcon(image);
                    builder.setStyle(new NotificationCompat.BigPictureStyle()
                            .bigPicture(image));
                }
            } catch (IOException ignored) {
            }
        }

        if (!"file".equals(destinationUri.getScheme())) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        if (Build.VERSION.SDK_INT >= 24) {
            //Nougat and later require "content://" uris instead of "file://" uris
            File file = new File(destinationUri.getPath());
            Uri contentUri = FileProvider.getUriForFile(device.getContext(), "org.kde.kdeconnect_tp.fileprovider", file);
            intent.setDataAndType(contentUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        } else {
            intent.setDataAndType(destinationUri, mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, destinationUri);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                device.getContext(),
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        builder.setContentText(device.getContext().getResources().getString(R.string.received_file_text, filename))
                .setContentIntent(resultPendingIntent);

        shareIntent = Intent.createChooser(shareIntent,
                device.getContext().getString(R.string.share_received_file, destinationUri.getLastPathSegment()));
        PendingIntent sharePendingIntent = PendingIntent.getActivity(device.getContext(), (int) System.currentTimeMillis(),
                shareIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Action.Builder shareAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_share_white, device.getContext().getString(R.string.share), sharePendingIntent);
        builder.addAction(shareAction.build());
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int targetWidth, int targetHeight) {
        int inSampleSize = 1;

        if (options.outHeight > targetHeight || options.outWidth > targetWidth) {
            final int halfHeight = options.outHeight / 2;
            final int halfWidth = options.outWidth / 2;

            while ((halfHeight / inSampleSize) >= targetHeight
                    && (halfWidth / inSampleSize) >= targetWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
