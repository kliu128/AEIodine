package space.potatofrom.aeiodine;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class DnsVpnService extends VpnService {
    private Thread thread;

    public static DnsVpnService instance = null;
    public static boolean isRunning = false;

    private static final String TAG = "DnsVpnService";
    private static final int NOTIFICATION_ID = 1;

    private static final int RETURN_SUCCESS = 0;
    private static final int RETURN_UNABLE_TO_OPEN_DNS_SOCKET = 1;
    private static final int RETURN_HANDSHAKE_FAILED = 2;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;
    }

    @Override
    public void onDestroy() {
        if (isRunning) {
            thread.interrupt();
            IodineClient.tunnelInterrupt();
            isRunning = false;
        }
        super.onDestroy();
    }

    /**
     * Starts iodine and the tunnel
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        startForeground(NOTIFICATION_ID, getOngoingNotification());

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs =
                            PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    String tunnelNameserver = IodineClient.getPropertyNetDns1();
                    String password =
                            prefs.getString(getString(R.string.pref_key_iodine_password), "");
                    String domain =
                            prefs.getString(getString(R.string.pref_key_iodine_ip), "");

                    int returnVal = IodineClient.connect(
                            tunnelNameserver,
                            domain,
                            false,
                            true,
                            password,
                            200,
                            200);

                    switch (returnVal) {
                        case RETURN_SUCCESS:
                            Log.d(TAG, "Successful iodine connection");
                            startTunnel();
                            break;
                        case RETURN_UNABLE_TO_OPEN_DNS_SOCKET:
                            stopSelf();
                            throw new SocketException("Could not open iodine dns socket");
                        case RETURN_HANDSHAKE_FAILED:
                            stopSelf();
                            throw new ConnectException("Failed handshake with iodine server");
                        default:
                            stopSelf();
                            throw new UnknownError("??? " + returnVal);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, TAG);

        thread.start();
        return START_STICKY;
    }

    private void startTunnel() throws IOException {
        VpnService.Builder builder = new VpnService.Builder();
        builder.setSession(TAG);

        String hostIp = IodineClient.getIp();
        int netBits = IodineClient.getNetbits();
        int mtu = IodineClient.getMtu();
        Log.d(TAG, "Build tunnel for configuration: hostIp=" + hostIp + " netbits=" + netBits + " mtu=" + mtu);

        String[] hostIpBytesString = hostIp.split("\\.");
        if (hostIpBytesString.length != 4) {
            throw new InvalidServerIpException("Server sent invalid IP");
        }
        byte[] hostIpBytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            try {
                int integer = Integer.parseInt(hostIpBytesString[i]);
                hostIpBytes[i] = (byte) (integer);
            } catch (NumberFormatException e) {
                throw new InvalidServerIpException("Server sent invalid IP", e);
            }
        }

        InetAddress hostAddress;
        try {
            hostAddress = InetAddress.getByAddress(hostIpBytes);
        } catch (UnknownHostException e) {
            throw new InvalidServerIpException("Server sent invalid IP", e);
        }

        builder.addDnsServer("8.8.8.8");
        builder.addDnsServer("8.8.4.4");
        builder.addAddress(hostAddress, netBits);
        builder.addRoute("0.0.0.0", 0);
        builder.setMtu(mtu);

        ParcelFileDescriptor parcelFD = builder.establish();
        protect(IodineClient.getDnsFd());

        int tunFd = parcelFD.detachFd();

        IodineClient.tunnel(tunFd);
        try {
            ParcelFileDescriptor.adoptFd(tunFd).close();
        } catch (IOException e) {
            throw new IOException(
                    "Failed to close fd after tunnel exited");
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