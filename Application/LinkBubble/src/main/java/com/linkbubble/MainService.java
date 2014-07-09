package com.linkbubble;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.webkit.WebIconDatabase;

import com.linkbubble.ui.CloseAllBubblesActivity;
import com.linkbubble.ui.HideAllBubblesActivity;
import com.linkbubble.util.Analytics;
import com.linkbubble.util.CrashTracking;

/**
 * Created by gw on 28/08/13.
 */
public class MainService extends Service {

    private static final String BCAST_CONFIGCHANGED = "android.intent.action.CONFIGURATION_CHANGED";
    private boolean mRestoreComplete;

    @Override
    public IBinder onBind(Intent intent) {
       return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        MainController mc = MainController.get();
        if (mc != null && intent != null) {
            String cmd = intent.getStringExtra("cmd");
            if (cmd != null) {
                long urlLoadStartTime = intent.getLongExtra("start_time", System.currentTimeMillis());

                if (cmd.compareTo("open") == 0) {
                    String url = intent.getStringExtra("url");
                    if (url != null) {
                        boolean doLicenseCheck = intent.getBooleanExtra("doLicenseCheck", true);
                        String openedFromAppName = intent.getStringExtra("openedFromAppName");
                        mc.openUrl(url, urlLoadStartTime, true, openedFromAppName, doLicenseCheck);
                    }
                } else if (cmd.compareTo("restore") == 0) {
                    if (!mRestoreComplete) {
                        String [] urls = intent.getStringArrayExtra("urls");
                        if (urls != null) {
                            for (int i = 0; i < urls.length; i++) {
                                String urlAsString = urls[i];
                                if (urlAsString != null && !urlAsString.equals(Constant.WELCOME_MESSAGE_URL)) {
                                    mc.openUrl(urlAsString, urlLoadStartTime, i == urls.length - 1, Analytics.OPENED_URL_FROM_RESTORE);
                                }
                            }
                        }
                        mRestoreComplete = true;
                    }
                }
            }
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {

        mRestoreComplete = false;

        setTheme(android.R.style.Theme_Holo_Light);

        super.onCreate();
        CrashTracking.init(this);

        Intent closeAllIntent = new Intent(this, CloseAllBubblesActivity.class);
        closeAllIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent closeAllPendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), closeAllIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent hideAllIntent = new Intent(this, HideAllBubblesActivity.class);
        hideAllIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent hideAllPendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), hideAllIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder notificationBuilder = new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_stat)
                        .setPriority(Notification.PRIORITY_MIN)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.notification_expand_summary))
                        .addAction(R.drawable.ic_action_cancel_dark, getString(R.string.notification_action_close_all), closeAllPendingIntent)
                        .addAction(R.drawable.ic_action_halt_dark, getString(R.string.notification_action_hide), hideAllPendingIntent);
        startForeground(1, notificationBuilder.build());

        Config.init(this);
        Settings.get().onOrientationChange();

        WebIconDatabase.getInstance().open(getDir("icons", MODE_PRIVATE).getPath());

        MainController.create(this, new MainController.EventHandler() {
                @Override
                public void onDestroy() {
                    Settings.get().saveBubbleRestingPoint();
                    stopSelf();
                }
            });

        //Intent i = new Intent();
        //i.setData(Uri.parse("https://t.co/uxMl3bWtMP"));
        //i.setData(Uri.parse("http://t.co/oOyu7GBZMU"));
        //i.setData(Uri.parse("http://goo.gl/abc57"));
        //i.setData(Uri.parse("https://bitly.com/QtQET"));
        //i.setData(Uri.parse("http://www.duckduckgo.com"));
        //openUrl("https://www.duckduckgo.com");
        //openUrl("http://www.duckduckgo.com", true);
        //openUrl("https://t.co/uxMl3bWtMP", true);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BCAST_CONFIGCHANGED);
        registerReceiver(mBroadcastReceiver, filter);

        registerReceiver(mDialogReceiver, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");

        filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenReceiver, filter);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mScreenReceiver);
        unregisterReceiver(mDialogReceiver);
        unregisterReceiver(mBroadcastReceiver);
        MainController.destroy();
    }

    private static BroadcastReceiver mDialogReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent myIntent) {
            if (myIntent.getAction().equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                MainController.get().onCloseSystemDialogs();
            }
        }
    };

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent myIntent) {
            if ( myIntent.getAction().equals( BCAST_CONFIGCHANGED ) ) {
                MainController.get().onOrientationChanged();
            }
        }
    };

    private static BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MainController.get().updateScreenState(intent.getAction());
        }
    };
}
