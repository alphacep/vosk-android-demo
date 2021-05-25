package org.vosk.demo;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.util.Log;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by Vaibhav.Jani on 6/4/15.
 */
public class FileDownloadService extends IntentService {

    private static int STATUS_OK = 100;

    private static int STATUS_FAILED = 200;

    private static final String DOWNLOADER_RECEIVER = "downloader_receiver";

    private static final String DOWNLOAD_DETAILS = "download_details";

    private static final String DOWNLOAD_STARTED = "download_started";

    private static final String DOWNLOAD_FAILED = "download_failed";

    private static final String DOWNLOAD_COMPLETED = "download_completed";

    private static final String DOWNLOAD_PROGRESS = "download_progress";

    public FileDownloadService() {

        super("");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        Bundle bundle = intent.getExtras();

        if (bundle == null
                || !bundle.containsKey(DOWNLOADER_RECEIVER)
                || !bundle.containsKey(DOWNLOAD_DETAILS)) {

            return;
        }

        ResultReceiver resultReceiver = bundle.getParcelable(DOWNLOADER_RECEIVER);

        DownloadRequest downloadDetails = bundle.getParcelable(DOWNLOAD_DETAILS);

        try {

            assert downloadDetails != null;
            URL url = new URL(downloadDetails.getServerFilePath());

            URLConnection urlConnection = url.openConnection();

            urlConnection.connect();

            int lengthOfFile = urlConnection.getContentLength();

            Log.d("FileDownloaderService", "Length of file: " + lengthOfFile);
            downloadStarted(resultReceiver);

            InputStream input = new BufferedInputStream(url.openStream());

            String localPath = downloadDetails.getLocalFilePath();

            OutputStream output = new FileOutputStream(localPath);

            byte data[] = new byte[1024];

            long total = 0;

            int count;

            while ((count = input.read(data)) != -1) {

                total += count;

                int progress = (int) ((total * 100) / lengthOfFile);

                sendProgress(progress, resultReceiver);

                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            input.close();

            if (downloadDetails.isRequiresUnzip()) {

                String unzipDestination = downloadDetails.getUnzipAtFilePath();

                if(unzipDestination == null){

                    File file = new File(localPath);

                    unzipDestination = file.getParentFile().getAbsolutePath();
                }

                unzip(localPath, unzipDestination);
            }

            downloadCompleted(resultReceiver);

            if (downloadDetails.isDeleteZipAfterExtract()) {

                File file = new File(localPath);
                file.delete();
            }

        } catch (Exception e) {

            e.printStackTrace();

            downloadFailed(resultReceiver);
        }

    }

    public void sendProgress(int progress, ResultReceiver receiver) {

        Bundle progressBundle = new Bundle();
        progressBundle.putInt(FileDownloadService.DOWNLOAD_PROGRESS, progress);
        receiver.send(STATUS_OK, progressBundle);
    }

    public void downloadStarted(ResultReceiver resultReceiver) {

        Bundle progressBundle = new Bundle();
        progressBundle.putBoolean(FileDownloadService.DOWNLOAD_STARTED, true);
        resultReceiver.send(STATUS_OK, progressBundle);
    }

    public void downloadCompleted(ResultReceiver resultReceiver) {

        Bundle progressBundle = new Bundle();
        progressBundle.putBoolean(FileDownloadService.DOWNLOAD_COMPLETED, true);
        resultReceiver.send(STATUS_OK, progressBundle);
    }

    public void downloadFailed(ResultReceiver resultReceiver) {

        Bundle progressBundle = new Bundle();
        progressBundle.putBoolean(FileDownloadService.DOWNLOAD_FAILED, true);
        resultReceiver.send(STATUS_FAILED, progressBundle);
    }

    private void unzip(String zipFilePath, String unzipAtLocation) throws Exception {

        File archive = new File(zipFilePath);

        try {

            ZipFile zipfile = new ZipFile(archive);

            for (Enumeration e = zipfile.entries(); e.hasMoreElements(); ) {

                ZipEntry entry = (ZipEntry) e.nextElement();

                unzipEntry(zipfile, entry, unzipAtLocation);
            }

        } catch (Exception e) {

            Log.e("Unzip zip", "Unzip exception", e);
        }
    }

    private void unzipEntry(ZipFile zipfile, ZipEntry entry, String outputDir) throws IOException {

        if (entry.isDirectory()) {
            createDir(new File(outputDir, entry.getName()));
            return;
        }

        File outputFile = new File(outputDir, entry.getName());
        if (!outputFile.getParentFile().exists()) {
            createDir(outputFile.getParentFile());
        }

        Log.v("ZIP E", "Extracting: " + entry);

        InputStream zin = zipfile.getInputStream(entry);
        BufferedInputStream inputStream = new BufferedInputStream(zin);
        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));

