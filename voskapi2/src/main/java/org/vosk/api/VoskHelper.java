package org.vosk.api;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Android independant code to download, unzip and initialize a Model through local file system */
public class VoskHelper {
    private static final String TAG = "VoskHelper";
    private static final Logger LOGGER = LoggerFactory.getLogger(TAG);

    public static File getOrDownloadModelDir(
            File modelRootDir, LanguageModelDefinition languageModelDefinition,
            String exceptionFormatIfNotFound, BiConsumer<String, Integer> progress) throws CompletionException {

        File modelDir = new File(modelRootDir, languageModelDefinition.getId());
        if (isModel(modelDir))  return modelDir;

        if (exceptionFormatIfNotFound != null) throw new CompletionException(String.format(exceptionFormatIfNotFound, languageModelDefinition.getLocaleName()), null);

        modelDir.mkdirs();
        File zipFile = new File(modelDir, languageModelDefinition.getId() + ".zip");

        try {
            downloadAndUnzip(languageModelDefinition.getUrl(), zipFile, progress);
        } catch (IOException e) {
            throw new CompletionException(e);
        }
        if (!modelDir.isDirectory() || !modelDir.exists()) {
            modelDir = new File(modelRootDir, languageModelDefinition.getId());
        }
        return modelDir;
    }

    public static boolean isModel(File modelDir) {
        return modelDir.exists() && modelDir.isDirectory() && modelDir.listFiles().length > 3;
    }

    public static void downloadAndUnzip(URL url, File localZipPath, BiConsumer<String, Integer> progress) throws IOException {
        download(url, localZipPath, progress);
        unzip(localZipPath, localZipPath.getParentFile(), progress);
        localZipPath.delete();
    }

    private static void download(URL url, File localZipPath, BiConsumer<String, Integer> progress) throws IOException {
        URLConnection urlConnection = url.openConnection();
        urlConnection.connect();

        int lengthOfFile = urlConnection.getContentLength();
        String message = "download('" + url + "' => '" + localZipPath + "')[" + lengthOfFile + "] ";
        LOGGER.info(message);
        if (progress != null) progress.accept(message, 0);

        try (   InputStream input = new BufferedInputStream(url.openStream());
                OutputStream output = new FileOutputStream(localZipPath)) {
            copy(input, output, progress, message);
        } catch (IOException e) {
            LOGGER.error( "err " + message + e.getMessage(), e);
            throw new IOException(message, e);
        }
    }

    private static void copy(InputStream input, OutputStream output, BiConsumer<String, Integer> progress, String message) throws IOException {
        byte data[] = new byte[10240];
        int count;

        int total = 0;
        while ((count = input.read(data)) != -1) {
            output.write(data, 0, count);
            total += count;
            if (progress != null) progress.accept(message, 0);
        }
        output.flush();
    }

    private static void unzip(File zipFilePath, File unzipAtLocation, BiConsumer<String, Integer> progress) throws IOException {
        File archive = zipFilePath;

        String message = "unzip('" + zipFilePath + "' => '" + unzipAtLocation + "') ";
        LOGGER.info( message);
        if (progress != null) progress.accept(message, 0);
        try (ZipFile zipfile = new ZipFile(archive)) {
            for (Enumeration e = zipfile.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = (ZipEntry) e.nextElement();
                unzipEntry(zipfile, entry, unzipAtLocation, progress);
            }
        } catch (IOException e) {
            LOGGER.error( "err " + message + e.getMessage(), e);
            throw new IOException(message, e);
        }
    }

    private static void unzipEntry(ZipFile zipfile, ZipEntry entry, File outputDir, BiConsumer<String, Integer> progress) throws IOException {
        if (entry.isDirectory()) {
            createDir(new File(outputDir, entry.getName()));
            return;
        }

        File outputFile = new File(outputDir, entry.getName());
        if (!outputFile.getParentFile().exists()) {
            createDir(outputFile.getParentFile());
        }

        String message = "unzipEntry(" + entry + ")[" + entry.getSize() + "] ";
        LOGGER.debug( message);
        if (progress != null) progress.accept(message, 0);

        InputStream zin = zipfile.getInputStream(entry);

        try (BufferedInputStream input = new BufferedInputStream(zin);
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(outputFile));) {
            copy(input, output, progress, message);
        } catch (IOException e) {
            LOGGER.error( "err " + message + e.getMessage(), e);
            throw new IOException(message, e);
        }
    }

    private static void createDir(File dir) {
        if (dir.exists()) {
            return;
        }
        LOGGER.debug("Creating dir " + dir.getName());

        if (!dir.mkdirs()) {
            throw new RuntimeException("Can not create dir " + dir);
        }
    }
}
