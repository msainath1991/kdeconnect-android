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
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.NotificationHelper;
import org.kde.kdeconnect_tp.R;

public class ShareNotification {

    private final String filename;
    private NotificationManager notificationManager;
    private int notificationId;
    private NotificationCompat.Builder builder;
    private Device device;

    public ShareNotification(Device device, String filename) {
        this.device = device;
        this.filename = filename;
        notificationId = (int) System.currentTimeMillis();
        notificationManager = (NotificationManager) device.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        builder = new NotificationCompat.Builder(device.getContext())
                .setContentTitle(device.getContext().getResources().getString(R.string.incoming_file_title, device.getName()))
                .setContentText(device.getContext().getResources().getString(R.string.incoming_file_text, filename))
                .setTicker(device.getContext().getResources().getString(R.string.incoming_file_title, device.getName()))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setAutoCancel(true)
                .setOngoing(true)
                .setProgress(100, 0, true);
    }

    public void show() {
        NotificationHelper.notifyCompat(notificationManager, notificationId, builder.build());
    }

    public int getId() {
        return notificationId;
    }

    public void setProgress(int progress) {
        builder.setProgress(100, progress, false)
        .setContentTitle(device.getContext().getResources().getString(R.string.incoming_file_title, device.getName())+" ("+progress+"%)");
    }

    public void setFinished(boolean success) {
        String message = success ? device.getContext().getResources().getString(R.string.received_file_title, device.getName()) : device.getContext().getResources().getString(R.string.received_file_fail_title, device.getName());
        builder = new NotificationCompat.Builder(device.getContext());
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

    public void setURI(Uri destinationUri, String mimeType) {
        // Nougat requires share:// URIs instead of file:// URIs
        // TODO use FileProvider for >=Nougat
        if (Build.VERSION.SDK_INT < 24) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(destinationUri, mimeType);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(device.getContext());
            stackBuilder.addNextIntent(intent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentText(device.getContext().getResources().getString(R.string.received_file_text, filename))
                    .setContentIntent(resultPendingIntent);
        }
    }
}