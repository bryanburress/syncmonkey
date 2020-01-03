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

public class SharingActivity extends AppCompatActivity
{
    private static final String LOG_TAG = SharingActivity.class.getSimpleName();

    private Uri sharedImageUri;
    private List<Uri> sharedImageUris;
    private String sharedText;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sharing);

        final Button shareButton = findViewById(R.id.shareButton);
        shareButton.setOnClickListener(view -> {
            copySharedFilesToSyncMonkeyDirectory();
            FileUploadSyncAdapter.runSyncAdapterNow(getApplicationContext());
            finish();
        });

        final Button cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(view -> finish());

        try
        {
            // Get intent, action and MIME type
            final Intent intent = getIntent();
            final String action = intent.getAction();
            final String type = intent.getType();

            if (type == null || action == null) throw new IllegalArgumentException("The type or action cannot be null when sharing content with Sync Monkey");

            switch (action)
            {
                case Intent.ACTION_SEND:
                    if (type.startsWith("text/"))
                    {
                        handleSendText(intent); // Handle text being sent
                    } else if (intent.getParcelableExtra(Intent.EXTRA_STREAM) != null)
                    {
                        handleSendFile(intent); // Handle single image being sent
                    } else
                    {
                        handleUnsupportedContent();
                    }

                    break;

                case Intent.ACTION_SEND_MULTIPLE:
                    if (intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) != null)
                    {
                        handleSendMultipleFiles(intent); // Handle multiple images being sent
                    } else
                    {
                        handleUnsupportedContent();
                    }

                    break;

                default:
                    handleUnsupportedContent();
            }
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "Unable to accept the file in the Sync Monkey Sharing Activity due to an exception: ", e);
            handleUnsupportedContent();
        }

        //Toolbar toolbar = findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);
    }

    private void handleSendText(Intent intent)
    {
        sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null)
        {
            final String fileName = createUniqueFileName(SyncMonkeyConstants.DEFAULT_SHARED_TEXT_FILE_NAME);

            final EditText fileNameEditText = findViewById(R.id.fileNameValue);
            fileNameEditText.setText(fileName);
        }
    }

    /**
     * Pulls the file from the provided intent, and then updates the UI so that the file name can be modified before copying it.
     *
     * @param intent The intent with a single file.
     */
    private void handleSendFile(Intent intent)
    {
        sharedImageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (sharedImageUri != null)
        {
            final String originalFileName = createUniqueFileName(getFileName(sharedImageUri));

            final EditText fileNameEditText = findViewById(R.id.fileNameValue);
            fileNameEditText.setText(originalFileName);
        }
    }

    /**
     * Pulls the list of files from the provided intent, and then updates the UI so that the file name can't be entered.
     *
     * @param intent The intent with multiple files.
     */
    private void handleSendMultipleFiles(Intent intent)
    {
        sharedImageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (sharedImageUris != null)
        {
            final EditText fileNameEditText = findViewById(R.id.fileNameValue);
            fileNameEditText.setEnabled(false);
            fileNameEditText.setText(getFileNamesAsString(sharedImageUris));

            final TextView fileNameLabel = findViewById(R.id.fileNameLabel);
            fileNameLabel.setText(R.string.file_name_multiple_label);
        }
    }

    private void handleUnsupportedContent()
    {
        Log.e(LOG_TAG, "Unable to share the provided content");
        Toast.makeText(getApplicationContext(), R.string.sharing_error_unknown_error, Toast.LENGTH_SHORT).show();
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
     * @return All the file names from the list of file URIs as one concatenated String with the names separated by commas.
     */
    private String getFileNamesAsString(List<Uri> uris)
    {
        final StringBuilder fileNames = new StringBuilder();
        uris.forEach(uri -> fileNames.append(getFileName(uri)).append(", "));

        return fileNames.toString();
    }

    /**
     * Takes the current shared file(s) and copies them to the private shared directory.
     */
    private void copySharedFilesToSyncMonkeyDirectory()
    {
        try
        {
            if (sharedImageUri != null)
            {
                // A single image was shared

                final EditText fileNameEditText = findViewById(R.id.fileNameValue);
                final String fileName = fileNameEditText.getText().toString();
                if (fileName.isEmpty())
                {
                    Toast.makeText(getApplicationContext(), R.string.sharing_error_invalid_file_name, Toast.LENGTH_SHORT).show();
                    return;
                }

                copySharedFile(sharedImageUri, fileName);

                Toast.makeText(getApplicationContext(), R.string.sharing_success_file, Toast.LENGTH_SHORT).show();
            } else if (sharedImageUris != null)
            {
                // Multiple images were shared

                sharedImageUris.forEach(imageUri -> {
                    try
                    {
                        copySharedFile(imageUri, null);
                    } catch (Exception e)
                    {
                        Log.e(LOG_TAG, "Unable to copy the file in the Sync Monkey Sharing Activity due to an exception: ", e);
                    }
                });

                Toast.makeText(getApplicationContext(), R.string.sharing_success_multiple_files, Toast.LENGTH_SHORT).show();
            } else if (sharedText != null)
            {
                // Text was shared

                final EditText fileNameEditText = findViewById(R.id.fileNameValue);
                final String fileName = fileNameEditText.getText().toString();
                if (fileName.isEmpty())
                {
                    Toast.makeText(getApplicationContext(), R.string.sharing_error_invalid_file_name, Toast.LENGTH_SHORT).show();
                    return;
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
        } finally
        {
            sharedImageUri = null;
            sharedImageUris = null;
            sharedText = null;
        }
    }

    /**
     * Given a URI, copy the file it represents into the app's private storage sync directory.
     *
     * @param fileToShare   The file to copy over to the app's private storage.
     * @param fileNameToUse The file name to use in the app's private storage sync directory.  If null is provided, then extract the name from the URI.
     * @throws IOException If something goes wrong trying to copy the file.
     */
    private void copySharedFile(Uri fileToShare, String fileNameToUse) throws IOException
    {
        if (fileNameToUse == null) fileNameToUse = getFileName(fileToShare);
        if (fileNameToUse == null) throw new IOException("Could not get a file name for the shared file: " + fileToShare);

        try (final InputStream imageInputStream = getContentResolver().openInputStream(fileToShare);
             final FileOutputStream imageOutputStream = createFileOutputStream(fileNameToUse))
        {
            if (imageInputStream == null) throw new FileNotFoundException("Could not get the shared image input stream");

            SyncMonkeyUtils.copyInputStreamToOutputStream(imageInputStream, imageOutputStream);
        }
    }

    /**
     * Creates a file output stream using the provided file name and a target directory of the Sync Monkey private sync directory.  This enables writing a
     * file to a directory that is automatically synced with the remote server.
     *
     * @param fileName The file name to use when writing the new file to the sync directory.  If a file with the same name already exists, a numerical count
     *                 will be appended to create a unique file name.
     * @return The file output stream where the shared file can be written to.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private FileOutputStream createFileOutputStream(String fileName) throws IOException
    {
        final File privateAppFilesSyncDirectory = new File(getApplicationContext().getFilesDir(), SyncMonkeyConstants.PRIVATE_SHARED_SYNC_DIRECTORY);
        if (!privateAppFilesSyncDirectory.exists()) privateAppFilesSyncDirectory.mkdir();

        File targetFile = new File(privateAppFilesSyncDirectory, createUniqueFileName(fileName));

        targetFile.createNewFile();

        return new FileOutputStream(targetFile);
    }

    /**
     * Given a starting file name, create a file name that is unique in the private shared directory.  If the provided file name is already unique, then use.
     * If it is not unique, then keep counting up and adding that number to the file name until a unique file name is found.
     *
     * @param fileName The file name to start with.
     * @return A unique file name in the app's private storage sync directory.
     */
    private String createUniqueFileName(String fileName)
    {
        final File privateAppFilesSyncDirectory = new File(getApplicationContext().getFilesDir(), SyncMonkeyConstants.PRIVATE_SHARED_SYNC_DIRECTORY);
        File potentialNewFile = new File(privateAppFilesSyncDirectory, fileName);

        if (potentialNewFile.exists())
        {
            final String nameWithoutExtension = SyncMonkeyUtils.getNameWithoutExtension(fileName);
            final String ext = SyncMonkeyUtils.getExtension(fileName);
            final String extension = ext == null ? "" : "." + ext;

            int i = 1;

            while (potentialNewFile.exists())
            {
                fileName = nameWithoutExtension + "(" + i++ + ")" + extension;
                potentialNewFile = new File(privateAppFilesSyncDirectory, fileName);
            }
        }

        return fileName;
    }
}
