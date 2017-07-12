package co.tjcelaya.manta.downloader;

import com.joyent.manta.client.MantaClient;
import com.joyent.manta.config.*;
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
    private static final String OPT_ERROR_ONLY = "e";
    private static MantaClient client;
    private static boolean successOutput = true;

    public static void main(String[] args) throws Exception {
        final Options opts = new Options()
                .addOption(OPT_ENCRYPTION_KEY_ID, true, "encryption key id")
                .addOption(OPT_ENCRYPTION_KEY_CONTENT, true, "encryption key (base64)")
                .addOption(OPT_PATH, true, "path to object")
                .addOption(OPT_ERROR_ONLY, false, "whether to omit successful files from output");

        final CommandLine invocation = new DefaultParser().parse(opts, args);

        successOutput = !invocation.hasOption(OPT_ERROR_ONLY);

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
            if (successOutput) {
                System.out.println(path);
            }
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
