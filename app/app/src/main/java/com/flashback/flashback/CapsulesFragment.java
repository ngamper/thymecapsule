package com.flashback.flashback;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.IOContext;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import de.undercouch.bson4jackson.BsonFactory;
import de.undercouch.bson4jackson.BsonParser;

public class CapsulesFragment extends Fragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_capsules, container, false);
        return view;
    }

    private class Capsule {
        private String uploader;
        private double lat, lon;

    }

    private class GetCapsulesTask extends AsyncTask<String, Void, List<Capsule>> {
        @Override
        protected List<Capsule> doInBackground(String... users) {
            try {
                URL url = new URL("http://104.236.119.189/cap");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                OutputStreamWriter outputStream = new OutputStreamWriter(connection.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}
