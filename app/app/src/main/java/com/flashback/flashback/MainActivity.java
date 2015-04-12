package com.flashback.flashback;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.astuetz.PagerSlidingTabStrip;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

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

public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_VIDEO_CAPTURE = 2;

    private Location location = null;
    private SharedPreferences sharedPrefs;
    private String user_uuid = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewPager viewPager = (ViewPager) findViewById(R.id.viewPager);
        viewPager.setAdapter(new MainPagerAdapter(getSupportFragmentManager(), this));

        PagerSlidingTabStrip tabStrip = (PagerSlidingTabStrip) findViewById(R.id.tabStrip);
        tabStrip.setViewPager(viewPager);
        tabStrip.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/fontawesome-webfont.ttf"), Typeface.NORMAL);

        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        tabStrip.setTabPaddingLeftRight((size.x / 6) - 21);

        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                MainActivity.this.location = location;
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

        sharedPrefs = getSharedPreferences(String.valueOf(R.string.shared_prefs_key), Context.MODE_PRIVATE);
        user_uuid = sharedPrefs.getString(String.valueOf(R.string.user_uuid), null);

        if (user_uuid == null) {
            if (isConnected()) {
                new CreateUserTask(this).execute();
            } else {
                Toast.makeText(this, "Not connected to Internet", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                Bitmap bitmap = (Bitmap) data.getExtras().get("data");
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void takePicture(View v) {
        Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show();

        if (location != null) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            } else {
                Toast.makeText(this, "No camera app found on your device!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Could not find location", Toast.LENGTH_SHORT).show();
        }
    }

    public void recordVideo(View v) {
        Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show();

        if (location != null) {
            Intent recordVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            recordVideoIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 180);   // Limit to 3 minutes of video

            if (recordVideoIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(recordVideoIntent, REQUEST_VIDEO_CAPTURE);
            } else {
                Toast.makeText(this, "No camera app found on your device!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Could not find location", Toast.LENGTH_SHORT).show();
        }
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

    public boolean isConnected(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        } else {
            return false;
        }
    }

    private class CreateUserTask extends AsyncTask<Void, Void, String> {
        private MainActivity activity;

        public CreateUserTask(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL("http://bitcmp.ngrok.com/user");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoInput(true);

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
                editor.commit();

                activity.user_uuid = uuid;
            } else {
                Toast.makeText(MainActivity.this, "Could not get your user ID", Toast.LENGTH_SHORT).show();
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

    private class SendPictureTask extends AsyncTask<PictureDetailed, Void, String> {
        private String user_uuid;

        public SendPictureTask(String user_uuid) {
            this.user_uuid = user_uuid;
        }

        @Override
        protected String doInBackground(PictureDetailed... pictureDetaileds) {
            try {
                if (user_uuid != null) {
                    URL url = new URL("http://bitcmp.ngrok.com/cap/img");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/bson");
                    connection.setRequestMethod("POST");

                    OutputStreamWriter outputStream = new OutputStreamWriter(connection.getOutputStream());

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
                        return "Error occurred on server when sending picture";
                    } else if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        return "Picture successfully sent!";
                    } else {
                        return "Error: HTTP Code " + connection.getResponseCode();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return "Error: could not get your user UUID";
        }

        @Override
        public void onPostExecute(String result) {
            if (result.contains("Error")) {
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
            }
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

    private class SendVideoTask extends AsyncTask<VideoDetailed, Void, String> {
        private String user_uuid;

        public SendVideoTask(String user_uuid) {
            this.user_uuid = user_uuid;
        }

        @Override
        protected String doInBackground(VideoDetailed... videoDetaileds) {
            if (user_uuid != null) {
                try {
                    URL url = new URL("http://bitcmp.ngrok.com/cap/vid");
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
                        return "Error occurred on server when sending video";
                    } else if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        return "Video successfully sent!";
                    } else {
                        return "Error: HTTP Code " + connection.getResponseCode();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Toast.makeText(MainActivity.this, "Error: could not get your user UUID", Toast.LENGTH_LONG).show();
            return null;
        }

        @Override
        public void onPostExecute(String result) {
            if (result.contains("Error")) {
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static class MainPagerAdapter extends FragmentPagerAdapter {
        private static int NUM_PAGES = 3;
        private MainActivity activity;

        public MainPagerAdapter(FragmentManager fm, MainActivity activity) {
            super(fm);
            this.activity = activity;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new CaptureFragment();
                case 1:
                    return new CapsulesFragment();
                case 2:
                    return new FriendsFragment();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return NUM_PAGES;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            String title = null;

            Typeface font = Typeface.createFromAsset(activity.getAssets(), "fonts/fontawesome-webfont.ttf");
            SpannableStringBuilder styled;
            switch (position) {
                case 0:
                    title = "\uf030";

                    styled = new SpannableStringBuilder(title);
                    styled.setSpan(new CustomTypefaceSpan(font), 0, title.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

                    return styled;
                case 1:
                    title = "\uf1c5";

                    styled = new SpannableStringBuilder(title);
                    styled.setSpan(new CustomTypefaceSpan(font), 0, title.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

                    return styled;
                case 2:
                    title = "\uf0c0";

                    styled = new SpannableStringBuilder(title);
                    styled.setSpan(new CustomTypefaceSpan(font), 0, title.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

                    return styled;
                default:
                    return null;
            }
        }
    }
}
