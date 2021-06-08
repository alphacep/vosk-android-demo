package org.vosk.api;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import org.vosk.Model;
import org.vosk.api.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

/** Android specific code to download, unzip and initialize a Model through local file system */
public class VoskHelperAndroid extends VoskHelper {
    private static final File MODEL_FILE_ROOT_PATH = new File(Environment.getExternalStorageDirectory(),"models");
    private static final String LANGUAGES_PROPERIES = "languages.properies";

    public static Map<String, LanguageModelDefinition> getLanguages(Context context) throws CompletionException {
        try(InputStream in = context.getAssets().open(LANGUAGES_PROPERIES)) {
            return LanguageModelDefinition.getLanguages(in);
        } catch (IOException ex) {
            throw new CompletionException("Cannot load " + LANGUAGES_PROPERIES + " from assets", ex);
        }
    }

    /**
     *
     * @param context this of activity or service
     * @param languageId Language to be loaded. One of the id-s from languages.properties. Example: "de"
     * @param exceptionFormatIfNotFound if not null throws exception if language must be downloaded first.
     * @param modelConsumer function to be called in gui threat, when there is an error or download is complete
     * @param progress if not null report progress to caller.
     */
    public static void loadOrDownloadModelInBackground(final Context context, final String languageId,
                                 String exceptionFormatIfNotFound,
                                 final BiConsumer<Model, Throwable> modelConsumer,
                                 final  BiConsumer<String, Integer> progress)  {
        Handler handler = new Handler(Looper.getMainLooper());

        CompletableFuture
                .supplyAsync(() -> getLanguages(context))
                .thenApply(map -> map.get(languageId))
                .thenApply(language -> check(language, true, context.getString(R.string.language_not_found), languageId))
                .thenApply(language -> getOrDownloadModelDir(MODEL_FILE_ROOT_PATH, language, exceptionFormatIfNotFound, progress))
                .thenApply(dir -> new Model(dir.getAbsolutePath()))
                .whenComplete((model, ex) -> handler.post(() -> modelConsumer.accept(model, ex)));
    }

    private static <T> T check(T l, boolean throwIfNotFound, String format, Object... params) throws CompletionException {
        if (throwIfNotFound && l == null) {
            throw new CompletionException(String.format(format, params), null);
        }
        return l;
    }
}
