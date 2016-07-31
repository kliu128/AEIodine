package space.potatofrom.aeiodine;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import java.io.IOException;

public class DnsVpnService extends VpnService {
    private Thread thread;
    private int tunnelFd;

    public static DnsVpnService instance = null;
    public static DnsVpnStatus status = DnsVpnStatus.STOPPED;

    public static final String ACTION_STATUS_UPDATE =
            "space.potatofrom.cubic20.DnsVpnService.STATUS_UPDATE";
    public static final String ACTION_LOG_MESSAGE =
            "space.potatofrom.cubic20.DnsVpnService.LOG_MESSAGE";
    public static final String ACTION_STOP =
            "space.potatofrom.cubic20.DnsVpnService.STOP";
    public static final String EXTRA_STATUS = "extra_status";
    public static final String EXTRA_MESSAGE = "extra_message";

    private static final String TAG = "DnsVpnService";
    private static final int NOTIFICATION_ID = 1;

    private static final int RETURN_SUCCESS = 0;
    private static final int RETURN_UNABLE_TO_OPEN_DNS_SOCKET = 1;
    private static final int RETURN_HANDSHAKE_FAILED = 2;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_STOP:
                    stop();
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "This broadcast receiver does not implement action " + intent.getAction());
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STOP);
        registerReceiver(receiver, filter);
    }

    /**
     * Starts iodine and the tunnel
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        changeStatus(DnsVpnStatus.STARTED);
        startForeground(NOTIFICATION_ID, getOngoingNotification());

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                log("Starting...");
                try {
                    SharedPreferences prefs =
                            PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    String tunnelNameserver = "8.8.8.8";
                    String password =
                            prefs.getString(getString(R.string.pref_key_iodine_password), "");
                    String domain =
                            prefs.getString(getString(R.string.pref_key_iodine_ip), "");

                    int returnVal = IodineClient.connect(
                            tunnelNameserver,
                            domain,
                            true,
                            password,
                            200,
                            0);

                    log("Iodine client connect() returned " + returnVal);

                    switch (returnVal) {
                        case RETURN_SUCCESS:
                            log("Successful iodine connection");
                            startTunnel();
                            break;
                        case RETURN_UNABLE_TO_OPEN_DNS_SOCKET:
                            log("Could not open iodine DNS socket");
                            stop();
                            break;
                        case RETURN_HANDSHAKE_FAILED:
                            log("Failed handshake with iodine server");
                            stop();
                            break;
                        default:
                            log("Unknown return value??");
                            stop();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, TAG);

        thread.start();
        return START_NOT_STICKY;
    }

    private void stop() {
        log("Stopping from status " + status);

        if (    status == DnsVpnStatus.STARTED ||
                status == DnsVpnStatus.CONNECTED) {
            thread.interrupt();
            unregisterReceiver(receiver);
        }
        if (status == DnsVpnStatus.CONNECTED) {
            IodineClient.tunnelInterrupt();
            try {
                ParcelFileDescriptor.adoptFd(tunnelFd).close();
            } catch (IOException e) {
            }
        }
        if (status == DnsVpnStatus.STOPPED) {
            throw new IllegalStateException("Attempted to stop " + TAG + " when it was not active");
        } else {
            changeStatus(DnsVpnStatus.STOPPED);
            stopSelf();
        }
    }

    private void log(String text) {
        sendBroadcast(new Intent(ACTION_LOG_MESSAGE).putExtra(EXTRA_MESSAGE, text + "\n"));
    }

    private void changeStatus(DnsVpnStatus status) {
        DnsVpnService.status = status;
        sendBroadcast(new Intent(ACTION_STATUS_UPDATE).putExtra(EXTRA_STATUS, status));
    }

    private void startTunnel() throws IOException {
        VpnService.Builder vpnBuilder = new VpnService.Builder();
        vpnBuilder.setSession(TAG);

        String clientIp = IodineClient.getIp();
        int netBits = IodineClient.getNetbits();
        int mtu = IodineClient.getMtu();
        String dnsServer = IodineClient.getPropertyNetDns1();
        log("Build tunnel for configuration: " +
                "clientIp=" + clientIp + ", " +
                "netbits=" + netBits + ", " +
                "mtu=" + mtu + ", " +
                "dnsServer=" + dnsServer);

        vpnBuilder.addDnsServer(dnsServer);
        vpnBuilder.addAddress(clientIp, netBits);
        vpnBuilder.addRoute("0.0.0.0", 0);
        vpnBuilder.setMtu(mtu);

        ParcelFileDescriptor parcelFD = vpnBuilder.establish();
        protect(IodineClient.getDnsFd());

        tunnelFd = parcelFD.detachFd();

        changeStatus(DnsVpnStatus.CONNECTED);

        IodineClient.tunnel(tunnelFd);

        if (status != DnsVpnStatus.STOPPED) {
            // Only stop again if not stopped (it might be stopped already if
            // the user manually disconnects)
            log("Stopped without manual disconnect; iodine crashed?");
            stop();
        }
    }

    private Notification getOngoingNotification() {
        return new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.vpn_notification_title))
                .setContentText(getString(R.string.vpn_notification_text))
                .setContentIntent(PendingIntent.getActivity(
                        this,
                        0,
                        new Intent(this, MainActivity.class),
                        0))
                .setOngoing(true)
                .build();
    }
}