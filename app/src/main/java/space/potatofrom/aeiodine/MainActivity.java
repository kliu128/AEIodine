package space.potatofrom.aeiodine;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private Button connect;
    private Button disconnect;
    private TextView output;
    private TextView status;
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case IodineClient.ACTION_LOG_MESSAGE:
                    String message = intent.getStringExtra(IodineClient.EXTRA_MESSAGE);
                    log(message);
                    break;
                case DnsVpnService.ACTION_LOG_MESSAGE:
                    log("[DnsVpnService] " + intent.getStringExtra(DnsVpnService.EXTRA_MESSAGE));
                    break;
                case DnsVpnService.ACTION_STATUS_UPDATE:
                    updateConnectionUi(
                            (DnsVpnStatus) intent.getSerializableExtra(DnsVpnService.EXTRA_STATUS));
                    break;
            }
        }
    };

    private static final int VPN_PREPARE_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connect = (Button) findViewById(R.id.connect);
        disconnect = (Button) findViewById(R.id.disconnect);
        output = (TextView) findViewById(R.id.output);
        status = (TextView) findViewById(R.id.status);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!areRequiredPrefsSet()) {
            getSetSettingsDialog().show();
        }

        updateConnectionUi(DnsVpnService.status);

        IntentFilter filter = new IntentFilter();
        filter.addAction(IodineClient.ACTION_LOG_MESSAGE);
        filter.addAction(DnsVpnService.ACTION_LOG_MESSAGE);
        filter.addAction(DnsVpnService.ACTION_STATUS_UPDATE);
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(receiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case VPN_PREPARE_REQUEST_CODE:
                startService(new Intent(this, DnsVpnService.class));
                break;
        }
    }

    private void updateConnectionUi(DnsVpnStatus status) {
        switch (status) {
            case STARTED:
                connect.setEnabled(false);
                disconnect.setEnabled(false);
                this.status.setText(R.string.main_status_started);
                break;
            case CONNECTED:
                connect.setEnabled(false);
                disconnect.setEnabled(true);
                this.status.setText(R.string.main_status_connected);
                break;
            case STOPPED:
                connect.setEnabled(true);
                disconnect.setEnabled(false);
                this.status.setText(R.string.main_status_stopped);
                break;
            default:
                throw new UnsupportedOperationException("Unknown vpn status??");
        }
    }

    private Dialog getSetSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (prefs.getBoolean(
                getString(R.string.pref_key_displayed_set_settings_dialog_before), false)) {
            // Already opened dialog before, they probably forgot something?
            builder.setMessage(R.string.dialog_set_settings_message_2);
        } else {
            // First time opening dialog
            builder.setMessage(R.string.dialog_set_settings_message_1);
        }

        builder.setPositiveButton(
                R.string.dialog_set_settings_open_settings,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // The dialog has been opened and used! Set preference.
                        SharedPreferences.Editor prefEditor = prefs.edit();
                        prefEditor.putBoolean(
                                getString(R.string.pref_key_displayed_set_settings_dialog_before),
                                true);
                        prefEditor.apply();

                        startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                    }
                });

        return builder.create();
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean areRequiredPrefsSet() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String iodineIp = prefs.getString(getString(R.string.pref_key_iodine_ip), null);
        boolean iodineUsePassword =
                prefs.getBoolean(getString(R.string.pref_key_iodine_use_password), false);
        String iodinePassword = prefs.getString(getString(R.string.pref_key_iodine_password), null);

        // Essentially, make sure these aren't null or empty
        return  iodineIp != null && !iodineIp.isEmpty() &&
                (iodineUsePassword ? (iodinePassword != null && !iodinePassword.isEmpty()) : true);
    }

    private void logLine(@StringRes int resId) {
        logLine(getString(resId));
    }

    private void logLine(String text) {
        log(text + "\n");
    }

    private void log(@StringRes int resId) {
        log(getString(resId));
    }

    private void log(String text) {
        output.append(text);
    }

    public void connect(View view) {
        // Load up preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        output.setText(""); // Clear output

        logLine(R.string.log_starting_iodine);

        Intent prepare = VpnService.prepare(getApplicationContext());
        if (prepare != null) {
            startActivityForResult(prepare, VPN_PREPARE_REQUEST_CODE);
        } else  {
            // User has already been asked
            onActivityResult(VPN_PREPARE_REQUEST_CODE, RESULT_OK, null);
        }
    }

    public void disconnect(View view) {
        sendBroadcast(new Intent(DnsVpnService.ACTION_STOP));
    }
}
