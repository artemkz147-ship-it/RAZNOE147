package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

final class DigitalSignatureTools {
    private DigitalSignatureTools() {}

    static Uri signPdf(Context ctx, Uri pdfUri, Uri pkcs12Uri, String password) throws Exception {
        if (password == null) password = "";
        Provider bc = new BouncyCastleProvider();
        KeyStore ks = KeyStore.getInstance("PKCS12", bc);
        try (InputStream in = ctx.getContentResolver().openInputStream(pkcs12Uri)) {
            if (in == null) throw new IllegalArgumentException("Не удалось открыть сертификат");
            ks.load(in, password.toCharArray());
        }

        String alias = findPrivateKeyAlias(ks);
        if (alias == null) throw new IllegalArgumentException("В PKCS#12 не найден закрытый ключ");
        PrivateKey key = (PrivateKey) ks.getKey(alias, password.toCharArray());
        Certificate[] chain = ks.getCertificateChain(alias);
        if (key == null || chain == null || chain.length == 0) throw new IllegalArgumentException("Не удалось получить ключ и цепочку сертификатов");
        X509Certificate signerCert = (X509Certificate) chain[0];
        List<X509Certificate> certs = new ArrayList<>();
        for (Certificate c : chain) if (c instanceof X509Certificate) certs.add((X509Certificate) c);
        String algorithm = signatureAlgorithm(key);

        File input = FileStore.copyUriToTemp(ctx, pdfUri, ".pdf");
        File out = File.createTempFile("crypto_signed_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = PDDocument.load(input)) {
            PDSignature sig = new PDSignature();
            sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            sig.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            sig.setName(signerCert.getSubjectX500Principal().getName());
            sig.setReason("Подписано в ФайлМастер");
            sig.setSignDate(Calendar.getInstance());

            SignatureInterface signer = content -> createCmsSignature(content, key, signerCert, certs, algorithm, bc);
            SignatureOptions options = new SignatureOptions();
            options.setPreferredSignatureSize(65536);
            try {
                doc.addSignature(sig, signer, options);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    doc.saveIncremental(fos);
                }
            } finally {
                try { options.close(); } catch (Exception ignored) {}
            }
        } finally { input.delete(); }

        try {
            return FileStore.publishFile(ctx, out, "Электронно_подписано_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
        } finally { out.delete(); }
    }

    static Uri verifyPdf(Context ctx, Uri pdfUri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, pdfUri, ".pdf");
        Provider bc = new BouncyCastleProvider();
        StringBuilder report = new StringBuilder("Проверка электронных подписей PDF\n\n");
        int index = 0;
        try (PDDocument doc = PDDocument.load(input)) {
            List<PDSignature> signatures = doc.getSignatureDictionaries();
            if (signatures.isEmpty()) report.append("Электронные подписи не найдены.\n");
            for (PDSignature sig : signatures) {
                index++;
                report.append("Подпись #").append(index).append("\n");
                report.append("Имя: ").append(safe(sig.getName())).append("\n");
                report.append("Причина: ").append(safe(sig.getReason())).append("\n");
                report.append("SubFilter: ").append(safe(sig.getSubFilter())).append("\n");
                if (sig.getSignDate() != null) report.append("Дата: ").append(sig.getSignDate().getTime()).append("\n");
                boolean valid = false;
                String subject = "";
                try (FileInputStream contentInput = new FileInputStream(input);
                     FileInputStream signedInput = new FileInputStream(input)) {
                    byte[] cmsBytes = sig.getContents(contentInput);
                    byte[] signedBytes = sig.getSignedContent(signedInput);
                    CMSSignedData cms = new CMSSignedData(new org.bouncycastle.cms.CMSProcessableByteArray(signedBytes), cmsBytes);
                    Store<X509CertificateHolder> certStore = cms.getCertificates();
                    SignerInformationStore signerInfos = cms.getSignerInfos();
                    for (SignerInformation signer : signerInfos.getSigners()) {
                        Collection<X509CertificateHolder> matches = certStore.getMatches(signer.getSID());
                        if (matches.isEmpty()) continue;
                        X509CertificateHolder holder = matches.iterator().next();
                        X509Certificate cert = new JcaX509CertificateConverter().setProvider(bc).getCertificate(holder);
                        subject = cert.getSubjectX500Principal().getName();
                        if (signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(bc).build(cert))) valid = true;
                    }
                } catch (Exception e) {
                    report.append("Ошибка проверки CMS: ").append(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()).append("\n");
                }
                if (!subject.isEmpty()) report.append("Сертификат: ").append(subject).append("\n");
                report.append("Криптографическая проверка: ").append(valid ? "УСПЕШНО" : "НЕ ПОДТВЕРЖДЕНА").append("\n\n");
            }
        } finally { input.delete(); }
        return FileStore.publishBytes(ctx, report.toString().getBytes(StandardCharsets.UTF_8),
                "Проверка_подписей_" + System.currentTimeMillis() + ".txt", "text/plain", null);
    }

    private static byte[] createCmsSignature(InputStream content, PrivateKey key, X509Certificate signerCert,
                                             List<X509Certificate> certs, String algorithm, Provider provider) throws IOException {
        try {
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            ContentSigner contentSigner = new JcaContentSignerBuilder(algorithm).setProvider(provider).build(key);
            DigestCalculatorProvider digestProvider = new JcaDigestCalculatorProviderBuilder().setProvider(provider).build();
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(digestProvider).build(contentSigner, signerCert));
            gen.addCertificates(new JcaCertStore(certs));
            CMSTypedData data = new CMSTypedData() {
                @Override public ASN1ObjectIdentifier getContentType() { return CMSObjectIdentifiers.data; }
                @Override public Object getContent() { return content; }
                @Override public void write(OutputStream out) throws IOException, CMSException {
                    byte[] buf = new byte[64 * 1024]; int n;
                    while ((n = content.read(buf)) != -1) out.write(buf, 0, n);
                }
            };
            CMSSignedData signed = gen.generate(data, false);
            return signed.getEncoded();
        } catch (Exception e) {
            throw new IOException("Не удалось создать электронную подпись: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    private static String findPrivateKeyAlias(KeyStore ks) throws Exception {
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) return alias;
        }
        return null;
    }

    private static String signatureAlgorithm(PrivateKey key) {
        String a = key.getAlgorithm() == null ? "" : key.getAlgorithm().toUpperCase();
        if (a.contains("RSA")) return "SHA256withRSA";
        if (a.contains("ED25519")) return "Ed25519";
        if (a.contains("ED448")) return "Ed448";
        if (a.contains("GOST") && a.contains("512")) return "GOST3411-2012-512WITHECGOST3410-2012-512";
        if (a.contains("GOST")) return "GOST3411-2012-256WITHECGOST3410-2012-256";
        if (a.contains("EC")) return "SHA256withECDSA";
        throw new IllegalArgumentException("Тип закрытого ключа пока не поддерживается: " + key.getAlgorithm());
    }

    private static String safe(String s) { return s == null || s.isBlank() ? "—" : s; }
}
