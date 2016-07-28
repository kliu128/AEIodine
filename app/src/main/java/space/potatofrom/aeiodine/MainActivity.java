package space.potatofrom.aeiodine;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private TextView output;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        output = (TextView) findViewById(R.id.output);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!areRequiredPrefsSet()) {
            getSetSettingsDialog().show();
        }
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
        String sshIp = prefs.getString(getString(R.string.pref_key_ssh_ip), null);
        String sshPort = prefs.getString(getString(R.string.pref_key_ssh_port), null);

        // Essentially, make sure these aren't null or empty
        return  iodineIp != null && !iodineIp.isEmpty() &&
                (iodineUsePassword ? (iodinePassword != null && !iodinePassword.isEmpty()) : true) &&
                sshIp != null && !sshIp.isEmpty() &&
                sshPort != null && !sshPort.isEmpty();
    }

    private void startLog(final InputStream stream) throws IOException {
        Thread logThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    int c;
                    while ((c = reader.read()) != -1) {
                        final char ch = (char)c;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                output.append(Character.toString(ch));
                            }
                        });
                    }
                    reader.close();
                } catch (IOException e) { }
            }
        });
        logThread.setPriority(Thread.MIN_PRIORITY);
        logThread.start();
    }

    private void log(@StringRes int resId) {
        log(getString(resId));
    }

    private void log(String text) {
        output.append(text + "\n");
    }

    public void connect(View view) {
        // Load up preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String iodineIp = prefs.getString(getString(R.string.pref_key_iodine_ip), null);
        boolean iodineUsePassword =
                prefs.getBoolean(getString(R.string.pref_key_iodine_use_password), false);
        String iodinePassword = prefs.getString(getString(R.string.pref_key_iodine_password), null);
        String iodineExtraParams =
                prefs.getString(getString(R.string.pref_key_iodine_extra_params), "");

        String sshIp = prefs.getString(getString(R.string.pref_key_ssh_ip), null);
        int sshPort = Integer.valueOf(prefs.getString(getString(R.string.pref_key_ssh_port), null));
        boolean sshUseCompression =
                prefs.getBoolean(getString(R.string.pref_key_ssh_use_compression), false);

        output.setText(""); // Clear output

        Process iodineProcess = null;
        Session sshSession = null;

        try {
            // Set up Iodine client
            log(R.string.log_starting_iodine);

            Set<IodineArgument> args = new HashSet<>();
            args.add(new IodineArgument(IodineFlags.LAZY_MODE, "1"));
            if (iodineUsePassword) {
                args.add(new IodineArgument(IodineFlags.AUTHENTICATION_PASSWORD, iodinePassword));
            }

            IodineClient iodineClient = new IodineClient(args, iodineExtraParams, iodineIp, this);
            iodineProcess = iodineClient.start();
            startLog(iodineProcess.getInputStream());

            // Set up SSH tunnel
            sshSession = new JSch().getSession(sshIp);
            sshSession.setPort(sshPort);

            if (sshUseCompression) {
                sshSession.setConfig("compression.s2c", "zlib@openssh.com,zlib,none");
                sshSession.setConfig("compression.c2s", "zlib@openssh.com,zlib,none");
                sshSession.setConfig("compression_level", "9");
            }

            // TODO: Handle authentication

            sshSession.connect();
        } catch (IOException|JSchException e) {
            log(e.getMessage());
        } finally {
            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
            }
            if (iodineProcess != null) {
                iodineProcess.destroy();
            }
        }

    }
}
