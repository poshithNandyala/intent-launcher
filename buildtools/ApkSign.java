import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * Tiny wrapper around apksig.
 *   sign:   java ApkSign sign <in.apk> <out.apk> <keystore.p12> <alias> <password> <minSdk>
 *   verify: java ApkSign verify <apk>
 */
public class ApkSign {
    public static void main(String[] a) throws Exception {
        if (a.length >= 1 && a[0].equals("sign")) {
            File in = new File(a[1]), out = new File(a[2]);
            char[] pw = a[5].toCharArray();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(a[3])) { ks.load(fis, pw); }
            PrivateKey key = (PrivateKey) ks.getKey(a[4], pw);
            X509Certificate cert = (X509Certificate) ks.getCertificate(a[4]);
            ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("INTENT", key,
                    Collections.singletonList(cert)).build();
            new ApkSigner.Builder(Collections.singletonList(cfg))
                    .setInputApk(in).setOutputApk(out)
                    .setMinSdkVersion(Integer.parseInt(a[6]))
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .setCreatedBy("Intent build")
                    .build().sign();
            System.out.println("signed -> " + out);
        } else if (a.length >= 2 && a[0].equals("verify")) {
            ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[1])).build().verify();
            System.out.println("verified=" + r.isVerified() + " v1=" + r.isVerifiedUsingV1Scheme()
                    + " v2=" + r.isVerifiedUsingV2Scheme() + " v3=" + r.isVerifiedUsingV3Scheme());
            for (Object e : r.getErrors()) System.out.println("ERROR: " + e);
            for (Object w : r.getWarnings()) System.out.println("WARN: " + w);
            if (!r.isVerified()) System.exit(2);
        } else {
            System.err.println("usage: sign|verify ...");
            System.exit(1);
        }
    }
}
