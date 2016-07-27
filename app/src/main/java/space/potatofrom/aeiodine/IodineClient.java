package space.potatofrom.aeiodine;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by kevin on 7/25/16.
 */
public final class IodineClient {
    private final Set<IodineArgument> args;
    private final String domain;
    private final Context context;
    private final String iodineExecutable;

    private static final String IODINE_ASSETS_DIR = "iodine";
    private static final String IODINE_FILE_NAME = "iodine";

    public IodineClient(
            Set<IodineArgument> args,
            String domain,
            Context context) throws IOException {
        this.args = args;
        this.domain = domain;
        this.context = context;

        iodineExecutable = prepareCorrectIodineExecutable();
    }

    private void copyInputStreamToFile(InputStream in, File file) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while((len=in.read(buf)) > 0){
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Detect arch, prepare executable, and return the path to it
     *
     * @return The path to the iodine executable
     */
    private String prepareCorrectIodineExecutable() throws IOException {
        String[] abis = Build.SUPPORTED_ABIS;
        AssetManager assetMan = context.getAssets();
        InputStream iodineInputStream = null;

        for (String abi : abis) {
            try {
                iodineInputStream =
                        assetMan.open(IODINE_ASSETS_DIR + "/" + abi + "/" + IODINE_FILE_NAME);
                // Hey, it didn't fail!
                break;
            } catch (IOException e) {
                continue;
            }
        }

        if (iodineInputStream == null) {
            throw new IOException("No iodine binary found for abis");
        } else {
            File cacheDir = context.getCodeCacheDir();

            File iodineExecutable = new File(cacheDir, IODINE_FILE_NAME);
            if (iodineExecutable.exists()) {
                iodineExecutable.delete();
            }

            copyInputStreamToFile(iodineInputStream, iodineExecutable);
            if (!iodineExecutable.setExecutable(true, false)) {
                throw new SecurityException("Couldn't set iodine executable");
            }
            return iodineExecutable.getCanonicalPath();
        }
    }

    public Process start() throws IOException {
        ProcessBuilder pBuilder = new ProcessBuilder();
        List<String> command = new ArrayList<>();
        String iodineCommand = iodineExecutable;

        command.add("su");
        command.add("-c");

        for (IodineArgument arg : args) {
            String flagValue = arg.getFlag().toString();

            iodineCommand += " " + flagValue;
            if (arg.getValue() != null) {
                iodineCommand += " " + arg.getValue();
            }
        }

        iodineCommand += " " + domain;

        command.add(iodineCommand);

        return pBuilder
                .command(command)
                .redirectErrorStream(true)
                .start();
    }
}