        try {

            //IOUtils.copy(inputStream, outputStream);

            try {

                for (int c = inputStream.read(); c != -1; c = inputStream.read()) {
                    outputStream.write(c);
                }

            } finally {

                outputStream.close();
            }

        } finally {
            outputStream.close();
            inputStream.close();
        }
    }

    private void createDir(File dir) {

        if (dir.exists()) {
            return;
        }

        Log.v("ZIP E", "Creating dir " + dir.getName());

        if (!dir.mkdirs()) {

            throw new RuntimeException("Can not create dir " + dir);
        }
    }

    public static class FileDownloader extends ResultReceiver {

        private DownloadRequest downloadDetails;

        private OnDownloadStatusListener onDownloadStatusListener;

        public static FileDownloader getInstance(DownloadRequest downloadDetails, OnDownloadStatusListener downloadStatusListener) {

            Handler handler = new Handler(Looper.getMainLooper());

            FileDownloader fileDownloader = new FileDownloader(handler);

            fileDownloader.downloadDetails = downloadDetails;

            fileDownloader.onDownloadStatusListener = downloadStatusListener;

            return fileDownloader;
        }

        public void download(Context context) {

            if (isOnline(context)) {

                Intent intent = new Intent(context, FileDownloadService.class);
                intent.putExtra(FileDownloadService.DOWNLOADER_RECEIVER, this);
                intent.putExtra(FileDownloadService.DOWNLOAD_DETAILS, downloadDetails);
                context.startService(intent);
            }
        }

        private FileDownloader(Handler handler) {

            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            super.onReceiveResult(resultCode, resultData);

            if (onDownloadStatusListener == null) {

                return;
            }

            if (resultCode == FileDownloadService.STATUS_OK) {

                if (resultData.containsKey(FileDownloadService.DOWNLOAD_STARTED)
                        && resultData.getBoolean(FileDownloadService.DOWNLOAD_STARTED)) {

                    onDownloadStatusListener.onDownloadStarted();

                } else if (resultData.containsKey(FileDownloadService.DOWNLOAD_COMPLETED)
                        && resultData.getBoolean(FileDownloadService.DOWNLOAD_COMPLETED)) {

                    onDownloadStatusListener.onDownloadCompleted();

                } else if (resultData.containsKey(FileDownloadService.DOWNLOAD_PROGRESS)) {

                    int progress = resultData.getInt(FileDownloadService.DOWNLOAD_PROGRESS);
                    onDownloadStatusListener.onDownloadProgress(progress);

                }

            } else if (resultCode == FileDownloadService.STATUS_FAILED) {

                onDownloadStatusListener.onDownloadFailed();
            }
        }

        public DownloadRequest getDownloadDetails() {

            return downloadDetails;
        }

        public void setDownloadDetails(DownloadRequest downloadDetails) {

            this.downloadDetails = downloadDetails;
        }

        public OnDownloadStatusListener getOnDownloadStatusListener() {

            return onDownloadStatusListener;
        }

        public void setOnDownloadStatusListener(OnDownloadStatusListener onDownloadStatusListener) {

            this.onDownloadStatusListener = onDownloadStatusListener;
        }

    }

    public static interface OnDownloadStatusListener {

        void onDownloadStarted();

        void onDownloadCompleted();

        void onDownloadFailed();

        void onDownloadProgress(int progress);

    }

    public static class DownloadRequest implements Parcelable {

        private String tag;

        private boolean requiresUnzip;

        private String serverFilePath;

        private String localFilePath;

        private String unzipAtFilePath;

        private boolean deleteZipAfterExtract = true;

        public DownloadRequest(String serverFilePath, String localPath) {

            this.serverFilePath = serverFilePath;

            this.localFilePath = localPath;

            this.requiresUnzip = requiresUnzip;
        }

        protected DownloadRequest(Parcel in) {

            requiresUnzip = in.readByte() != 0x00;
            serverFilePath = in.readString();
            localFilePath = in.readString();
            unzipAtFilePath = in.readString();
            deleteZipAfterExtract = in.readByte() != 0x00;
        }

        @Override
        public int describeContents() {

            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {

            dest.writeByte((byte) (requiresUnzip ? 0x01 : 0x00));
            dest.writeString(serverFilePath);
            dest.writeString(localFilePath);
            dest.writeString(unzipAtFilePath);
            dest.writeByte((byte) (deleteZipAfterExtract ? 0x01 : 0x00));
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<DownloadRequest> CREATOR = new Parcelable.Creator<DownloadRequest>() {

            @Override
            public DownloadRequest createFromParcel(Parcel in) {

                return new DownloadRequest(in);
            }

            @Override
            public DownloadRequest[] newArray(int size) {

                return new DownloadRequest[size];
            }
        };

        public boolean isRequiresUnzip() {

            return requiresUnzip;
        }

        public void setRequiresUnzip(boolean requiresUnzip) {

            this.requiresUnzip = requiresUnzip;
        }

        public String getServerFilePath() {

            return serverFilePath;
        }

        public void setServerFilePath(String serverFilePath) {

            this.serverFilePath = serverFilePath;
        }

        public String getLocalFilePath() {

            return localFilePath;
        }

        public void setLocalFilePath(String localFilePath) {

            this.localFilePath = localFilePath;
        }

        public static Creator<DownloadRequest> getCreator() {

            return CREATOR;
        }

        public String getUnzipAtFilePath() {
            return unzipAtFilePath;
        }

        public void setUnzipAtFilePath(String unzipAtFilePath) {
            this.unzipAtFilePath = unzipAtFilePath;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public boolean isDeleteZipAfterExtract() {
            return deleteZipAfterExtract;
        }

        public void setDeleteZipAfterExtract(boolean deleteZipAfterExtract) {
            this.deleteZipAfterExtract = deleteZipAfterExtract;
        }
    }

    private static boolean isOnline(Context context) {

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo netInfo = cm.getActiveNetworkInfo();

        if (netInfo != null
                && netInfo.isConnectedOrConnecting()
                && cm.getActiveNetworkInfo().isAvailable()
                && cm.getActiveNetworkInfo().isConnected()) {

            return true;
        }

        return false;
    }

}
