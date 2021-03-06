/**
 * Wi-Fi в метро (pw.thedrhax.mosmetro, Moscow Wi-Fi autologin)
 * Copyright © 2015 Dmitry Karikh <the.dr.hax@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pw.thedrhax.mosmetro.authenticator;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Map;

import pw.thedrhax.mosmetro.R;
import pw.thedrhax.mosmetro.activities.CaptchaDialog;
import pw.thedrhax.mosmetro.services.ConnectionService;
import pw.thedrhax.util.Listener;
import pw.thedrhax.util.Notify;
import pw.thedrhax.util.Util;

public class CaptchaRequest {
    private final Listener<Boolean> running = new Listener<>(true);

    public CaptchaRequest setRunningListener(Listener<Boolean> master) {
        running.subscribe(master); return this;
    }

    public Map<String,String> getResult(Context context, Bitmap image) {
        final Map<String,String> result = new HashMap<>();

        Intent captcha_activity = new Intent(context, CaptchaDialog.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("image", Util.bitmapToBase64(image));

        Notify captcha_notify = new Notify(context).id(2)
                .title(context.getString(R.string.notification_captcha))
                .text(context.getString(R.string.notification_captcha_summary))
                .icon(R.drawable.ic_notification_register)
                .onClick(PendingIntent.getActivity(
                        context, 254, captcha_activity, PendingIntent.FLAG_UPDATE_CURRENT
                ))
                .onDelete(PendingIntent.getService(
                        context, 253,
                        new Intent(context, ConnectionService.class).setAction("STOP"),
                        PendingIntent.FLAG_UPDATE_CURRENT
                ));

        boolean auto_activity = context instanceof Activity || PreferenceManager
                .getDefaultSharedPreferences(context).getBoolean("pref_captcha_dialog", true);

        // Asking user to enter the code
        if (auto_activity)
            context.startActivity(captcha_activity);
        else
            captcha_notify.show();

        // Register result receiver
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                result.put("captcha_image", intent.getStringExtra("image"));
                result.put("captcha_code", intent.getStringExtra("value"));
            }
        };
        context.getApplicationContext().registerReceiver(
                receiver, new IntentFilter("pw.thedrhax.mosmetro.event.CAPTCHA_RESULT")
        );

        // Wait for answer
        while (result.get("captcha_code") == null && running.get()) {
            SystemClock.sleep(100);
        }

        // Unregister receiver, close the Activity and remove the Notification
        context.getApplicationContext().unregisterReceiver(receiver);
        if (!running.get() && auto_activity)
            context.startActivity(
                    new Intent(context, CaptchaDialog.class).setAction("STOP")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            );
        captcha_notify.hide();

        return result;
    }
}
