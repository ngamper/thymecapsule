package com.flashback.flashback;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import de.undercouch.bson4jackson.BsonFactory;

public class CaptureFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_VIDEO_CAPTURE = 2;

    private GoogleApiClient googleApiClient;
    private Location location = null;
    private SharedPreferences sharedPrefs;
    private String user_uuid = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPrefs = getActivity().getSharedPreferences(String.valueOf(R.string.shared_prefs_key), Context.MODE_PRIVATE);
        buildGoogleApiClient();

        user_uuid = sharedPrefs.getString(String.valueOf(R.string.user_uuid), null);

        if (user_uuid == null) {
            if (isConnected()) {
                new CreateUserTask(this).execute();
            } else {
                Toast.makeText(getActivity(), "Not connected to Internet", Toast.LENGTH_SHORT);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_capture, container, false);
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == getActivity().RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                File file = new File(Environment.getExternalStorageDirectory() + File.separator + "temp.jpg");
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                byte[] imageByteArray = baos.toByteArray();

                String base64Image = Base64.encodeToString(imageByteArray, Base64.DEFAULT);

                PictureDetailed imageCapsule = new PictureDetailed(base64Image, location);

                new SendPictureTask(user_uuid).execute(imageCapsule);
            } else if (requestCode == REQUEST_VIDEO_CAPTURE) {
                Uri videoUri = data.getData();
                File videoFile = new File(videoUri.getPath());
                FileInputStream videoInputStream = null;

                try {
                    videoInputStream = new FileInputStream(videoFile);
                    VideoDetailed videoCapsule = new VideoDetailed(videoInputStream, location);

                    new SendVideoTask(user_uuid).execute(videoCapsule);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    protected synchronized void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(getActivity())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    public void takePicture(View v) {
        Toast.makeText(getActivity(), "Getting location...", Toast.LENGTH_SHORT);
        location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);

        if (location != null) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File file = new File(Environment.getExternalStorageDirectory() + File.separator + "temp.jpg");
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(file));

            if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            } else {
                Toast.makeText(getActivity(), "No camera app found on your device!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getActivity(), "Could not find location", Toast.LENGTH_SHORT).show();
        }
    }

    public void recordVideo(View v) {
        Toast.makeText(getActivity(), "Getting location...", Toast.LENGTH_SHORT);
        location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);

        if (location != null) {
            Intent recordVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            recordVideoIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 180);   // Limit to 3 minutes of video

            if (recordVideoIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                startActivityForResult(recordVideoIntent, REQUEST_VIDEO_CAPTURE);
            } else {
                Toast.makeText(getActivity(), "No camera app found on your device!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getActivity(), "Could not find location", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isConnected(){
        ConnectivityManager connMgr = (ConnectivityManager) getActivity().getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }

    @Override
    public void onConnected(Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    }

    private class CreateUserTask extends AsyncTask<Void, Void, String> {
        private CaptureFragment fragment;

        public CreateUserTask(CaptureFragment fragment) {
            this.fragment = fragment;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL("http://104.236.119.189/user");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                String line = "", response = "";
                while ((line = reader.readLine()) != null) {
                    response += line;
                }

                return response;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String uuid) {
            if (uuid != null) {
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(getString(R.string.user_uuid), uuid);
            } else {
                Toast.makeText(getActivity(), "Could not get your user ID", Toast.LENGTH_SHORT);
            }
        }
    }

    private class PictureDetailed {
        private String base64;
        private Location loc;

        public PictureDetailed(String base64, Location loc) {
            this.base64 = base64;
            this.loc = loc;
        }

        public double getLat() {
            return loc.getLatitude();
        }

        public double getLong() {
            return loc.getLongitude();
        }
    }

    private class SendPictureTask extends AsyncTask<PictureDetailed, Void, Void> {
        private String user_uuid;

        public SendPictureTask(String user_uuid) {
            this.user_uuid = user_uuid;
        }

        @Override
        protected Void doInBackground(PictureDetailed... pictureDetaileds) {
            try {
                if (user_uuid != null) {
                    URL url = new URL("http://104.236.119.189/cap/img");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    OutputStreamWriter outputStream = new OutputStreamWriter(connection.getOutputStream());

                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/bson");
                    connection.setRequestMethod("POST");

                    BsonFactory factory = new BsonFactory();
                    JsonGenerator gen = factory.createGenerator(outputStream);

                    gen.writeStartObject();

                    gen.writeFieldName("uploader");
                    gen.writeString(user_uuid);

                    gen.writeFieldName("lat");
                    gen.writeNumber(pictureDetaileds[0].getLat());

                    gen.writeFieldName("long");
                    gen.writeNumber(pictureDetaileds[0].getLong());

                    gen.writeFieldName("payload");
                    gen.writeString(pictureDetaileds[0].base64);

                    gen.writeEndObject();

                    gen.close();
                    outputStream.flush();

                    if (connection.getResponseCode() == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                        Toast.makeText(getActivity(), "Error occurred on server when sending picture", Toast.LENGTH_LONG);
                    } else if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(getActivity(), "Picture successfully sent!", Toast.LENGTH_LONG);
                    } else {
                        Toast.makeText(getActivity(), "Error: HTTP Code " + connection.getResponseCode(), Toast.LENGTH_LONG);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            Toast.makeText(getActivity(), "Error: could not get your user UUID", Toast.LENGTH_LONG);
            return null;
        }
    }

    private class VideoDetailed {
        private FileInputStream videoStream;
        private Location loc;

        public VideoDetailed(FileInputStream videoStream, Location loc) {
            this.videoStream = videoStream;
            this.loc = loc;
        }

        public double getLat() {
            return loc.getLatitude();
        }

        public double getLong() {
            return loc.getLongitude();
        }
    }

    private class SendVideoTask extends AsyncTask<VideoDetailed, Void, Void> {
        private String user_uuid;

        public SendVideoTask(String user_uuid) {
            this.user_uuid = user_uuid;
        }

        @Override
        protected Void doInBackground(VideoDetailed... videoDetaileds) {
            if (user_uuid != null) {
                try {
                    URL url = new URL("http://104.236.119.189/cap/vid");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    OutputStreamWriter outputStream = new OutputStreamWriter(connection.getOutputStream());

                    connection.setDoOutput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/bson");

                    BsonFactory factory = new BsonFactory();
                    JsonGenerator gen = factory.createJsonGenerator(outputStream);

                    gen.writeStartObject();

                    gen.writeFieldName("uploader");
                    gen.writeString(user_uuid);

                    gen.writeFieldName("lat");
                    gen.writeNumber(videoDetaileds[0].getLat());

                    gen.writeFieldName("long");
                    gen.writeNumber(videoDetaileds[0].getLong());

                    gen.writeFieldName("payload");
                    gen.writeBinary(videoDetaileds[0].videoStream, -1);

                    gen.writeEndObject();

                    gen.close();
                    outputStream.flush();

                    if (connection.getResponseCode() == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                        Toast.makeText(getActivity(), "Error occurred on server when sending video", Toast.LENGTH_LONG);
                    } else if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(getActivity(), "Video successfully sent!", Toast.LENGTH_LONG);
                    } else {
                        Toast.makeText(getActivity(), "Error: HTTP Code " + connection.getResponseCode(), Toast.LENGTH_LONG);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Toast.makeText(getActivity(), "Error: could not get your user UUID", Toast.LENGTH_LONG);
            return null;
        }
    }
}
