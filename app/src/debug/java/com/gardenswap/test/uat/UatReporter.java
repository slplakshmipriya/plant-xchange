package com.gardenswap.test.uat;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Persists UAT reports on-device (debug builds). The report file is the
 * handoff: whoever runs the console pastes it back for review, and a
 * future backend debug endpoint can ingest the same text payload.
 */
public final class UatReporter {

    private static final String TAG = "UAT";
    private static final String FILE_NAME = "uat-report.txt";

    private UatReporter() {
    }

    public static String localPath() {
        return "app files dir/" + FILE_NAME;
    }

    public static void saveLocal(Context context, String report) {
        File out = new File(context.getFilesDir(), FILE_NAME);
        try (FileWriter w = new FileWriter(out, false)) {
            w.write(report);
            Log.i(TAG, "report saved to " + out.getAbsolutePath());
        } catch (IOException e) {
            Log.w(TAG, "could not save UAT report", e);
        }
    }
}
