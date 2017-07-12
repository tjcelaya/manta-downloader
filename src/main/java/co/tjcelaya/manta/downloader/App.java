package co.tjcelaya.manta.downloader;

import com.joyent.manta.client.MantaClient;
import com.joyent.manta.config.ChainedConfigContext;
import com.joyent.manta.config.DefaultsConfigContext;
import com.joyent.manta.config.EnvVarConfigContext;
import com.joyent.manta.config.StandardConfigContext;
import com.joyent.manta.exception.MantaClientEncryptionCiphertextAuthenticationException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;

import java.io.InputStream;
import java.util.Base64;
import java.util.Scanner;

public class App {

    private static final String OPT_PATH = "p";
    private static final String OPT_ENCRYPTION_KEY_CONTENT = "k";
    private static final String OPT_ENCRYPTION_KEY_ID = "i";
    private static MantaClient client;

    public static void main(String[] args) throws Exception {
        final Options opts = new Options()
                .addOption("i", true, "encryption key id")
                .addOption("k", true, "encryption key (base64)")
                .addOption("p", true, "path to object");

        final CommandLine invocation = new DefaultParser().parse(opts, args);

        client = new MantaClient(
                new ChainedConfigContext(
                        new DefaultsConfigContext(),
                        new EnvVarConfigContext(),
                        new StandardConfigContext()
                                .setClientEncryptionEnabled(true)
                                .setEncryptionAlgorithm("AES256/CTR/NoPadding")
                                .setEncryptionKeyId(invocation.getOptionValue(OPT_ENCRYPTION_KEY_ID))
                                .setEncryptionPrivateKeyBytes(
                                        Base64.getDecoder().decode(invocation.getOptionValue(OPT_ENCRYPTION_KEY_CONTENT)))));

        final Scanner input;
        if (invocation.hasOption(OPT_PATH)) {
            input = new Scanner(invocation.getOptionValue(OPT_PATH));
        } else {
            input = new Scanner(System.in);
        }

        boolean shouldRetry;
        String path;

        while (input.hasNextLine()) {
            shouldRetry = true;
            path = input.nextLine();
            while (shouldRetry) {
                shouldRetry = attemptValidation(path);
            }
        }
    }

    /**
     * @param path the path to check for MantaClientEncryptionCiphertextAuthenticationException
     *
     * @return whether or not we checked successfully, false means the caller should retry
     */
    private static boolean attemptValidation(String path) {
        try {
            InputStream obj = client.getAsInputStream(path);
            IOUtils.copy(obj, new NullOutputStream());
            obj.close();
            System.out.println(path);
            return false;
        } catch (MantaClientEncryptionCiphertextAuthenticationException mcecae) {
            System.err.println(path);
            return false;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return true;
        }
    }
}
