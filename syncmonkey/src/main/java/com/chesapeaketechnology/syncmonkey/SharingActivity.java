package com.chesapeaketechnology.syncmonkey;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.chesapeaketechnology.syncmonkey.fileupload.FileUploadSyncAdapter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * An activity that accepts files/text from other apps and writes it to a file in a directory that
 * is synced with the remote server.
 *
 * @since 0.0.8
 */
public class SharingActivity extends AppCompatActivity
{
    private static final String LOG_TAG = SharingActivity.class.getSimpleName();

    private Uri sharedFileUri;
    private List<Uri> sharedFileUris;
    private String sharedText;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sharing);

        final Button shareButton = findViewById(R.id.shareButton);
        shareButton.setOnClickListener(view -> {
            if (copySharedFilesToSyncMonkeyDirectory())
            {
                FileUploadSyncAdapter.runSyncAdapterNow(getApplicationContext());
                finish();
            }
        });

        final Button cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(view -> finish());

        try
        {
            // Get intent, action and MIME type
            final Intent intent = getIntent();
            final String action = intent.getAction();
            final String type = intent.getType();

            if (type == null || action == null)
            {
                throw new IllegalArgumentException("The type or action cannot be null when sharing content with Sync Monkey");
            }

            switch (action)
            {
                case Intent.ACTION_SEND:
                    if (type.startsWith("text/"))
                    {
                        updateUiForSharedText(intent); // Handle text being sent
                    } else if (intent.getParcelableExtra(Intent.EXTRA_STREAM) != null)
                    {
                        updateUiForSharedFile(intent); // Handle single image being sent
                    } else
                    {
                        showSharingErrorToast();
                    }

                    break;

                case Intent.ACTION_SEND_MULTIPLE:
                    if (intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) != null)
                    {
                        updateUiForMultipleSharedFiles(intent); // Handle multiple images being sent
                    } else
                    {
                        showSharingErrorToast();
                    }

                    break;

                default:
                    showSharingErrorToast();
            }
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "Unable to accept the file in the Sync Monkey Sharing Activity due to an exception: ", e);
            showSharingErrorToast();
        }
    }

    /**
     * Create a unique file name and update the UI with the new file name.  The user will have the
     * option to change the generated file name before actually saving the shared text to the file.
     *
     * @param intent The intent that contains the shared text.
     */
    private void updateUiForSharedText(Intent intent)
    {
        sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null)
        {
            setFileNameEditText(createUniqueFileName(SyncMonkeyConstants.DEFAULT_SHARED_TEXT_FILE_NAME));
        } else
        {
            Log.i(LOG_TAG, "The text shared to the Sync Monkey app was null");
        }
    }

    /**
     * Pulls the file from the provided intent, and then updates the UI so that the file name can be modified before
     * copying it.
     *
     * @param intent The intent with a single file.
     */
    private void updateUiForSharedFile(Intent intent)
    {
        sharedFileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (sharedFileUri != null)
        {
            setFileNameEditText(createUniqueFileName(getFileName(sharedFileUri)));
        } else
        {
            Log.i(LOG_TAG, "The file URI shared to the Sync Monkey app was null");
        }
    }

    /**
     * Pulls the list of files from the provided intent, and then updates the UI so that the file name can't be entered.
     *
     * @param intent The intent with multiple files.
     */
    private void updateUiForMultipleSharedFiles(Intent intent)
    {
        sharedFileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (sharedFileUris != null)
        {
            final EditText fileNameEditText = findViewById(R.id.fileNameValue);
            fileNameEditText.setEnabled(false);
            fileNameEditText.setText(getFileNamesAsString(sharedFileUris));

            final TextView fileNameLabel = findViewById(R.id.fileNameLabel);
            fileNameLabel.setText(R.string.file_name_multiple_label);
        } else
        {
            Log.i(LOG_TAG, "The List of file URIs shared to the Sync Monkey app was null");
        }
    }

    /**
     * Logs an error and displays a toast that the shared content could not be accepted this activity.
     */
    private void showSharingErrorToast()
    {
        Log.e(LOG_TAG, "Unable to share the provided content");
        Toast.makeText(getApplicationContext(), R.string.sharing_error_unknown_error, Toast.LENGTH_SHORT).show();
    }

    /**
     * Sets the provided string to the file name text box in the UI.
     *
     * @param fileName The file name string to set in the edit text UI element.
     */
    private void setFileNameEditText(String fileName)
    {
        final EditText fileNameEditText = findViewById(R.id.fileNameValue);
        fileNameEditText.setText(fileName);
    }

    /**
     * Extracts the file name from a URI resource.
     *
     * @param uri The URI to pull the file name for.
     * @return The file name for the URI resource, or null if the URI does not represent a file.
     */
    private String getFileName(Uri uri)
    {
        String result = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme()))
        {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null))
            {
                if (cursor != null && cursor.moveToFirst())
                {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }

        if (result == null)
        {
            result = uri.getPath();
            if (result != null)
            {
                int cut = result.lastIndexOf('/');
                if (cut != -1)
                {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result;
    }

    /**
     * @param uris The list of URIs representing the files.
     * @return All the file names from the list of file URIs as one concatenated String with the names separated by
     * commas.
     */
    private String getFileNamesAsString(List<Uri> uris)
    {
        final StringBuilder fileNames = new StringBuilder();
        uris.forEach(uri -> fileNames.append(getFileName(uri)).append(", "));

        return fileNames.toString();
    }

    /**
     * Takes the current shared file(s) and copies them to the private shared directory.
     *
     * @return True if the activity should be ended, or false if the activity should not be ended as the user can fix
     * whatever the copy error is (e.g. The file name edit text field is empty).
     */
    private boolean copySharedFilesToSyncMonkeyDirectory()
    {
        try
        {
            if (sharedFileUri != null)
            {
                // A single image was shared

                final EditText fileNameEditText = findViewById(R.id.fileNameValue);
                final String fileName = fileNameEditText.getText().toString();
                if (fileName.isEmpty())
                {
                    Toast.makeText(getApplicationContext(), R.string.sharing_error_invalid_file_name, Toast.LENGTH_SHORT).show();
                    return false;
                }

                copySharedFile(sharedFileUri, fileName);

                Toast.makeText(getApplicationContext(), R.string.sharing_success_file, Toast.LENGTH_SHORT).show();
            } else if (sharedFileUris != null)
            {
                // Multiple images were shared

                int errorCount = 0;

                for (Uri imageUri : sharedFileUris)
                {
                    try
                    {
                        copySharedFile(imageUri, getFileName(imageUri));
                    } catch (Exception e)
                    {
                        errorCount++;
                        Log.e(LOG_TAG, "Unable to copy the file in the Sync Monkey Sharing Activity due to an exception: ", e);
                    }
                }

                if (errorCount > 0)
                {
                    Toast.makeText(getApplicationContext(),
                            getString(R.string.sharing_failure_multiple_files, String.valueOf(errorCount)), Toast.LENGTH_SHORT).show();
                } else
                {
                    Toast.makeText(getApplicationContext(), R.string.sharing_success_multiple_files, Toast.LENGTH_SHORT).show();
                }
            } else if (sharedText != null)
            {
                // Text was shared

                final EditText fileNameEditText = findViewById(R.id.fileNameValue);
                final String fileName = fileNameEditText.getText().toString();
                if (fileName.isEmpty())
                {
                    Toast.makeText(getApplicationContext(), R.string.sharing_error_invalid_file_name, Toast.LENGTH_SHORT).show();
                    return false;
                }

                try (final OutputStream textOutputStream = createFileOutputStream(fileName))
                {
                    textOutputStream.write(sharedText.getBytes());
                    textOutputStream.flush();
                }

                Toast.makeText(getApplicationContext(), R.string.sharing_success_text, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "Unable to share the file in the Sync Monkey Sharing Activity due to an exception: ", e);
            Toast.makeText(getApplicationContext(), R.string.sharing_error_unknown_error, Toast.LENGTH_SHORT).show();
        }

        return true;
    }

    /**
     * Given a URI, copy the file it represents into the app's private storage sync directory.
     *
     * @param fileToShare   The file to copy over to the app's private storage.
     * @param fileNameToUse The file name to use in the app's private storage sync directory.
     * @throws IOException If something goes wrong trying to copy the file.
     */
    private void copySharedFile(Uri fileToShare, String fileNameToUse) throws IOException
    {
        if (fileNameToUse == null)
        {
            throw new IOException("Could not get a file name for the shared file: " + fileToShare);
        }

        try (final InputStream imageInputStream = getContentResolver().openInputStream(fileToShare);
             final FileOutputStream imageOutputStream = createFileOutputStream(fileNameToUse))
        {
            if (imageInputStream == null)
            {
                throw new FileNotFoundException("Could not get the shared image input stream");
            }

            SyncMonkeyUtils.copyInputStreamToOutputStream(imageInputStream, imageOutputStream);
        }
    }

    /**
     * Creates a file output stream using the provided file name and a target directory of the Sync Monkey private sync
     * directory.  This enables writing a file to a directory that is automatically synced with the remote server.
     *
     * @param fileName The file name to use when writing the new file to the sync directory.  If a file with the same
     *                 name already exists, a numerical count
     *                 will be appended to create a unique file name.
     * @return The file output stream where the shared file can be written to.
     */
    private FileOutputStream createFileOutputStream(String fileName) throws IOException
    {
        final File privateAppFilesSyncDirectory = getPrivateAppFilesSyncDirectory();

        File targetFile = new File(privateAppFilesSyncDirectory, createUniqueFileName(fileName));

        //noinspection ResultOfMethodCallIgnored
        targetFile.createNewFile();

        return new FileOutputStream(targetFile);
    }

    /**
     * Given a starting file name, create a file name that is unique in the private shared directory.  If the provided
     * file name is already unique, then use it.  If it is not unique, then keep counting up and adding that number to
     * the file name until a unique file name is found.
     *
     * @param fileName The file name to start with.
     * @return A unique file name in the app's private storage sync directory.
     */
    private String createUniqueFileName(String fileName)
    {
        final File privateAppFilesSyncDirectory = getPrivateAppFilesSyncDirectory();
        File potentialNewFile = new File(privateAppFilesSyncDirectory, fileName);

        if (potentialNewFile.exists())
        {
            final String nameWithoutExtension = SyncMonkeyUtils.getNameWithoutExtension(fileName);
            final String ext = SyncMonkeyUtils.getExtension(fileName);
            final String extension = ext == null ? "" : ext;

            int i = 1;

            while (potentialNewFile.exists())
            {
                fileName = nameWithoutExtension + "(" + i++ + ")" + extension;
                potentialNewFile = new File(privateAppFilesSyncDirectory, fileName);
            }
        }

        return fileName;
    }

    /**
     * @return The File object representing the app's private storage directory that is synced with the remote server.
     * Any files in this directory will be uploaded to the remote server.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private File getPrivateAppFilesSyncDirectory()
    {
        final File privateAppFilesSyncDirectory = new File(getApplicationContext().getFilesDir(), SyncMonkeyConstants.PRIVATE_SHARED_SYNC_DIRECTORY);
        if (!privateAppFilesSyncDirectory.exists()) privateAppFilesSyncDirectory.mkdir();
        return privateAppFilesSyncDirectory;
    }
}
